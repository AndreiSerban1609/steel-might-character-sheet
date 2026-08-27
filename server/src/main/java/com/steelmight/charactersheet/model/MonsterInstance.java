package com.steelmight.charactersheet.model;

import com.steelmight.charactersheet.gamedata.GameDataProvider;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A monster stamped into a room's fight (ADR-001 §2). Owns its own copy of the combat
 * block, HP, life status and effect stack, and flows through the SAME damage / healing /
 * effect / turn-tick pipelines as a player because it is a {@link Combatant}.
 *
 * What a monster does NOT have (ruling E1): AP, mana, decks, inventory, abilities beyond
 * free text — {@link #getAp()} is null and callers guard on it. Nor a downed window: at
 * 0 HP it is dead (ruling 2026-08-26, no exceptions for bosses).
 */
@Entity
@Table(name = "monster_instances")
public class MonsterInstance implements Combatant {

    public static final String ID_PREFIX = "monster:";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roomName;
    private Long templateId;
    private String displayName;

    @Embedded
    private MonsterBlock block;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "currentHp", column = @Column(name = "hp_current")),
            @AttributeOverride(name = "maxHp", column = @Column(name = "hp_max")),
            @AttributeOverride(name = "tempHp", column = @Column(name = "hp_temp"))
    })
    private HitPoints hp;

    @Enumerated(EnumType.STRING)
    private LifeStatus lifeStatus;

    // Death-fight bookkeeping — always neutral for monsters (they never enter the downed state);
    // kept so DeathCheckRule stays one implementation.
    private Integer downedRoundsRemaining;
    private Boolean pendingDeathFight;
    private Integer downsThisCombat;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "monster_instance_saving_throw_profs", joinColumns = @JoinColumn(name = "monster_id"))
    @Column(name = "ability")
    @Enumerated(EnumType.STRING)
    private List<AbilityScore> savingThrowProficiencies = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "monster_instance_damage_taken", joinColumns = @JoinColumn(name = "monster_id"))
    @MapKeyColumn(name = "damage_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "multiplier")
    private Map<DamageType, Double> damageTaken = new HashMap<>();

    @OneToMany(mappedBy = "monster", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("appliedAt ASC")
    private List<ActiveEffect> activeEffects = new ArrayList<>();

    protected MonsterInstance() {}

    /** Stamp a template: copies the block so later template edits never touch this fight. */
    public MonsterInstance(MonsterTemplate template, String displayName) {
        this.roomName = template.getRoomName();
        this.templateId = template.getId();
        this.displayName = displayName;
        this.block = new MonsterBlock(template.getBlock());
        this.hp = new HitPoints(block.getMaxHp(), block.getMaxHp(), 0);
        this.lifeStatus = LifeStatus.ALIVE;
        this.savingThrowProficiencies.addAll(template.getSavingThrowProficiencies());
        this.damageTaken.putAll(template.getDamageTaken());
    }

    public static boolean isMonsterId(String combatantId) {
        return combatantId != null && combatantId.startsWith(ID_PREFIX);
    }

    /** The numeric instance id inside a {@code monster:{id}} combatant id, or null if malformed. */
    public static Long parseId(String combatantId) {
        if (!isMonsterId(combatantId)) return null;
        try {
            return Long.parseLong(combatantId.substring(ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- Combatant ----

    @Override
    public String getCombatantId() { return ID_PREFIX + id; }

    @Override
    public String getName() { return displayName; }

    @Override
    public int getLevel() { return block.getLevel(); }

    @Override
    public int getInitiativeBonus() { return block.getInitiativeBonus(); }

    @Override
    public Stats getStats() { return block.getStats(); }

    @Override
    public HitPoints getHp() { return hp; }

    /** No AP economy (ruling E1). */
    @Override
    public ActionPoints getAp() { return null; }

    @Override
    public int getSpeed() { return block.getSpeed(); }

    @Override
    public LifeStatus getLifeStatus() { return lifeStatus != null ? lifeStatus : LifeStatus.ALIVE; }

    @Override
    public void setLifeStatus(LifeStatus status) { this.lifeStatus = status; }

    @Override
    public List<ActiveEffect> getActiveEffects() { return activeEffects; }

    @Override
    public void addEffect(ActiveEffect effect) {
        activeEffects.add(effect);
        effect.setMonster(this);
    }

    @Override
    public void removeEffect(ActiveEffect effect) {
        activeEffects.remove(effect);
        effect.setMonster(null);
    }

    @Override
    public List<AbilityScore> getSavingThrowProficiencies() { return savingThrowProficiencies; }

    /** The authored block IS the override for every combat stat; the rest has no monster meaning. */
    @Override
    public Integer overrideFor(OverridableStat stat) {
        return switch (stat) {
            case MAX_HP -> block.getMaxHp();
            case AC -> block.getAc();
            case PA -> block.getPa();
            case MA -> block.getMa();
            case SPEED -> block.getSpeed();
            default -> null;
        };
    }

    @Override
    public Integer authoredStackThreshold() { return block.getStackThreshold(); }

    @Override
    public Map<DamageType, Double> innateDamageTaken(GameDataProvider gameData) { return damageTaken; }

    @Override
    public String innateDamageTakenSource() { return "monster: " + displayName; }

    /** Ruling 2026-08-26: a downed monster is a dead monster — no window, no revival, ever. */
    @Override
    public boolean usesDeathRules() { return false; }

    @Override
    public Integer getDownedRoundsRemaining() { return downedRoundsRemaining; }

    @Override
    public void setDownedRoundsRemaining(Integer rounds) { this.downedRoundsRemaining = rounds; }

    @Override
    public int getDownsThisCombat() { return downsThisCombat != null ? downsThisCombat : 0; }

    @Override
    public void setDownsThisCombat(int downs) { this.downsThisCombat = downs; }

    @Override
    public boolean isPendingDeathFight() { return Boolean.TRUE.equals(pendingDeathFight); }

    @Override
    public void setPendingDeathFight(boolean pending) { this.pendingDeathFight = pending; }

    // ---- Plain accessors ----

    public Long getId() { return id; }
    public String getRoomName() { return roomName; }
    public Long getTemplateId() { return templateId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public MonsterBlock getBlock() { return block; }
    public Map<DamageType, Double> getDamageTaken() { return damageTaken; }
}
