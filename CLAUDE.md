# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Steel & Might Character Sheet** — a digital character sheet for "Steel & Might" (S&M), a custom tabletop RPG system. This is **not** D&D — it has its own races, classes, damage types, card-based skill system, and action point economy. The app is an [Owlbear Rodeo](https://www.owlbear.rodeo/) (OBR) plugin that runs as an iframe SPA, backed by a Spring Boot REST API.

This project will integrate the existing **Deck of Fates** OBR extension (the card-draw skill check system) into the character sheet. The combined app will be deployed as a static SPA on GitHub Pages (`andreiserban1609.github.io`) with the backend on a VPS or cloud host.

## Game System Reference

The `.claude/` folder contains the authoritative game design PDFs. Always consult these before implementing game mechanics:

- **S&M Guide.pdf** — core rules (stats, combat, effects, spell components). Pages 1–37 are current; pages 37–54 are an old draft kept in the file. Death, resting, and skills are explicit "SPECIFY SECTION" placeholders — answered rules live in `.claude/requirements/OPEN-QUESTIONS.md`
- **S&M Guide_OLD.docx** (converted: `sm-guide-old.txt`) — the previous ruleset; source of the race entries (current-ish) and the Exhaustion ladder; other mechanics in it are superseded
- **S&M Races.pdf** — addendum covering only Dyionies/Jarghas/Eniyans + race-design notes
- **S&M Classes and Subclasses.pdf** — the 395-page master class document (per-subclass tables, resources, abilities)
- **S&M Classes Descriptions.pdf** — all 10 classes and 30 subclasses with core abilities
- **S&M Classes - Specializations.pdf** — specialization trees per subclass
- **S&M Shops.pdf** — shop items, prices, upgrade paths

### Key Game Concepts

- **Stats**: STR, DEX, CON, INT, WIS, CHA, **WILL** (7 ability scores — WILL is S&M-specific, used for Religion checks and certain class abilities like Cleric/Exorcist healing)
- **Action Points (AP)**: replaces D&D's action economy. Abilities cost AP; movement costs AP (speed is "X ft per AP")
- **Skill checks use a card deck, not dice**: each character has a 20-card deck with modifiers. Races and classes modify the deck (add cards, remove cards, change card values). Card types include draw cards, consume/burn cards (single use until rest), and redraw cards
- **11 magic damage types**: pure, spectral, light, shadow, fire, ice, lightning, poison, thunder, psychic, force
- **Physical armor (PA)** and **magic armor (MA)** are separate defense stats; **AC** is for dodging. Crushing damage ignores PA; true damage ignores everything; armor does not reduce start/end-of-turn (DoT) or environmental damage
- **Stack-based negative effects use a threshold system**: stacks accumulate dormant; when stacks ≥ threshold ceil(level/2) the effect fires, and at the **end of the afflicted creature's turn** `threshold`-many stacks are consumed (duration −1). Stacks clear on any rest (Guide pp.8–9; round-4 N9/N10)
- **A combat round ≈ 10 seconds** (1 minute ≈ 6 rounds)
- **Mana** is the spellcasting resource; some classes use alternatives (chakra, energy, focus, sacred energy, fury)
- **Class hierarchy**: **Path → Class → Specialization** (e.g., Musician *path* → Bard / Virtuoso / Minstrel *classes*). Renamed round-4 (N16): today's "subclass" is now a **class**, today's broad "class" is now a **path**. Id values are unchanged (`musician` = path id, `bard` = class id). Saving throws are per-class. 11 paths / 33 classes extracted, plus a 12th in-progress path **Specialist** (Sentry, Tinkerer — in the C&S PDF, extraction pending):
  - Musician (Bard, Virtuoso, Minstrel)
  - Disciple (Paladin, Cleric, Exorcist)
  - Wildborn (Druid, Shapeshifter, Stormbringer)
  - Warrior (Barbarian, Guardian, Conqueror)
  - Monk (Shaolin, Martyr, Hierophant)
  - Archer (Marksman, Arcane Ranger, Hunter)
  - Rogue (Assassin, Burglar, Shadow Trickster)
  - Corruptor (Spirit Master, Necromancer, Warlock)
  - Wizard (Sorcerer, Mindbender, Elementalist)
  - Battlemage (Striker, Battlecaster, Inquisitor)
  - Wraith Hunter (Runekeeper, Tormentor, Phantom)
- **Races** (24, all complete in `src/data/races.json`): Human, Elf, Dwarf, Stoneborn (Goliath replacement), Orc, Dwurgo (Goblin replacement), Oni, Tamari, Gnome, Forest Folk, Refur, Neko, Dragonborn, Centaur, Minotaur, Nyxari (Tiefling replacement), Naga, Dae'va, Kraka, Triton, Vampire, Dyionies, Jarghas, Eniyans. Each has an active ability, passive ability, one skill proficiency (or deck modification), movement speed (ft per AP), and `levelOneStats` — historically the 4 stats a race could raise at level 1, but **deprecated round-4 (N17)**: creation now spreads +5 across any stats (max 2 each), unconstrained by race

## Deck of Fates — Existing OBR Extension

The character sheet will integrate the existing Deck of Fates plugin, located at `C:\Projects\Steel_Might_Deck-of-fates\deck-of-fates-v5\card-deck-modifier\`. It is a standalone Vite + React 18 (JSX, no TypeScript) app currently deployed at `https://andreiserban1609.github.io/deck-of-fates/`.

### What Deck of Fates Does

Replaces traditional `+stat` modifiers with a **card deck draw mechanic** for skill checks. Instead of rolling a d20+modifier, a player draws a card from a personalized deck that modifies their d10 roll. The DM or player draws, the card applies its effect, and the result is computed.

### Card Types

| Type | Effect |
|------|--------|
| Steel Critical | DM decides outcome (ambiguous crit — silver themed) |
| Might Critical | DM decides outcome (ambiguous crit — red/fury themed) |
| Neutral | No modifier or small ±1/±2 |
| Encounter | Negative modifier + narrative flavor |
| Stat | Player's relevant ability modifier auto-applies |
| Class | Per-player custom cards, themed by class, with optional skill check restrictions and redraw effects |

### Deck Composition

Each player has a personalized deck built from: 2 locked criticals + GM-defined neutral/stat/encounter base template + player/GM customizations (add/remove stat cards, toggle neutrals, select encounter type, add class cards). Class cards can have consume (removed until long rest) or burn (removed permanently) mechanics.

### Key Integration Points

- **OBR Extension ID**: `com.deckoffates.modifier`
- **OBR metadata keys** (all under `com.deckoffates.modifier/`): `deckTemplate`, `playerConfigs`, `settings`, `currentDraw`, `currentDeck`, `drawHistory`, `playerDraws`
- **Ability scores**: STR, DEX, CON, INT, WIS, CHA, **WILL** (7 scores — the character sheet architecture has 6; WILL is S&M-specific and must be included)
- **Ability modifier formula**: `floor((score - 10) / 2)` (same as D&D)
- **19 skill checks** mapped to abilities: Lifting/Athletics→STR, Thievery/Reflex/Stealth→DEX, Knowledge/Arcana/Investigation→INT, Medicine/Perception/Survival/Animal Handling/Insight→WIS, Seduction/Performance/Persuasion/Deception/Intimidation→CHA, Religion→WILL
- **Proficiency system**: per-check proficiency grants redraws (gamble: forfeit current card, draw again)
- **Visibility modes**: DM-only or table-visible draws
- **Optional integrated d10**: animated hexagonal die roll with result breakdown
- **Player self-draw**: players can draw independently; DM sees it live
- **Draw history**: session log of all draws with full breakdown
- **11 class themes** with unique SVG card frame art and color palettes

### Deck of Fates Source Structure

```
src/
├── hooks/useOBR.js         # OBR SDK hooks (useOBR, useRoomMetadata)
├── lib/
│   ├── constants.js        # Extension ID, metadata keys, card types, skills, ability mappings
│   ├── deck.js             # buildDeck, shuffle, drawCard, getModifierDisplay, stat modifier calc
│   └── classThemes.js      # 11 class visual themes
├── components/
│   ├── CardArt.jsx         # Card face/back rendering with class themes + SVG frames
│   ├── CardFrames.jsx      # 16 SVG border frames (5 base + 11 class)
│   ├── CardDraw.jsx        # DM draw interface, redraw, auto-skip, history panel
│   ├── DeckEditor.jsx      # GM deck template editor + per-player overrides
│   ├── PlayerDeckEditor.jsx # Player-facing deck customization
│   ├── PlayerView.jsx      # Player broadcast listener + self-draw
│   ├── DiceRoll.jsx        # D10 dice animation
│   └── ResultBreakdown.jsx # Roll + card + stat + bonus = total
```

### Pending Feedback (from FEEDBACK.md in Deck of Fates repo)

- Neutral card: remove ±0 display
- Rename completed: Energy Critical → Might Critical (already done in constants)
- Card icon slots: bottom-left skill check type label, bottom-right redraw modifier flag on CLASS cards

### Style System

Dark fantasy aesthetic: deep dark backgrounds, gold/amber accents. Fonts: Cinzel (display), Crimson Text (body). All card rendering uses inline React styles (no CSS modules). SVG frames use 20px padding for protrusions.

## Architecture

See `.claude/ARCHITECTURE.md` for the full design. Summary of the critical decisions:

### Server as Game Server

The Spring Boot backend is the **single source of truth** for all character state. The frontend is a display layer that sends actions and renders results. OBR room metadata is a lightweight broadcast mirror — a viewport snapshot that syncs state to all clients in real-time.

### Data Tiers

| Tier | Storage | Examples | Constraint |
|------|---------|----------|------------|
| **Server state** | Spring Boot + Postgres | ALL character data — stats, HP, effects, inventory, backstory | Single source of truth. Server validates and resolves all mutations |
| **Broadcast mirror** | OBR room metadata (16kB cap) | Lightweight viewport snapshot of current tab | Real-time sync to all clients. Swappable per tab. Never the authority |
| **Static** | Bundled JSON in build (`src/data/`) | Spell definitions, item stats, class abilities, effect rules | Identical for everyone, zero metadata cost. Server also consumes these |
| **Computed** | Never stored — derived by server | Modifiers, AC, carry weight, spell DCs, effective stats after effects | Derivation = enforcement. Included in server responses, never persisted independently |

### OBR Metadata Viewport Swapping

Since the server owns all state, OBR metadata only holds whichever slice the player is currently viewing. Switching tabs swaps the viewport:
- **Combat tab**: stats, HP, AP, active effects, equipped gear (~1–2 kB)
- **Bio tab**: name, backstory, appearance, notes (~2–4 kB)
- **Inventory tab**: items, quantities, upgrade tiers, gold (~1–2 kB)
- **Spellbook tab**: known/prepared spells, mana, concentration (~1–2 kB)

Metadata key: `com.deckoffates.sheets/{playerId}/{viewport}`

### Effect Stack & Resolution Pipeline

Combat mechanics are resolved through a server-side pipeline. Each character has an ordered list of active effects. When a player submits an action (e.g., "take 35 fire damage"), the server walks it through every applicable rule (resistance → armor → auras → temp HP → HP → death checks → triggered effects) and returns a step-by-step breakdown. Every rule is a pure function: `(event, state) → (event, state, logEntry)`.

### Two Hard Rules

1. `domain/` never imports React, OBR SDK, or fetch — pure display logic only
2. `presentation/` never touches the SDK or backend — components → stores → platform

### Plugin Layer Structure

```
src/
├── platform/          # OBR SDK + HTTP (thinnest layer)
├── domain/            # PURE display logic (formatting, view helpers — no game mechanics)
├── application/       # Zustand stores (characterStore, combatStore, inventoryStore, spellStore)
├── data/              # Static JSON (27 files — classes, spells, effects, weapons, etc.)
├── presentation/      # React views and components
└── App.tsx            # Tab routing inside the iframe
```

### Backend (Spring Boot)

Action-based REST contract. Queries return viewport snapshots; actions mutate state through the validated pipeline and return resolution breakdowns + updated snapshots.

```
GET  /api/characters/{playerId}/combat      → CombatSnapshot
GET  /api/characters/{playerId}/bio         → bio fields
GET  /api/characters/{playerId}/inventory   → inventory + gold
GET  /api/characters/{playerId}/spells      → spellbook

POST /api/characters/{playerId}/actions/damage        → resolve damage through effect pipeline
POST /api/characters/{playerId}/actions/heal           → resolve healing (checks Maimed/Cursed/Decaying)
POST /api/characters/{playerId}/actions/apply-effect   → add effect to stack
POST /api/characters/{playerId}/actions/remove-effect  → remove effect
POST /api/characters/{playerId}/actions/turn-start     → tick start-of-turn effects
POST /api/characters/{playerId}/actions/turn-end       → tick end-of-turn effects
POST /api/characters/{playerId}/actions/purchase       → validate and process item purchase
POST /api/characters/{playerId}/actions/cast           → resolve spell
POST /api/characters/{playerId}/actions/rest           → restore resources by quality tier (25/50/75/100%); no short/long split (round-4 Q20)

PUT  /api/characters/{playerId}/bio         → upsert narrative data
```

CORS allows `https://andreiserban1609.github.io`. Identity is OBR player ID at face value (trusted table, no auth). Postgres via Spring Data JPA.

## Build & Development

This is a greenfield project. When bootstrapping, the planned stack is:
- **Frontend**: React + TypeScript + Zustand (state management), built with Vite, deployed to GitHub Pages
- **Backend**: Spring Boot + Spring Data JPA + Postgres (H2/SQLite acceptable for dev)
- **OBR SDK**: `@owlbear-rodeo/sdk` for plugin integration

## Domain Logic Principles

- **Server owns all mechanics.** The frontend never validates, computes, or resolves game rules. It sends actions to the server and displays results.
- **Computed values are never stored.** Modifiers, AC, carry weight, and DCs are derived by the server from character state + static data. Derivation = enforcement.
- **Static game data is the source of truth for definitions.** Item stats, spell costs, class abilities, effect rules — all come from bundled JSON in `src/data/`. Both the server and frontend consume these files.
- **The effect pipeline is deterministic and testable.** Each rule is a pure function. The full resolution is returned as a step-by-step breakdown for transparency.
