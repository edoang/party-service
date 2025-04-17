package org.acme.party.testprofile;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.acme.party.testprofile.profile.StagingProfile;
import org.junit.jupiter.api.Test;
import org.wildfly.common.Assert;

@QuarkusTest()
@TestProfile(StagingProfile.class)
public class StagingProfileTest {

    @Test
    public void test() {
        Log.info("Test with profile 'staging'");
        Log.info("Profile tags: " + System.getProperty("quarkus.test.profile.tags"));
        Log.info("path.to.keycloak property: " + System.getProperty("keycloak.url"));
        Assert.assertTrue(true);
    }


}