package org.acme.party.testprofile.profile;

import io.quarkus.test.junit.QuarkusTestProfile;
import org.acme.party.testprofile.ExtKeycloakTestResource;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExtServiceProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.oidc.enabled", "false"); // turn off the dev ui KC service
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(ExtKeycloakTestResource.class)); // link to custom test resource for external keycloak
    }

    @Override
    public Set<String> tags() {
        return Set.of("ext-keycloak"); // ad hoc profile
    }
}
