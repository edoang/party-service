package org.acme.party.testprofile;

import io.quarkus.logging.Log;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

public class ExtKeycloakTestResource implements QuarkusTestResourceLifecycleManager {


    private String KEYCLOAK_PORT = "7777";// ----  PORTA DEL KC esterno ----

    @Override
    public Map<String, String> start() {
        Log.info("EXT/Mock Keycloak Server...");
        // <we could start a service here>
        // instead we use an existing one if any
        return Map.of("keycloak.url", "http://localhost:" + KEYCLOAK_PORT); // using an external keycloak...
    }

    @Override
    public void stop() {
        Log.info("Stopping Mock Keycloak Server...");
        // <we could stop a service here>
    }
}
