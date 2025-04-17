package org.acme.party.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Entity
@Getter
@Setter
@ToString
public class Game extends PanacheEntity {

    public Long getId() {
        return id;
    }

    public String userId;
    public Integer won;
    public Integer lost;
    public Boolean over;
    public Date created;
}
