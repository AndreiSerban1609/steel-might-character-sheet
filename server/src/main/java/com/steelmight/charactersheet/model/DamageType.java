package com.steelmight.charactersheet.model;

public enum DamageType {
    // Physical
    SLASHING, PIERCING, CRUSHING,
    // Magical
    SHADOW, LIGHT, FIRE, ICE, LIGHTNING,
    POISON, THUNDER, PSYCHIC, SPECTRAL, PURE, FORCE,
    // True
    TRUE;

    public boolean isPhysical() {
        return this == SLASHING || this == PIERCING || this == CRUSHING;
    }

    public boolean isMagical() {
        return !isPhysical() && this != TRUE;
    }
}
