package org.acme.party.testprofile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.acme.party.testprofile.profile.ExtServiceProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest()
@TestProfile(ExtServiceProfile.class)
public class ExtKeycloakTest {

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl; // ext kc url

    @Test
    public void testAuthentication() {
        RestAssured
                .given()
                .contentType(ContentType.JSON)
                .when()
                .get(keycloakUrl)
                .then()
                .statusCode(200);
    }

}