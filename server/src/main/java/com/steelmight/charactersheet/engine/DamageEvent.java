package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.model.DamageType;

public class DamageEvent {

    private int value;
    private final DamageType damageType;

    public DamageEvent(int value, DamageType damageType) {
        this.value = value;
        this.damageType = damageType;
    }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public DamageType getDamageType() { return damageType; }
}
