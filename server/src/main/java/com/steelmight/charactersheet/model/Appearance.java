package com.steelmight.charactersheet.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class Appearance {

    private Integer age;
    private String eyeColor;
    private Integer heightCm;
    private String skin;
    private Integer weightKg;
    private String hair;

    protected Appearance() {}

    public Appearance(Integer age, String eyeColor, Integer heightCm, String skin,
                      Integer weightKg, String hair) {
        this.age = age;
        this.eyeColor = eyeColor;
        this.heightCm = heightCm;
        this.skin = skin;
        this.weightKg = weightKg;
        this.hair = hair;
    }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getEyeColor() { return eyeColor; }
    public void setEyeColor(String eyeColor) { this.eyeColor = eyeColor; }

    public Integer getHeightCm() { return heightCm; }
    public void setHeightCm(Integer heightCm) { this.heightCm = heightCm; }

    public String getSkin() { return skin; }
    public void setSkin(String skin) { this.skin = skin; }

    public Integer getWeightKg() { return weightKg; }
    public void setWeightKg(Integer weightKg) { this.weightKg = weightKg; }

    public String getHair() { return hair; }
    public void setHair(String hair) { this.hair = hair; }
}
