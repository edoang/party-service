package org.acme.party.rest;

import io.micrometer.core.annotation.Counted;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.party.entity.Game;

import java.util.List;

import static io.smallrye.mutiny.Uni.createFrom;


@Path("game")
@Produces(MediaType.APPLICATION_JSON)
public class GameResource {

    @Inject
    jakarta.ws.rs.core.SecurityContext context;

    /**
     * Starts a new game, persists the given game entity in the database, and logs the creation of the game.
     *
     * @param game the {@link Game} object to be persisted. This object contains details like user ID, game status,
     *             and other game-related metadata.
     * @return a {@link Uni} object containing the persisted {@link Game} entity upon successful completion.
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @POST
    @WithTransaction // reactive transaction
    @Path("play")
    @Counted(description = "Number of games started") // metrics
    public Uni<Game> play(Game game) {
        game.userId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : "anonymous";

        return game.<Game>persist()// active record pattern & reactive data access
                .onItem() // mutiny reactive pipeline on succesfully game save
                .call(persistedGame -> {
                    Log.info("Successfully created game "
                            + persistedGame);
                    return createFrom().item(persistedGame);
                });
    }

    /**
     * Marks all game entities associated with the given user ID as "over".
     * For every game with a matching user ID, the `over` property is set to `true` and the entity is persisted.
     * Logs game update details during the operation.
     *
     * @param userId the unique identifier of the user whose game entries are to be updated.
     *               It is used to filter game entities from the database.
     * @return a {@link Uni} instance indicating the asynchronous completion of the operation.
     * If no games are found for the given user ID, it completes gracefully without any updates.
     */
    @PUT
    @WithTransaction
    @Path("game-over-user-games")
    public Uni<Void> removeUserParties(final String userId) {
        Log.info("game over for user id: " + userId);

        String currentUserId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : "anonymous";

        if (!userId.equals(currentUserId)) {
            throw new SecurityException("Cannot remove parties for user id " + userId
                    + " because user request " + currentUserId + " <>  owner " + userId);
        }
        Uni<List<Game>> gameUni = Game.listAll();

        return gameUni.flatMap(games -> {

            if (games.isEmpty()) {
                // lista vuota
                return Uni.createFrom().voidItem();
            }

            // filtra per user ID
            List<Uni<PanacheEntityBase>> updateOperations = games.stream()
                    .filter(game -> game.getUserId().equals(userId))
                    .map(game -> {
                        game.setOver(true);
                        return game.persist();
                    })
                    .toList();

            // Controlla se ci sono Uni da combinare
            if (updateOperations.isEmpty()) {
                Log.info("No games found to update for user id: " + userId);
                return Uni.createFrom().voidItem();
            }

            // combina la lista in un solo Uni<Void> e ritorna
            return Uni.combine().all().unis(updateOperations).discardItems();
        });
    }

    /**
     * Updates the "over" status of a specific game identified by its ID.
     * The method validates if the request is made by the user who owns the game and
     * marks the game as "over" if the validation succeeds. The updated game entity is then persisted.
     * If the game does not exist, a 404 (NOT_FOUND) response is returned.
     *
     * @param id the unique identifier of the game to be updated.
     * @return a {@link Uni} that resolves to a {@link Response} indicating the outcome of the operation.
     * If the game is updated successfully, it returns a 200 (OK) response containing the updated game entity.
     * If the game does not exist or the user does not have permission, it returns an appropriate status code.
     */
    @PUT
    @WithTransaction
    @Path("over")
    public Uni<Response> over(final Long id) {

        Log.info("updating over status for game id: " + id);

        String userId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : "anonymous";

        return Game.findById(id)
                .onItem().ifNotNull().transformToUni(existingGame -> {
                    Game p = (Game) existingGame;

                    if (!p.getUserId().equals(userId)) {
                        return createFrom().failure(new IllegalStateException("Cannot close game with id "
                                + id + " because user request " + userId + " <>  owner " + p.getUserId()));
                    }

                    p.setOver(true);

                    return p.persist()
                            .onItem().transform(updated -> Response.ok(p).build());
                })
                .onItem().ifNull().continueWith(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Retrieves a list of game entities based on the current user's context and the provided filter.
     * If no user ID is available in the context, all game entities are returned sorted by user ID.
     * If a user ID is available, the games are filtered and sorted based on the `over` parameter and creation timestamp.
     *
     * @param over an optional parameter to filter games by their `over` status.
     *             If `false`, only games that are not over are retrieved. If `null` or not provided, all games
     *             for the current user are retrieved.
     * @return a {@link Uni} object containing a list of {@link Game} entities that match the specified criteria.
     */
    @GET
    @Path("get")
    public Uni<List<Game>> allGames(@QueryParam("over") Boolean over) {
        String userId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : null;

        if (userId == null) {
            return Game.listAll(Sort.by("userId"));
        } else {
            if (over != null && over == false) {
                return Game.list("userId = ?1 AND over = ?2 ", Sort.by("created"), userId, over);
            } else {
                return Game.list("userId", Sort.by("created"), userId);
            }
        }
    }

}
