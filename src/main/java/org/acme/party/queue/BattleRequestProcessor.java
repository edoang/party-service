package org.acme.party.queue;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.party.entity.Game;
import org.acme.party.entity.PartyMember;
import org.acme.party.model.BattleEnd;
import org.acme.party.model.BattleUpdate;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import java.util.concurrent.CompletionStage;
import java.util.random.RandomGenerator;

import static org.acme.party.model.Quote.*;

@ApplicationScoped
public class BattleRequestProcessor {

    /**
     * Processes a battle request message. It updates the state of a {@link PartyMember} and a {@link Game} entity based
     * on the battle's outcome, marking the party member as no longer fighting and updating the game statistics.
     * The method also logs the battle's processing status and handles errors accordingly.
     *
     * @param message the incoming message containing the battle request information. It includes details such as
     *                the party member involved, the associated game ID, and whether the battle was a victory.
     * @return a {@link Uni} that resolves to a {@link CompletionStage<Void>} after processing the battle request
     * and acknowledging the message. The operation fails if any required entity is not found or if
     * the `partyMember` field in the battle request is null.
     */
//    @Incoming("battles-end")
//    @Outgoing("battles-update")
//    @WithTransaction
//    public Uni<CompletionStage<Void>> processBattleRequest(Message<JsonObject> message) {
//
//
//        BattleEnd battleEnd = message.getPayload().mapTo(BattleEnd.class);
//
//        if (battleEnd.partyMember != null) {
//
//            Long partyMemberId = battleEnd.partyMember.id;
//            Long gameId = battleEnd.gameId != null ? battleEnd.gameId : null;
//
//            return PartyMember.findById(partyMemberId)
//                    .onItem().ifNotNull().invoke(partyMember -> {
//                        PartyMember p = (PartyMember) partyMember;
//                        p.fighting = false;
//                        p.villain = null;
//                        p.health = battleEnd.partyMember.health;
//
//                        partyMember.persist();
//                    })
//                    //
//                    .chain(() -> {
//                        if (gameId != null) {
//                            return Game.findById(gameId)
//                                    .onItem().ifNotNull().invoke(game -> {
//                                        Game g = (Game) game;
//                                        if (battleEnd.isVictory) {
//                                            g.won++;
//                                        } else {
//                                            g.lost++;
//                                        }
//                                        g.persist();
//                                    })
//                                    .onItem().ifNull().failWith(new IllegalStateException("Game with ID " + gameId + " not found"));
//                        } else {
//                            return Uni.createFrom().nullItem();
//                        }
//                    })
//                    .onItem().invoke(() -> {
//                                Log.info("Processing battle end: " + battleEnd.partyMember.heroName + " vs " + battleEnd.partyMember.villain);
//                            }
//                    )
//                    .onFailure().invoke(throwable -> Log.error("Error processing battle request", throwable))
//                    .replaceWith(message.ack())
//                    .onFailure().invoke(throwable -> Log.error("Party Member or Game not found", throwable));
//
//        } else {
//            Log.error("Party is null in battle request: " + battleEnd);
//            return Uni.createFrom().failure(new IllegalArgumentException("Party Member cannot be null"));
//        }
//    }
    @Incoming("battles-end")
    @Outgoing("battles-update")
    @WithTransaction
    public Uni<Message<BattleUpdate>> processBattleRequest(Message<JsonObject> message) {

        // Converte il payload in un oggetto `BattleEnd`
        BattleEnd battleEnd = message.getPayload().mapTo(BattleEnd.class);

        if (battleEnd.partyMember != null) {
            Long partyMemberId = battleEnd.partyMember.id;
            Long gameId = battleEnd.gameId != null ? battleEnd.gameId : null;

            return Uni.createFrom().voidItem()
                    .flatMap(voidItem -> {
                        if (!battleEnd.isVictory) {
                            Log.info("Applying health reduction for all party members...");
                            return PartyMember.update("health = health - 10 WHERE health > 20 and userId = ?1 AND id != ?2",
                                            battleEnd.partyMember.userId, partyMemberId)
                                    .onItem().invoke(updated -> {
                                        if (updated == 0) {
                                            Log.warn("No party members were updated.");
                                        } else {
                                            Log.info("Updated health for " + updated + " party members.");
                                        }
                                    })
                                    .onFailure().invoke(throwable -> {
                                        Log.error("Error updating party members' health.", throwable);
                                    });
                        }
                        return Uni.createFrom().voidItem();
                    })
                    .flatMap(voidItem -> PartyMember.findById(partyMemberId)

                            //return PartyMember.findById(partyMemberId)
                            .onItem().ifNotNull().transformToUni(partyMember -> {
                                PartyMember p = (PartyMember) partyMember;
                                p.fighting = false;
                                p.villain = null;
                                p.health = battleEnd.partyMember.health;

                                updateMemberLevel(p);

                                return p.persist()
                                        .replaceWith(gameId != null
                                                ? Game.findById(gameId) // Trova e aggiorna il Game (se necessario)
                                                .onItem().ifNotNull().invoke(game -> {
                                                    Game g = (Game) game;
                                                    if (battleEnd.isVictory) {
                                                        g.won++;
                                                    } else {
                                                        g.lost++;
                                                    }
                                                    g.persist();
                                                })
                                                .onItem().ifNull().failWith(() -> new IllegalStateException("Game with ID " + gameId + " not found"))
                                                : Uni.createFrom().nullItem());
                            }))
                    .onItem().transform(ignored -> {
                        BattleUpdate update = new BattleUpdate();
                        update.setUser(battleEnd.partyMember.userId);
                        update.setMessage((battleEnd.isVictory ? "[WON] " : "[LOST]") + " " + battleEnd.partyMember.heroName + ": " + getQuote(battleEnd.partyMember.heroName));
                        Log.info("Processing battle end: " + battleEnd.partyMember.heroName + " vs " + battleEnd.partyMember.villain);
                        return Message.of(update, () -> message.ack());
                    })
                    .onFailure().invoke(throwable -> {
                        Log.error("Error processing battle request", throwable);
                    });
        } else {
            Log.error("Party Member is null in battle request: " + battleEnd);
            return Uni.createFrom().failure(new IllegalArgumentException("Party Member cannot be null"));
        }
    }

    private void updateMemberLevel(PartyMember p) {
        switch (p.level) {
            case 1:
                if (p.health >= 100) {
                    p.level = 2;
                }
                break;
            case 2:
                if (p.health >= 150) {
                    p.level = 3;
                }
            case 3:
                if (p.health >= 200) {
                    p.level = 4;
                }
            case 4:
                if (p.health >= 250) {
                    p.level = 5;
                }
            case 5:
                if (p.health >= 300) {
                    p.level = 6;
                }
            case 6:
                if (p.health >= 350) {
                    p.level = 7;
                }
            case 7:
                if (p.health >= 400) {
                    p.level = 8;
                }
            case 8:
                if (p.health >= 450) {
                    p.level = 9;
                }
            case 9:
                if (p.health >= 500) {
                    p.level = 10;
                }

                break;
            default:
                break;
        }
    }

    private String getQuote(String partyMember) {
        String[] quotes = {};
        switch (partyMember) {
            case "Astarion":
                quotes = ASTARION_QUOTES;
                break;
            case "Gale":
                quotes = GALE_QUOTES;
                break;
            case "Shadowheart":
                quotes = SHADOWHEART_QUOTES;
                break;
            case "Wyll":
                quotes = WYLL_QUOTES;
                break;
            case "The_Dark_Urge":
                quotes = THE_DARK_URGE_QUOTES;
                break;
            case "Karlach":
                quotes = KARLACH_QUOTES;
                break;

            default:
                break;
        }
        if (quotes.length == 0) {
            return "...";
        }
        int index = RandomGenerator.getDefault().nextInt(quotes.length);
        return quotes[index];
    }
}