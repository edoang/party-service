package org.acme.party.hero;

import io.smallrye.mutiny.Uni;

import java.util.List;

public interface HeroClient {

    Uni<List<Hero>> allHeroes();
}
