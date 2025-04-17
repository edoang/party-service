package org.acme.party.hero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Hero {

    public Long id;
    public String name;
    public String classe;

}
