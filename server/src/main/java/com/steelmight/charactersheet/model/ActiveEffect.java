package com.steelmight.charactersheet.model;

import jakarta.persistence.*;

@Entity
@Table(name = "active_effects")
public class ActiveEffect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One row type, one table, two possible parents (ADR-001 §2): exactly one of these is set.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private GameCharacter character;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monster_id")
    private MonsterInstance monster;

    private String effectId;
    private String source;
    private int stacks;
    @Column(name = "effect_value") // "value" is a reserved word in H2 (and SQL standard)
    private Integer value;
    private Integer remainingRounds;
    private int appliedAt;

    // M3 Part B — nullable so pre-existing rows migrate via the derived default in the getter.
    @Enumerated(EnumType.STRING)
    private DurationType durationType;

    protected ActiveEffect() {}

    public ActiveEffect(String effectId, String source, int stacks, Integer value,
                        Integer remainingRounds, int appliedAt) {
        this.effectId = effectId;
        this.source = source;
        this.stacks = stacks;
        this.value = value;
        this.remainingRounds = remainingRounds;
        this.appliedAt = appliedAt;
    }

    public Long getId() { return id; }

    public GameCharacter getCharacter() { return character; }
    public void setCharacter(GameCharacter character) { this.character = character; }
    public MonsterInstance getMonster() { return monster; }
    public void setMonster(MonsterInstance monster) { this.monster = monster; }

    public String getEffectId() { return effectId; }
    public void setEffectId(String effectId) { this.effectId = effectId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public int getStacks() { return stacks; }
    public void setStacks(int stacks) { this.stacks = stacks; }

    public Integer getValue() { return value; }
    public void setValue(Integer value) { this.value = value; }

    public Integer getRemainingRounds() { return remainingRounds; }
    public void setRemainingRounds(Integer remainingRounds) { this.remainingRounds = remainingRounds; }

    public int getAppliedAt() { return appliedAt; }
    public void setAppliedAt(int appliedAt) { this.appliedAt = appliedAt; }

    /** Migration default (M3 criterion 9): rows without an explicit type derive
     *  ROUNDS when they carry a duration, UNTIL_DISPELLED otherwise. */
    public DurationType getDurationType() {
        if (durationType != null) return durationType;
        return remainingRounds != null ? DurationType.ROUNDS : DurationType.UNTIL_DISPELLED;
    }

    public void setDurationType(DurationType durationType) { this.durationType = durationType; }
}
