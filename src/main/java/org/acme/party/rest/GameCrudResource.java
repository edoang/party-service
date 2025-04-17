package org.acme.party.rest;

import io.quarkus.hibernate.reactive.rest.data.panache.PanacheEntityResource;
import io.quarkus.rest.data.panache.ResourceProperties;
import org.acme.party.entity.Game;

@ResourceProperties(path = "/admin/game")
public interface GameCrudResource extends PanacheEntityResource<Game, Long> {
}