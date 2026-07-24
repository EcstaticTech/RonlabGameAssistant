# ADR-0002: Datapack Isolation Strategy & Global Server Registries

- **Status**: Accepted
- **Date**: 2026-07-24
- **Authors**: Ronlab Game Assistant Maintainers
- **Target Issue**: Issue #28 — Evaluate Datapack Isolation Strategy
- **Related ADRs**: ADR-0001 (Issue #24: Event-driven Companion Plugin API)

---

## 1. Context & Problem Statement

Minecraft 1.20+ / 1.26+ and CraftBukkit/Paper handle datapacks (recipes, functions, loot tables, tags, advancements, and dimension types) via global server registries loaded at server startup or during `/minecraft:reload`.

When Ronlab Game Assistant (RGA) instantiates minigame session worlds by copying template folders (`WorldCopyManager`), questions arise regarding whether template-level `/datapacks/` directories isolate per minigame session, or if they bleed across worlds.

### Upstream CraftBukkit / Paper Architecture
Per PaperMC upstream tracking (PaperMC/Paper#4314, PaperMC/Paper#7347, PaperMC/Paper#10147):
- Datapacks placed inside sub-world directories (e.g. `/worlds/minigame_session_123/datapacks/`) are physically ignored by CraftBukkit/Paper.
- All active server datapacks reside strictly in the root world directory (`/world/datapacks/`) and affect all loaded worlds globally.
- Paper maintainers have closed per-world datapack requests as *Won't Fix* due to CraftBukkit's single-registry architecture. Dynamic per-world switching within a single server instance is not exposed via standard Paper API.

---

## 2. Empirical Behavioral Analysis

### A. Static Propagation (Template Baked Data)
- **Behavior**: When a template world is created while custom terrain or structure datapacks are active in `/world/datapacks/`, custom terrain, biomes, structures, and container NBT loot are **baked directly into chunk region files**.
- **Result in Sessions**: When RGA copies the template to a session world, players in pre-generated chunks experience all visual and structural consequences of the creation-time datapack.

### B. Dynamic Non-Propagation (Session Folder Inertia & Global Registry Leakage)
- **Session Folders**: `/datapacks/` sub-folders copied into session world directories are ignored by Paper. They consume disk space but register no recipes, functions, tags, or loot tables.
- **Global Leakage**: If an admin places a datapack in the main server root (`/world/datapacks/`), its custom recipes, tick functions, advancements, and block/item tags apply to all minigames globally.
- **Chunk Boundary Discontinuity**: If players explore beyond pre-generated chunk boundaries, new chunks are generated using server-level global datapacks (or vanilla generation). If a creation-time datapack is not present in the global directory, a visible terrain boundary discontinuity occurs where pre-generated template terrain meets newly generated vanilla terrain.

---

## 3. Decision Record (ADR-0002)

To maintain server performance, prevent disk bloat, and establish clear operational expectations:

1. **Unconditional Datapack Stripping in RGA (Issue #39 Follow-up)**:
   `WorldCopyManager` will skip the `/datapacks/` directory during `copyTemplateWorld()` session creation. Session worlds will not contain inert `/datapacks/` folders.
2. **Java-First Minigame Mechanics**:
   Minigame features (rules, timers, scoreboards, custom items, loot generation, and win/loss conditions) must be implemented via Java companion plugins (e.g., Block-Shuffle) utilizing Bukkit/Paper Event APIs rather than Vanilla `.mcfunction` datapacks.
3. **Mandatory Template Pre-generation**:
   Template worlds utilizing custom terrain datapacks during creation must be fully pre-generated across their playable area prior to template import (`/rga importworld`).
4. **Velocity Proxy Isolation Tier (Optional)**:
   For minigames requiring non-replicable datapack overhauls (custom dimension types or extensive Vanilla `.mcfunction` packs), deployment must occur on dedicated sub-servers behind a proxy network (e.g. Velocity).

---

## 4. Java Plugin Replication Feasibility & Override Patterns

### Feasibility Matrix

| Datapack Feature | Java Plugin Replication | Implementation Strategy / Limitation |
| --- | --- | --- |
| Custom Recipes | ✅ Full | `Bukkit.addRecipe()` or per-world `CraftItemEvent` listeners |
| Custom Loot Tables | ⚠️ Partial | `LootGenerateEvent` listeners in session worlds |
| Custom Advancements | ⚠️ Partial | Paper Advancement API |
| Custom Functions (`.mcfunction`) | ✅ Full | `BukkitScheduler` + command execution |
| Custom Item Attributes | ⚠️ Partial | `ItemStack` NBT / `AttributeModifier` API |
| Custom Block/Item Tags | ❌ Cannot | Bukkit Tag API is read-only; use `BlockBreakEvent` / `PlayerInteractEvent` cancellation |
| Custom World Generation | ❌ Cannot | Pre-generate template worlds prior to import |
| Custom Dimension Types | ❌ Cannot | Requires Velocity proxy isolation tier |

### Global Datapack Java Override Patterns

When global server datapacks create unwanted side effects in minigame worlds, companion plugins can neutralize them using session-scoped Java event listeners:

#### Recipe Restrictions in Minigames
```java
@EventHandler
public void onCraft(CraftItemEvent event) {
    if (isMinigameSessionWorld(event.getWhoClicked().getWorld())) {
        if (!MINIGAME_ALLOWED_RECIPES.contains(event.getRecipe())) {
            event.setCancelled(true);
        }
    }
}
```

#### Custom Loot Overrides in Minigames
```java
@EventHandler
public void onLootGenerate(LootGenerateEvent event) {
    if (isMinigameSessionWorld(event.getWorld())) {
        event.setLoot(generateMinigameLoot(event.getLootTable()));
    }
}
```

#### Block Interaction Rules in Minigames
```java
@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    if (isMinigameSessionWorld(event.getBlock().getWorld())) {
        if (!MINIGAME_ALLOWED_BLOCKS.contains(event.getBlock().getType())) {
            event.setCancelled(true);
        }
    }
}
```

---

## 5. Velocity Proxy Isolation Tier — Operational Cost Assessment

For minigames that cannot function without non-replicable datapack features (custom dimension types or core tag modifications), running a dedicated sub-server behind a Velocity proxy is the only network boundary tier:

### Operational Requirements & Costs
- **Multi-Instance Server Footprint**: Requires running at least 2 Minecraft server instances (main hub + minigame server), requiring separate RAM allocation (minimum 1-2GB+ per instance).
- **Proxy Routing**: Requires a Velocity proxy instance configured for player server switching.
- **State Synchronization**: Requires cross-server state sync for player data (inventory, scoreboards, advancements) via plugin messaging channels or shared databases.
- **Management Complexity**: Quadruples configuration maintenance (multiple `server.properties`, `paper-global.yml`, and monitoring scripts).

*Recommendation*: For 95%+ of minigames, the Java companion plugin approach on a single RGA server instance is sufficient and recommended.

---

## 6. Related Concern: Resource Pack Isolation (Out of Scope)

Resource packs (client-side textures, models, sound events) share a single-stack limitation in Paper: the server sends one resource pack stack per player, not per world. 

Minigames requiring custom client-side assets must either:
1. Accept the client-side loading screen when switching resource packs between worlds.
2. Design minigame assets to fit within the server's global resource pack stack.
3. Deploy behind Velocity proxy isolation.

Resource pack isolation is explicitly out of scope for ADR-0002 but may be evaluated in a future architectural review.

---

## 7. Future Considerations

1. **Session World Borders**: Implementing a `world-border-size` config option in `minigames.yml` to prevent players from wandering past pre-generated template boundaries in session worlds.
2. **Datapack Stripping in `WorldCopyManager`**: Tracked as Issue #39 for RGA v1.12.x / Phase 5.
