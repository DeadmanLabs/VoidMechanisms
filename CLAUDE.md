# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

VoidSpaces is a NeoForge 1.21 Minecraft mod that creates machines inside void dimensions to reduce lag. The mod allows players to create isolated dimensional spaces where complex machinery can operate without affecting the main world's performance.

## Build and Development Commands

### Building the Project
```bash
./gradlew build
```

### Running the Game
```bash
# Run client
./gradlew runClient

# Run server  
./gradlew runServer

# Run data generation
./gradlew runData
```

### Development Tasks
```bash
# Clean build artifacts
./gradlew clean

# Refresh dependencies if missing libraries
./gradlew --refresh-dependencies
```

## Architecture Overview

### Core Systems

**Infiniverse API** (`src/main/java/com/deadman/voidspaces/infiniverse/`)
- Dynamic dimension creation and management system
- `InfiniverseAPI`: Main interface for dimension operations
- `DimensionManager`: Internal implementation for creating/destroying dimensions
- Handles runtime dimension registration without server restart

**Dimensional Management** (`src/main/java/com/deadman/voidspaces/helpers/`)
- `Dimensional`: Main wrapper class for managing void spaces
- `DimensionalLevel`: Custom ServerLevel implementation for void dimensions  
- `DimensionalWorldBorder`: Custom world border management
- `Space`: Utilities for extracting/placing machine contents between dimensions

**Block System** (`src/main/java/com/deadman/voidspaces/block/`)
- Void machine blocks: Accelerator, Engine, Extractor, Injector, Stabilizer
- Each block has corresponding entity classes in `block/entity/`
- Machines operate within isolated void dimensions

### Key Design Patterns

**Dimension Lifecycle**: Dimensions are created on-demand when players enter void spaces, and can be marked for cleanup when no longer needed.

**Content Serialization**: Machine contents are extracted as `SpaceContents` when leaving dimensions and restored when re-entering.

**Player State Management**: Players entering void spaces get temporary creative mode with saved inventory, restored when exiting.

**World Border Enforcement**: Strict 10x10 block boundaries prevent players from leaving designated machine areas.

## Project Structure

- `src/main/java/com/deadman/voidspaces/` - Main source code
  - `init/` - Registration classes for blocks, items, menus, etc.
  - `block/` - Machine blocks and their entity implementations  
  - `item/` - Void-related items (alloy, blueprint, card, core, frame)
  - `helpers/` - Dimensional management and utilities
  - `infiniverse/` - Dynamic dimension creation system
- `src/main/resources/` - Assets and data files
  - `assets/voidspaces/textures/gui/` - GUI textures
  - `data/voidspaces/recipe/` - Crafting recipes

## Configuration

- Mod properties defined in `gradle.properties`
- Mod ID: `voidspaces`  
- Target: Minecraft 1.21 with NeoForge 21.0.167
- Requires Java 21