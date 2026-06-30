package com.steelmight.charactersheet.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class ActionPoints {

    private int currentAp;
    private int recoveryAp;
    private int maxAp;

    protected ActionPoints() {}

    public ActionPoints(int currentAp, int recoveryAp, int maxAp) {
        this.currentAp = currentAp;
        this.recoveryAp = recoveryAp;
        this.maxAp = maxAp;
    }

    public int getCurrent() { return currentAp; }
    public int getRecovery() { return recoveryAp; }
    public int getMax() { return maxAp; }

    public void setCurrent(int value) { this.currentAp = value; }
    public void setRecovery(int value) { this.recoveryAp = value; }
    public void setMax(int value) { this.maxAp = value; }
}
