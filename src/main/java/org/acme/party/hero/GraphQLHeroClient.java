package org.acme.party.hero;

import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.graphql.Query;

import java.util.List;

@GraphQLClientApi(configKey = "hero")
public interface GraphQLHeroClient extends HeroClient {

    @Query("heroes")
    Uni<List<Hero>> allHeroes();

}

