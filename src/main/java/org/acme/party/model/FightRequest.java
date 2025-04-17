package org.acme.party.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FightRequest {
    public Long partyMemberId;
    public Long gameId;

}
