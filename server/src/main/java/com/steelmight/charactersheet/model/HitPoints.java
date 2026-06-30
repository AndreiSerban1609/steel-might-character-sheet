package com.steelmight.charactersheet.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class HitPoints {

    private int currentHp;
    private int maxHp;
    private int tempHp;

    protected HitPoints() {}

    public HitPoints(int currentHp, int maxHp, int tempHp) {
        this.currentHp = currentHp;
        this.maxHp = maxHp;
        this.tempHp = tempHp;
    }

    public int getCurrent() { return currentHp; }
    public int getMax() { return maxHp; }
    public int getTemp() { return tempHp; }

    public void setCurrent(int value) { this.currentHp = value; }
    public void setMax(int value) { this.maxHp = value; }
    public void setTemp(int value) { this.tempHp = value; }
}
