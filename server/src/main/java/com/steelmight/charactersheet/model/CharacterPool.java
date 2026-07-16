package com.steelmight.charactersheet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One materialized sub-resource pool (perseverance, fury, fatal-fixation…).
 * Definitions live in abilities-*.json (gamedata.PoolDefinition); the character row
 * only tracks current/max. max is a wrapper: null = unbounded. current may go
 * NEGATIVE for pools whose definition declares a min (fury disaster rule — the
 * consequence is adjudicated at the table).
 */
@Embeddable
public class CharacterPool {

    @Column(name = "pool_id")
    private String poolId;

    @Column(name = "pool_current")
    private int current;

    @Column(name = "pool_max")
    private Integer max;

    protected CharacterPool() {}

    public CharacterPool(String poolId, int current, Integer max) {
        this.poolId = poolId;
        this.current = current;
        this.max = max;
    }

    public String getPoolId() { return poolId; }

    public int getCurrent() { return current; }
    public void setCurrent(int current) { this.current = current; }

    public Integer getMax() { return max; }
    public void setMax(Integer max) { this.max = max; }
}
