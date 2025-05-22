# Engine Block Persistence and Tooltip Plan

## Background

The `EngineEntity` currently stores a `Dimensional` instance at runtime, including the custom dimension key and the player's return location. However, this information is not persisted to NBT or transferred when the block is broken, so unloading/reloading the world or moving the engine block loses this state.

## Objectives
1. Ensure that when the world unloads and reloads, an engine block retains its custom dimension and return-location data.
2. When the engine block is broken and dropped as an item, the dropped item carries the engine's dimension and return-location.
3. When the block item is placed again, the engine entity restores the previous dimension and return-location state.
4. (Optional) Display relevant information (dimension name, owner) in the block/item tooltip.

## Implementation Plan

### 1. Extend `EngineEntity` NBT read/write
- **saveAdditional**: Persist the following tags in the block entity NBT:
  - `owner` (UUID) — already persisted.
  - `dimension` (String) — registry name of the custom dimension key.
  - `returnLocation` (CompoundTag):
    - `origDim` (String) — original world dimension registry name.
    - `x`, `y`, `z` (Int) — original player block position.
- **loadAdditional**: Read these tags and:
  - Reconstruct the `Dimensional` instance using the existing-dimension constructor.
  - Restore the return-location entry in `Dimensional.returnPositions` for the owner.

### 2. Transfer NBT between tile and item
- Create `VoidEngineBlockItem extends BlockItem` (or override the existing item) and register it for the engine block.
- Override the item’s NBT share methods (`getShareTag` / `getBlockEntityData` and `updateCustomBlockEntityTag` / `readShareTag`) so that when the block is broken, the engine's NBT is attached to the dropped item, and when the item is placed, that NBT is re-applied to the new tile entity.

### 3. Placement hook in `VoidEngine#setPlacedBy`
- Update `setPlacedBy(Level, BlockPos, BlockState, LivingEntity, ItemStack)` to detect persisted engine data on the `ItemStack`:
  - If the stack has our engine NBT, call a new helper `EngineEntity.readPersistedData(CompoundTag)` to initialize owner, dimension, and return-location.
  - Otherwise, fall back to assigning a new owner via `setOwner(placerUUID)`.

### 4. Tooltip enhancement (optional)
- Override `VoidEngineBlockItem.appendHoverText` to read the NBT from the `ItemStack` and display:
  - Custom dimension registry name.
  - Owner player name or UUID.
  - (Optionally) the original return position.

### 5. Testing and Verification
1. Run in a development environment:
   - Place an engine, set the owner, teleport in, and teleport out.
   - Break the engine block, pick up the engine item, reload the world.
   - Place the engine again and verify that pressing "O" returns the player to the same custom dimension and original location.

---
Once this plan is approved, implementation will proceed in a follow-up commit.