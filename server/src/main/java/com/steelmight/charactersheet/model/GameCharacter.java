package com.steelmight.charactersheet.model;

import com.steelmight.charactersheet.gamedata.GameDataProvider;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "characters")
public class GameCharacter implements Combatant {

    @Id
    private String playerId;

    private String roomName;
    private String email;

    private String name;
    private String portraitUrl;
    private String background;
    private String alignment;

    private String raceId;
    private String pathId;
    private String classId;
    private String specializationId;
    private int level;

    @Embedded
    private Stats stats;

    @Embedded
    private HitPoints hp;

    @Embedded
    private ManaPool mana;

    @Embedded
    private ActionPoints ap;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "resourceType", column = @Column(name = "resource_type")),
            @AttributeOverride(name = "currentResource", column = @Column(name = "resource_current")),
            @AttributeOverride(name = "maxResource", column = @Column(name = "resource_max"))
    })
    private ClassResource resource;

    /** Money — ONE generic currency (Game Owner 2026-07-06: the copper/silver/gold
     *  tier concept is gone); all shop prices are in this unit. Wrapper so legacy
     *  rows (null column) load as 0. */
    @Column(name = "gold")
    private Integer gold;

    private int speed;
    private int bonusInitiative;
    private int deathStacks;

    // --- Death & dying (M2-D) — wrappers so pre-existing rows load as sane defaults ---

    @Enumerated(EnumType.STRING)
    private LifeStatus lifeStatus;

    private Integer downedRoundsRemaining;
    private Boolean pendingDeathFight;
    private Integer downsThisCombat;

    // --- Bio / Narrative ---

    @Column(length = 4000)
    private String backstory;

    @Column(length = 2000)
    private String notes;

    @Column(length = 2000)
    private String personalityTraits;

    @Column(length = 2000)
    private String ideals;

    @Column(length = 2000)
    private String bonds;

    @Column(length = 2000)
    private String flaws;

    @Column(length = 2000)
    private String allies;

    @Column(length = 2000)
    private String organizations;

    @Column(length = 2000)
    private String titles;

    private String symbolUrl;

    // --- Appearance ---

    @Embedded
    private Appearance appearance;

    // --- Saving throws ---

    @ElementCollection
    @CollectionTable(name = "character_saving_throw_profs", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "ability")
    @Enumerated(EnumType.STRING)
    private List<AbilityScore> savingThrowProficiencies = new ArrayList<>();

    // --- Collections ---

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("appliedAt ASC")
    private List<ActiveEffect> activeEffects = new ArrayList<>();

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryEntry> inventory = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "character_known_spells", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "spell_id")
    private List<String> knownSpells = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "character_prepared_spells", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "spell_id")
    private List<String> preparedSpells = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "character_proficiencies", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "skill_id")
    private List<String> proficiencies = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "character_talents", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "talent_id")
    private List<String> talents = new ArrayList<>();

    /** Specialization feat picks at 5/9/13 (M6-C): "active" | "passive" | "modification". */
    @ElementCollection
    @CollectionTable(name = "character_spec_feats", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "feat_slot")
    private List<String> specFeats = new ArrayList<>();

    /** Sub-resource pools (Epic 1 / Story 1.2) — materialized lazily from abilities-*.json defs. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_pools", joinColumns = @JoinColumn(name = "player_id"))
    private List<CharacterPool> pools = new ArrayList<>();

    /** Choice-group ability picks (Story 1.3, free-form picker); group-null abilities are implicit. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_known_abilities", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "ability_id")
    private List<String> knownAbilities = new ArrayList<>();

    /** Player-written free-text abilities pending official rulings (2026-07-20). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_custom_abilities", joinColumns = @JoinColumn(name = "player_id"))
    @OrderColumn(name = "idx")
    private List<CustomAbility> customAbilities = new ArrayList<>();

    /** Reactions readied this turn (2026-08-27) — AP already paid; lapse when the next turn starts. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_prepared_reactions", joinColumns = @JoinColumn(name = "player_id"))
    @OrderColumn(name = "idx")
    private List<PreparedReaction> preparedReactions = new ArrayList<>();

    /** Use counters for limit-bearing abilities (Story 1.4). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_ability_uses", joinColumns = @JoinColumn(name = "player_id"))
    private List<AbilityUse> abilityUses = new ArrayList<>();

    /** Player/GM-defined weapons and armor (demo feedback #19), owned by this sheet. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_custom_items", joinColumns = @JoinColumn(name = "player_id"))
    private List<CustomItem> customItems = new ArrayList<>();

    /**
     * Table escape hatch (demo feedback #11/#12): a derived stat the GM has pinned to a
     * literal value because the character has something the formulas don't model. Keyed
     * by {@link OverridableStat#getKey()}. The override replaces the FORMULA only —
     * active effects still modify the result — and clearing the key returns to derivation.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_stat_overrides", joinColumns = @JoinColumn(name = "player_id"))
    @MapKeyColumn(name = "stat")
    @Column(name = "override_value") // "value" is reserved in H2
    private Map<String, Integer> statOverrides = new HashMap<>();

    protected GameCharacter() {}

    public GameCharacter(String playerId) {
        this.playerId = playerId;
    }

    // --- Helpers ---

    public void addEffect(ActiveEffect effect) {
        activeEffects.add(effect);
        effect.setCharacter(this);
    }

    public void removeEffect(ActiveEffect effect) {
        activeEffects.remove(effect);
        effect.setCharacter(null);
    }

    // --- Combatant (ADR-001): players are combatants under their playerId ---

    @Override
    public String getCombatantId() { return playerId; }

    @Override
    public int getInitiativeBonus() { return bonusInitiative; }

    /** Race damageTaken multipliers from races.json (empty for an unknown/missing race). */
    @Override
    public Map<DamageType, Double> innateDamageTaken(GameDataProvider gameData) {
        return gameData.getRaceDamageTaken(raceId);
    }

    @Override
    public String innateDamageTakenSource() { return "racial: " + raceId; }

    /** Players use the downed window + Medicine revival (M2-D). */
    @Override
    public boolean usesDeathRules() { return true; }

    public void addItem(InventoryEntry entry) {
        inventory.add(entry);
        entry.setCharacter(this);
    }

    // --- Getters and setters ---

    public String getPlayerId() { return playerId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPortraitUrl() { return portraitUrl; }
    public void setPortraitUrl(String portraitUrl) { this.portraitUrl = portraitUrl; }

    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }

    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }

    public String getBackstory() { return backstory; }
    public void setBackstory(String backstory) { this.backstory = backstory; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPersonalityTraits() { return personalityTraits; }
    public void setPersonalityTraits(String personalityTraits) { this.personalityTraits = personalityTraits; }

    public String getIdeals() { return ideals; }
    public void setIdeals(String ideals) { this.ideals = ideals; }

    public String getBonds() { return bonds; }
    public void setBonds(String bonds) { this.bonds = bonds; }

    public String getFlaws() { return flaws; }
    public void setFlaws(String flaws) { this.flaws = flaws; }

    public String getAllies() { return allies; }
    public void setAllies(String allies) { this.allies = allies; }

    public String getOrganizations() { return organizations; }
    public void setOrganizations(String organizations) { this.organizations = organizations; }

    public String getTitles() { return titles; }
    public void setTitles(String titles) { this.titles = titles; }

    public String getSymbolUrl() { return symbolUrl; }
    public void setSymbolUrl(String symbolUrl) { this.symbolUrl = symbolUrl; }

    public Appearance getAppearance() { return appearance; }
    public void setAppearance(Appearance appearance) { this.appearance = appearance; }

    public String getRaceId() { return raceId; }
    public void setRaceId(String raceId) { this.raceId = raceId; }

    public String getPathId() { return pathId; }
    public void setPathId(String pathId) { this.pathId = pathId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getSpecializationId() { return specializationId; }
    public void setSpecializationId(String specializationId) { this.specializationId = specializationId; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public Stats getStats() { return stats; }
    public void setStats(Stats stats) { this.stats = stats; }

    public HitPoints getHp() { return hp; }
    public void setHp(HitPoints hp) { this.hp = hp; }

    public ManaPool getMana() { return mana; }
    public void setMana(ManaPool mana) { this.mana = mana; }

    public ActionPoints getAp() { return ap; }
    public void setAp(ActionPoints ap) { this.ap = ap; }

    public ClassResource getResource() { return resource; }
    public void setResource(ClassResource resource) { this.resource = resource; }

    public int getGold() { return gold != null ? gold : 0; }
    public void setGold(int gold) { this.gold = gold; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public int getBonusInitiative() { return bonusInitiative; }
    public void setBonusInitiative(int bonusInitiative) { this.bonusInitiative = bonusInitiative; }

    public int getDeathStacks() { return deathStacks; }
    public void setDeathStacks(int deathStacks) { this.deathStacks = deathStacks; }

    public LifeStatus getLifeStatus() { return lifeStatus != null ? lifeStatus : LifeStatus.ALIVE; }
    public void setLifeStatus(LifeStatus lifeStatus) { this.lifeStatus = lifeStatus; }

    public Integer getDownedRoundsRemaining() { return downedRoundsRemaining; }
    public void setDownedRoundsRemaining(Integer downedRoundsRemaining) { this.downedRoundsRemaining = downedRoundsRemaining; }

    public boolean isPendingDeathFight() { return Boolean.TRUE.equals(pendingDeathFight); }
    public void setPendingDeathFight(boolean pendingDeathFight) { this.pendingDeathFight = pendingDeathFight; }

    public int getDownsThisCombat() { return downsThisCombat != null ? downsThisCombat : 0; }
    public void setDownsThisCombat(int downsThisCombat) { this.downsThisCombat = downsThisCombat; }

    public List<AbilityScore> getSavingThrowProficiencies() { return savingThrowProficiencies; }
    public List<ActiveEffect> getActiveEffects() { return activeEffects; }
    public List<InventoryEntry> getInventory() { return inventory; }
    public List<String> getKnownSpells() { return knownSpells; }
    public List<String> getPreparedSpells() { return preparedSpells; }
    public List<PreparedReaction> getPreparedReactions() { return preparedReactions; }
    public List<String> getProficiencies() { return proficiencies; }
    public List<String> getTalents() { return talents; }
    public List<String> getSpecFeats() { return specFeats; }

    public List<CharacterPool> getPools() { return pools; }

    public CharacterPool findPool(String poolId) {
        return pools.stream().filter(p -> p.getPoolId().equals(poolId)).findFirst().orElse(null);
    }

    public List<String> getKnownAbilities() { return knownAbilities; }

    public List<CustomAbility> getCustomAbilities() { return customAbilities; }

    public List<AbilityUse> getAbilityUses() { return abilityUses; }

    /** Null-safe: legacy rows predate the table. */
    public List<CustomItem> getCustomItems() {
        if (customItems == null) customItems = new ArrayList<>();
        return customItems;
    }

    /** This sheet's custom weapon/armor by id, or null when the id isn't one of theirs. */
    public CustomItem customItem(String itemId) {
        if (itemId == null) return null;
        return getCustomItems().stream()
                .filter(i -> itemId.equals(i.getItemId()))
                .findFirst().orElse(null);
    }

    /** Null-safe: legacy rows persisted before the column existed come back null. */
    public Map<String, Integer> getStatOverrides() {
        if (statOverrides == null) statOverrides = new HashMap<>();
        return statOverrides;
    }

    /** The pinned value for a stat, or null when it should be derived normally. */
    public Integer overrideFor(OverridableStat stat) {
        return getStatOverrides().get(stat.getKey());
    }

    /** Use counter for one ability, created on first use. */
    public AbilityUse abilityUse(String abilityId) {
        return abilityUses.stream()
                .filter(u -> u.getAbilityId().equals(abilityId))
                .findFirst()
                .orElseGet(() -> {
                    var use = new AbilityUse(abilityId);
                    abilityUses.add(use);
                    return use;
                });
    }
}
