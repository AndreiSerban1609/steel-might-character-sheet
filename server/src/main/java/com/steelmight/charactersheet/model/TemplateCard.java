package com.steelmight.charactersheet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class TemplateCard {

    private String name;
    private int modifier;

    @Column(length = 500)
    private String description;

    protected TemplateCard() {}

    public TemplateCard(String name, int modifier, String description) {
        this.name = name;
        this.modifier = modifier;
        this.description = description;
    }

    public String getName() { return name; }
    public int getModifier() { return modifier; }
    public String getDescription() { return description; }
}
