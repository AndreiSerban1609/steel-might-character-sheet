package com.steelmight.charactersheet.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The GM's reusable monster stat block, room-scoped (ruling E9 — per room, with JSON
 * export/import so goblins can travel between rooms). Stamping one into a fight creates a
 * {@link MonsterInstance} that copies the block.
 */
@Entity
@Table(name = "monster_templates")
public class MonsterTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roomName;
    private String name;

    @Embedded
    private MonsterBlock block = MonsterBlock.empty();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "monster_template_saving_throw_profs", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "ability")
    @Enumerated(EnumType.STRING)
    private List<AbilityScore> savingThrowProficiencies = new ArrayList<>();

    /** Innate resist (<1) / vulnerability (>1) / immunity (0) multipliers per damage type. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "monster_template_damage_taken", joinColumns = @JoinColumn(name = "template_id"))
    @MapKeyColumn(name = "damage_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "multiplier")
    private Map<DamageType, Double> damageTaken = new HashMap<>();

    protected MonsterTemplate() {}

    public MonsterTemplate(String roomName, String name) {
        this.roomName = roomName;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getRoomName() { return roomName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public MonsterBlock getBlock() { return block; }
    public void setBlock(MonsterBlock block) { this.block = block; }
    public List<AbilityScore> getSavingThrowProficiencies() { return savingThrowProficiencies; }
    public Map<DamageType, Double> getDamageTaken() { return damageTaken; }
}
