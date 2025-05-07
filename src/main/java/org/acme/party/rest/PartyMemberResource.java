package org.acme.party.rest;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.smallrye.graphql.client.GraphQLClient;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.party.entity.Game;
import org.acme.party.entity.PartyMember;
import org.acme.party.hero.GraphQLHeroClient;
import org.acme.party.hero.Hero;
import org.acme.party.hero.HeroClient;
import org.acme.party.model.BattleRequest;
import org.acme.party.model.FightRequest;
import org.acme.party.model.HealRequest;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

import static io.smallrye.mutiny.Uni.createFrom;
import static org.acme.party.model.Armory.*;


@Path("party")
@Produces(MediaType.APPLICATION_JSON)
public class PartyMemberResource {

    Map<String, List<String>> lastArmourMap = new ConcurrentHashMap<>();
    Map<String, List<String>> lastWeaponMap = new ConcurrentHashMap<>();

    @Inject
    @Channel("battles-request") // imperative messaging
    Emitter<BattleRequest> battleRequestEmitter;

    private final HeroClient heroClient;

    @Inject
    jakarta.ws.rs.core.SecurityContext context;

    public PartyMemberResource(@GraphQLClient("inventory") GraphQLHeroClient heroClient) {
        this.heroClient = heroClient;
    }

    /**
     * d persists a new {@link PartyMember} entity with randomized weapon and armor,
     * assigning the user ID of the current user or "anonymous" if there is no user context.
     * Logs the creation of the party member upon successful persistence.
     *
     * @param partyMember the {@link PartyMember} entity to be created and persisted. It is augmented
     *                    with a user ID, randomly chosen weapon, and armor before persisting.
     * @return a {@link Uni} object containing the persisted {@link PartyMember} entity upon
     * successful completion.
     * Creates an
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @POST
    @WithTransaction
    @Path("make")
    public Uni<PartyMember> make(PartyMember partyMember) {
        partyMember.userId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : "anonymous";

        partyMember.setArmour(getArmour(partyMember.userId));
        partyMember.setWeapon(getWeapon(partyMember.userId));
        partyMember.setLevel(1);

        return partyMember.<PartyMember>persist().onItem()
                .call(persistedParty -> {
                    Log.info("Successfully created party member "
                            + persistedParty);
                    return createFrom().item(persistedParty);
                });
    }

    /**
     * Removes all parties associated with the specified user ID by deleting corresponding PartyMember entries.
     * If no parties are associated with the given user ID, the method completes without performing any deletions.
     *
     * @param userId the unique identifier of the user whose party memberships are to be removed.
     *               Only parties mapped to this user ID will be affected.
     * @return a {@link Uni} object representing the asynchronous completion of the operation.
     * The Uni resolves to void upon successful deletion or if no parties are found for the specified user ID.
     */
    @DELETE
    @WithTransaction
    @Path("remove-user-parties")
    public Uni<Void> removeUserParties(final String userId) {

        lastArmourMap.put(userId, new ArrayList<>());
        lastWeaponMap.put(userId, new ArrayList<>());

        Log.info("release parties for user id: " + userId);

        String currentUserId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : "anonymous";

        if (!userId.equals(currentUserId)) {
            throw new SecurityException("Cannot remove parties for user id " + userId
                    + " because user request " + currentUserId + " <>  owner " + userId);
        }

        Uni<List<PartyMember>> partysUni = PartyMember.listAll();

        return partysUni.flatMap(parties ->
                parties.isEmpty()
                        ? Uni.createFrom().voidItem()
                        : Uni.combine().all().unis(
                        parties.stream()
                                .filter(party -> party.getUserId().equals(userId))
                                .map(PartyMember::delete)
                                .toList()
                ).discardItems()
        );
    }

    @PUT
    @WithTransaction
    @Path("heal")
    public Uni<Response> heal(final HealRequest healRequest) {
        if (healRequest == null || healRequest.getGameId() == null) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.BAD_REQUEST).entity("Invalid heal request").build()
            );
        }

        return Game.<Game>findById(healRequest.getGameId())
                .onItem().ifNotNull().transformToUni(game -> {
                    if (healRequest.getHealAll()) {
                        // Heal all PartyMembers in the game
                        return PartyMember
                                .update("health = health + 20 WHERE userId = ?1 AND health < 50", game.getUserId())
                                .onItem().transform(updated -> Response.ok("All members of the party healed").build());
                    } else {
                        if (healRequest.getPartyMemberId() == null) {
                            return Uni.createFrom().item(
                                    Response.status(Response.Status.BAD_REQUEST).entity("PartyMemberId is required when healAll is false").build()
                            );
                        }
                        // Heal the specific PartyMember
                        return PartyMember
                                .update("health = health + 10 WHERE id = ?1 ", healRequest.getPartyMemberId())
                                .onItem().transform(updated -> {
                                    if (updated > 0) {
                                        return Response.ok("Party member healed").build();
                                    } else {
                                        return Response.status(Response.Status.NOT_FOUND).entity("Party member not found or does not belong to the game").build();
                                    }
                                });
                    }
                })
                .onItem().ifNull().continueWith(() -> Response.status(Response.Status.NOT_FOUND).entity("Game not found").build());
    }


    /**
     * Toggles the fighting status of a party member based on the provided FightRequest.
     * Also handles associated game logic such as initiating or concluding a battle.
     *
     * @param fightRequest the request containing the party member ID and the game ID
     * @return a Uni<Response> indicating the result of the operation, which could be:
     * - a Response with the updated PartyMember details on success
     * - a failure Response if the operations cannot be completed due to invalid state or missing resources
     */
    @PUT
    @WithTransaction
    @Path("fight")
    public Uni<Response> fight(final FightRequest fightRequest) {

        Log.info("updating fighting status for party id: " + fightRequest.partyMemberId + " game id: " + fightRequest.gameId);

        String userId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : "anonymous";

        return PartyMember.findById(fightRequest.partyMemberId)
                .onItem().ifNotNull().transformToUni(existingParty -> {
                    PartyMember p = (PartyMember) existingParty;

                    if (!p.getUserId().equals(userId)) {
                        return createFrom().failure(new IllegalStateException("Cannot fight with party with id "
                                + fightRequest.partyMemberId + " because user request " + userId + " <>  owner " + p.getUserId()));
                    }

                    if (p.health <= 0) {
                        return createFrom().failure(new IllegalStateException("Cannot fight with a dead hero" + p));
                    }

                    return Game.findById(fightRequest.gameId)
                            .onItem().ifNull().failWith(new IllegalStateException("Game not found with id: " + fightRequest.gameId))
                            .onItem().transformToUni(game -> {
                                p.setFighting(!p.getFighting());
                                p.villain = getVillain();

                                if (p.getFighting()) {
                                    BattleRequest b = new BattleRequest();
                                    b.setId(UUID.randomUUID());
                                    b.setPartyMember(p);
                                    b.setGameId(((Game) game).getId());
                                    battleRequestEmitter.send(b);
                                    Log.info("battle request for party sent " + p + " game id" + b.getGameId());
                                } else {
                                    Log.info("battle done for party " + p);
                                }

                                return p.persist()
                                        .onItem().transform(updated -> Response.ok(p).build());
                            });
                })
                .onItem().ifNull().continueWith(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Retrieves a collection of heroes that are not currently part of the user's party.
     * The method fetches all available heroes and the user's party members,
     * then filters out heroes that are already included in the user's party.
     *
     * @return a {@link Uni} containing a collection of {@link Hero} objects that are not currently part of the user's party.
     */
    @GET
    @Path("availability")
    public Uni<Collection<Hero>> availability() {

        // restituisce gli eroi non presenti nel party

        Uni<List<Hero>> availableHeroesUni = heroClient.allHeroes();

        Uni<List<PartyMember>> partysUni = PartyMember.listAll();

        return Uni.combine().all().unis(availableHeroesUni, partysUni).with((availableHeroes, partys) -> {

            Map<Long, Hero> heroesById = new HashMap<>();
            for (Hero hero : availableHeroes) {
                heroesById.put(hero.id, hero);
            }

            for (PartyMember partyMember : partys) {
                if (partyMember.heroId != null &&
                        partyMember.userId.equals(context.getUserPrincipal().getName())) {
                    heroesById.remove(partyMember.heroId);
                }
            }
            return heroesById.values();
        });
    }

    /**
     * Retrieves a list of {@link PartyMember} entities based on the current user's context.
     * If no user ID is available, all party members are retrieved sorted by their hero ID.
     * If a user ID is available, only party members associated with that user ID are retrieved
     * and sorted by their hero ID.
     *
     * @return a {@link Uni} containing a list of {@link PartyMember} entities fetched from the database.
     * The list is sorted by hero ID and filtered by the user's context if applicable.
     */
    @GET
    @Path("all")
    public Uni<List<PartyMember>> allPartys() {
        String userId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : null;

        if (userId == null) {
            return PartyMember.listAll(Sort.by("heroId"));
        } else {
            return PartyMember.list("userId", Sort.by("heroId"), userId);
        }
    }

    /**
     * Retrieves a random villain name from the list of available villains.
     *
     * @return a randomly selected villain name as a string.
     */
    private String getVillain() {
        int index = RandomGenerator.getDefault().nextInt(VILLAINS.length);
        return VILLAINS[index];
    }

    /**
     * Selects and returns a random weapon from the available weapons list.
     *
     * @return a randomly selected weapon as a {@code String}. The weapon is chosen
     * from the current list of available weapons.
     */
    private String getWeapon(String userId) {
        List<String> lastWeapons = lastWeaponMap.get(userId);
        int index = RandomGenerator.getDefault().nextInt(WEAPONS.length);
        String weapon = null;
        if (lastWeapons != null) {
            int i = 0;
            while (i < WEAPONS.length) {
                if (!lastWeapons.contains(WEAPONS[index])) {
                    weapon = WEAPONS[index];
                    lastWeapons.add(weapon);
                    break;
                }
                index = (index + 1) % WEAPONS.length;
                i++;
            }
        } else {
            weapon = WEAPONS[index];
            lastWeaponMap.put(userId, new ArrayList<>(List.of(weapon)));
        }
        return weapon;
    }

    /**
     * Randomly selects an armour from the available armours array and returns it.
     *
     * @return a randomly chosen armour as a {@code String} from the predefined list of armours.
     */
    private String getArmour(String userId) {
        List<String> lastArmours = lastArmourMap.get(userId);
        int index = RandomGenerator.getDefault().nextInt(ARMOURS.length);
        String armour = null;
        if (lastArmours != null) {
            int i = 0;
            while (i < ARMOURS.length) {
                if (!lastArmours.contains(ARMOURS[index])) {
                    armour = ARMOURS[index];
                    lastArmours.add(armour);
                    break;
                }
                index = (index + 1) % ARMOURS.length;
                i++;
            }
        } else {
            armour = ARMOURS[index];
            lastArmourMap.put(userId, new ArrayList<>(List.of(armour)));
        }
        return armour;
    }
}
