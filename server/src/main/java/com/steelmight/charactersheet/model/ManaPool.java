package com.steelmight.charactersheet.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class ManaPool {

    private int currentMana;
    private int maxMana;

    protected ManaPool() {}

    public ManaPool(int currentMana, int maxMana) {
        this.currentMana = currentMana;
        this.maxMana = maxMana;
    }

    public int getCurrent() { return currentMana; }
    public int getMax() { return maxMana; }

    public void setCurrent(int value) { this.currentMana = value; }
    public void setMax(int value) { this.maxMana = value; }
}
