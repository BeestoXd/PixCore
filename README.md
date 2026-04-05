<div align="center">

# PixCore

**A feature-rich companion plugin for StrikePractice on Paper servers**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Paper](https://img.shields.io/badge/Paper-1.21-00AA00?style=flat-square&logo=data:image/svg+xml;base64,)](https://papermc.io/)
[![StrikePractice](https://img.shields.io/badge/StrikePractice-required-blue?style=flat-square)](https://www.spigotmc.org/resources/strikepractice-2-the-best-practice-plugin.46906/)
[![PlaceholderAPI](https://img.shields.io/badge/PlaceholderAPI-optional-lightgrey?style=flat-square)](https://github.com/PlaceholderAPI/PlaceholderAPI)
[![Version](https://img.shields.io/badge/version-1.0-purple?style=flat-square)]()
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](LICENSE)

PixCore extends StrikePractice with advanced duel mechanics, party fight modes, FFA arenas, leaderboard systems, and granular combat customization — all configurable through YAML files.

</div>

---

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Build from Source](#build-from-source)
- [Configuration](#configuration)
- [Commands](#commands)
- [PlaceholderAPI](#placeholderapi)
- [Project Structure](#project-structure)

---

## Features

### Combat Mechanics
- **Bow Hit Messages** — customizable hit feedback with combo tracking
- **Fireball Knockback & Cooldown** — fine-tune fireball behavior per kit
- **Pearl Cooldown** — configurable per-kit ender pearl delay
- **TNT Self-Knockback** — independent TNT knockback multiplier
- **Stick Fight** — dedicated stick fight mode with custom rules
- **No-Fall Damage** — toggleable no-fall per match context
- **Hit Action Bar** — real-time damage info in the action bar
- **Kill Messages** — fully customizable death/kill announcements

### Match & Duel
- **Match Duration Tracker** — live timer displayed on scoreboard
- **Duel Score Tracking** — round-based best-of series support
- **End Match Effects** — title/sound sequences at match end
- **Void Manager** — configurable void-kill threshold
- **Pearl Fight Mode** — 3-life pearl fight with automatic round handling
- **Respawn Countdown** — animated respawn timer with safety protection
- **Custom Beds** — BedWars-style bed destruction with titles and effects

### Party System
- **Party vs Party** — organized team duels via StrikePractice party
- **Party FFA** — all-party members in a free-for-all arena
- **Party Split** — auto-split party into two balanced teams

### FFA Mode
- **Dedicated FFA Arena** — persistent spawn and boundary management
- **Combat Request System** — 20-second reciprocal hit requirement before combat
- **Battle Kit** — saveble custom kit layout per player
- **Hub Integration** — lobby items for quick join/leave

### Leaderboard & Scoreboard
- **Leaderboard GUI** — in-game clickable leaderboard
- **Hologram Display** — DecentHolograms/persistent hologram support
- **Scoreboard Title Animation** — animated titles cycling on the sidebar
- **PlaceholderAPI Support** — expose stats to external scoreboards

### Utility
- **Arena Boundary** — block-level boundary enforcement
- **Block Replenishment** — auto-refill blocks after use
- **Block Disappear Effects** — timed block removal for specific kits
- **Spawn Safety** — break-animation shield on respawn tiles
- **Save Layout** — save a manually-built kit mid-match as a template

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Paper / Folia | 1.21 |
| StrikePractice | Latest |
| PlaceholderAPI | 2.12+ *(optional)* |

---

## Installation

PixCore does not provide pre-built releases. You need to build the plugin from source.

1. Clone the repository and build the JAR — see [Build from Source](#build-from-source).
2. Place the output `target/pixcore-1.0.jar` into your server's `plugins/` directory.
3. Ensure **StrikePractice** is already installed and loaded.
4. *(Optional)* Install **PlaceholderAPI** for custom placeholder support.
5. Start or restart your server.
6. Configuration files will auto-generate under `plugins/PixCore/`.

---

## Build from Source

**Prerequisites:** Java 21 JDK, Maven 3.9+

```bash
git clone https://github.com/BeestoXd/PixCore.git
cd PixCore
mvn clean package
```

The compiled plugin will be at `target/pixcore-1.0.jar`.

> **Note:** `libs/StrikePractice.jar` is required for compilation and is included in the repository as a local Maven dependency.

---

## Configuration

PixCore generates **17 configuration files** on first startup, each scoped to a specific feature:

| File | Description |
|---|---|
| `config.yml` | Global messages, spawn protection, block settings |
| `pixffa.yml` | FFA arena, combat request rules, battle kit |
| `knockback.yml` | Fireball and TNT knockback multipliers |
| `fireballcooldown.yml` | Per-kit fireball cooldown |
| `pearlcooldown.yml` | Per-kit pearl cooldown |
| `bestof.yml` | Best-of series configuration |
| `matchduration.yml` | Match timer settings |
| `nofalldamage.yml` | No-fall damage toggle |
| `bowhit.yml` | Bow hit message format |
| `killmessage.yml` | Kill/death message format |
| `hitactionbar.yml` | Action bar display format |
| `stickfight.yml` | Stick fight mechanics |
| `tnttick.yml` | TNT explosion timing |
| `void.yml` | Void detection threshold |
| `arrowgive.yml` | Arrow auto-give settings |
| `buildlimit.yml` | Build height restriction |
| `custombeds.yml` | Custom bed titles and effects |

All files support hot-reload via `/pix reload`.

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/pix reload` | `pixcore.admin` | Reload all configuration files |
| `/pix pl` | `pixcore.admin` | Plugin diagnostic info |
| `/pix bed` | `pixcore.admin` | Bed utility commands |
| `/pix leaderboard` | `pixcore.admin` | Manage leaderboard data |
| `/pixffa [join\|leave\|pos\|reload\|battlekit]` | — | FFA arena commands |
| `/savelayout` | — | Save current inventory as a kit layout |
| `/leaderboard` | — | Open leaderboard GUI |
| `/l` | — | Leave the current match |
| `/party <subcommand>` | — | Party management (proxied to StrikePractice) |

---

## PlaceholderAPI

When PlaceholderAPI is installed, PixCore registers custom placeholders for use in scoreboards and holograms. Use `%pixcore_<placeholder>%` format.

---

## Project Structure

```
PixCore/
├── src/main/java/com/pixra/pixcore/
│   ├── PixCore.java           # Main plugin class & manager bootstrap
│   ├── command/               # Command executors
│   ├── feature/
│   │   ├── arena/             # Arena boundary management
│   │   ├── bed/               # Custom bed mechanics
│   │   ├── combat/            # Combat feature handlers
│   │   ├── ffa/               # FFA mode
│   │   ├── gameplay/          # General gameplay mechanics
│   │   ├── leaderboard/       # Leaderboard & holograms
│   │   ├── match/             # Match/duel lifecycle
│   │   └── party/             # Party fight modes
│   ├── integration/
│   │   ├── placeholderapi/    # PAPI hook
│   │   └── strikepractice/    # StrikePractice reflection bridge
│   └── support/               # Shared utility classes
├── src/main/resources/        # plugin.yml + 17 YAML configs
├── libs/                      # Local StrikePractice dependency
└── pom.xml
```

---

<div align="center">

Made with care for competitive Minecraft communities.

</div>
