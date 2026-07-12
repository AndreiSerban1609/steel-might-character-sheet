package com.steelmight.charactersheet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class TemplateCard {

    private String name;
    private int modifier;

    @Column(length = 500)
    private String description;

    /** Skill id this card is restricted to (CLASS cards): drawn on another check, it auto-skips. */
    private String checkType;

    /** When set (CLASS cards): drawn card is passed, this bonus accumulates, and the draw continues. */
    private Integer redrawModifier;

    /** null | "consume" (out until rest) | "burn" (gone permanently) — applied when accepted as the final card. */
    private String removal;

    /** Wrapper, not primitive: legacy rows predate the column (house pattern). */
    private Boolean consumed;

    protected TemplateCard() {}

    public TemplateCard(String name, int modifier, String description) {
        this(name, modifier, description, null, null, null, null);
    }

    public TemplateCard(String name, int modifier, String description,
                        String checkType, Integer redrawModifier, String removal, Boolean consumed) {
        this.name = name;
        this.modifier = modifier;
        this.description = description;
        this.checkType = checkType;
        this.redrawModifier = redrawModifier;
        this.removal = removal;
        this.consumed = consumed;
    }

    public String getName() { return name; }
    public int getModifier() { return modifier; }
    public String getDescription() { return description; }
    public String getCheckType() { return checkType; }
    public Integer getRedrawModifier() { return redrawModifier; }
    public String getRemoval() { return removal; }

    public boolean isConsumed() { return Boolean.TRUE.equals(consumed); }
    public void setConsumed(Boolean consumed) { this.consumed = consumed; }
}
