package org.acme.party.testprofile.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;
import java.util.Set;

public class LocalProfile implements QuarkusTestProfile {


    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("keycloak.url",
                "http://local.keycloak");
    }

    @Override
    public Set<String> tags() {
        return Set.of("local");
    }
}