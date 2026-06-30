package com.steelmight.charactersheet.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "characters")
public class GameCharacter {

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

    private int gold;
    private int speed;
    private int bonusInitiative;
    private int deathStacks;

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

    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public int getBonusInitiative() { return bonusInitiative; }
    public void setBonusInitiative(int bonusInitiative) { this.bonusInitiative = bonusInitiative; }

    public int getDeathStacks() { return deathStacks; }
    public void setDeathStacks(int deathStacks) { this.deathStacks = deathStacks; }

    public List<AbilityScore> getSavingThrowProficiencies() { return savingThrowProficiencies; }
    public List<ActiveEffect> getActiveEffects() { return activeEffects; }
    public List<InventoryEntry> getInventory() { return inventory; }
    public List<String> getKnownSpells() { return knownSpells; }
    public List<String> getPreparedSpells() { return preparedSpells; }
    public List<String> getProficiencies() { return proficiencies; }
    public List<String> getTalents() { return talents; }
}
