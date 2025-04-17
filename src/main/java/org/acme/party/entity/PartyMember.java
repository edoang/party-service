package org.acme.party.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class PartyMember extends PanacheEntity {

    public String userId;
    public Long heroId;
    public String heroName;
    public String villain;
    public Boolean fighting;
    public Long health;
    public String weapon;
    public String armour;
    public Integer level;

}
