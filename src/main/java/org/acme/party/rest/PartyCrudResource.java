package org.acme.party.rest;

import io.quarkus.hibernate.reactive.rest.data.panache.PanacheEntityResource;
import io.quarkus.rest.data.panache.ResourceProperties;
import org.acme.party.entity.PartyMember;

@ResourceProperties(path = "/admin/party")
public interface PartyCrudResource extends PanacheEntityResource<PartyMember, Long> {
}