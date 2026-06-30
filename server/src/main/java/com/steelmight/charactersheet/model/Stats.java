package com.steelmight.charactersheet.model;

import jakarta.persistence.Embeddable;
import java.util.EnumMap;
import java.util.Map;

@Embeddable
public class Stats {

    private int str;
    private int dex;
    private int con;
    private int intel;
    private int wis;
    private int will;
    private int cha;

    protected Stats() {}

    public Stats(int str, int dex, int con, int intel, int wis, int will, int cha) {
        this.str = str;
        this.dex = dex;
        this.con = con;
        this.intel = intel;
        this.wis = wis;
        this.will = will;
        this.cha = cha;
    }

    public int get(AbilityScore ability) {
        return switch (ability) {
            case STR -> str;
            case DEX -> dex;
            case CON -> con;
            case INT -> intel;
            case WIS -> wis;
            case WILL -> will;
            case CHA -> cha;
        };
    }

    public void set(AbilityScore ability, int value) {
        switch (ability) {
            case STR -> str = value;
            case DEX -> dex = value;
            case CON -> con = value;
            case INT -> intel = value;
            case WIS -> wis = value;
            case WILL -> will = value;
            case CHA -> cha = value;
        }
    }

    public int modifier(AbilityScore ability) {
        return ability.modifier(get(ability));
    }

    public Map<AbilityScore, Integer> toMap() {
        var map = new EnumMap<AbilityScore, Integer>(AbilityScore.class);
        for (var a : AbilityScore.values()) {
            map.put(a, get(a));
        }
        return map;
    }

    public Map<AbilityScore, Integer> modifierMap() {
        var map = new EnumMap<AbilityScore, Integer>(AbilityScore.class);
        for (var a : AbilityScore.values()) {
            map.put(a, modifier(a));
        }
        return map;
    }

    // Getters
    public int getStr() { return str; }
    public int getDex() { return dex; }
    public int getCon() { return con; }
    public int getIntel() { return intel; }
    public int getWis() { return wis; }
    public int getWill() { return will; }
    public int getCha() { return cha; }
}
