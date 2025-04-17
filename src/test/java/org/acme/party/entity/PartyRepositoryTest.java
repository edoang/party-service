package org.acme.party.entity;

import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import org.acme.party.testprofile.profile.LocalProfile;
import org.junit.jupiter.api.Test;

@TestProfile(LocalProfile.class)
@QuarkusTest
public class PartyRepositoryTest {


    @Test
    @RunOnVertxContext
    public void testEntity(TransactionalUniAsserter asserter) {
        asserter.execute(() -> new PartyMember().persist());
        asserter.assertEquals(() -> PartyMember.count(), 1l);
        asserter.execute(() -> PartyMember.deleteAll());
    }
}