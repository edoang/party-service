package org.acme.party.rest;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.DisabledOnIntegrationTest;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.SecurityContext;
import org.acme.party.entity.PartyMember;
import org.acme.party.hero.GraphQLHeroClient;
import org.acme.party.hero.Hero;
import org.acme.party.testprofile.profile.LocalProfile;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URL;
import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;

@TestProfile(LocalProfile.class)
@QuarkusTest
public class PartyMemberResourceTest {

    @TestHTTPEndpoint(PartyMemberResource.class)
    @TestHTTPResource("make")
    URL partyMemberResource;

    @TestHTTPEndpoint(PartyMemberResource.class)
    @TestHTTPResource("availability")
    URL availability;

    @Test
    public void testPartyMemberIds() {
        PartyMember partyMember = new PartyMember();
        partyMember.heroId = 1L;

        RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(partyMember)
                .when()
                .post(partyMemberResource)
                .then()
                .statusCode(200)
                .body("id", notNullValue());
    }

    // uses mocks
    @DisabledOnIntegrationTest(forArtifactTypes =
            DisabledOnIntegrationTest.ArtifactType.NATIVE_BINARY)
    @Test
    public void testMakingAPartyMemberAndCheckAvailability() {
        GraphQLHeroClient mock =
                Mockito.mock(GraphQLHeroClient.class);

        SecurityContext context = Mockito.mock(SecurityContext.class);

        Principal principal = Mockito.mock(Principal.class);

        Hero pippo = new Hero(1L, "Pippo", "Fighter");
        List<Hero> list = Collections.singletonList(pippo);

        Mockito.when(mock.allHeroes())
                .thenReturn(Uni.createFrom().item(list));

        Mockito.when(principal.getName())
                .thenReturn("user");

        Mockito.when(context.getUserPrincipal())
                .thenReturn(principal);


        QuarkusMock.installMockForType(mock,
                GraphQLHeroClient.class);

        QuarkusMock.installMockForType(principal,
                Principal.class);

        QuarkusMock.installMockForType(context,
                SecurityContext.class);

        Hero[] heroes = RestAssured
                .given()
                .when().get(availability)
                .then().statusCode(200)
                .extract().as(Hero[].class);


        Hero hero = heroes[0];

        PartyMember partyMember = new PartyMember();
        partyMember.heroId = hero.id;

        RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(partyMember)
                .when().post(partyMemberResource)
                .then().statusCode(200)
                .body("heroId", is(hero.id.intValue()));

        RestAssured
                .given()
                .when().get(availability)
                .then().statusCode(200)
                .body("findAll { hero -> hero.id == " + hero.id + "}", hasSize(0));

    }
}
