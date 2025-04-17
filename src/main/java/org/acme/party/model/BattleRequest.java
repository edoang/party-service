package org.acme.party.model;

import lombok.Data;
import org.acme.party.entity.PartyMember;

import java.util.UUID;

@Data
public class BattleRequest {
    public UUID id;
    public PartyMember partyMember;
    public Long gameId;
    public Boolean isVictory;
}
