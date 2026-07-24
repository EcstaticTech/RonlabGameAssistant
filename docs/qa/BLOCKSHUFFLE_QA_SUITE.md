# BlockShuffle & Multi-Client QA Pre-Flight Test Suite

> [!IMPORTANT]
> **Pre-Flight Verification Protocol**: This QA suite details the step-by-step procedure for verifying multi-client minigame sessions, inventory isolation, party persistence, and session conclusion under Paper 26.1.

---

## 1. Test Environment Setup

- [ ] **Paper Version**: Paper 26.1 (Minecraft 1.21.x equivalent build).
- [ ] **Plugin Build**: `rga-plugin-1.12.0.jar` deployed in `plugins/`.
- [ ] **Config Check**: `minigames.yml` verified with `min-players: 2` and `max-players: 8`.
- [ ] **Test Clients**: 2 client instances connected (Tester Alpha / Party Leader, Tester Beta / Member).

---

## 2. Multi-Client Test Cases

### Test Case 1: Party Creation & Lobby GUI Navigation
- **Actions**:
  1. Tester Alpha executes `/party create`.
  2. Tester Alpha executes `/party invite TesterBeta`.
  3. Tester Beta accepts party invite via `/party accept TesterAlpha` (or clicking text component).
  4. Tester Alpha opens `/rga gui` and selects a dynamic minigame (e.g. Manhunt or BlockShuffle).
- **Expected Outcome**:
  - Party forms successfully.
  - Party leader (Tester Alpha) initiates minigame queue.
  - Notification components display correctly to both clients without color code formatting errors.

---

### Test Case 2: World Copy, Inventory Isolation & Hotbar Slotting
- **Actions**:
  1. Both players wait for world creation and multi-stage teleport sequence into `minigame_<uuid>`.
  2. Inspect player inventories upon arrival in minigame world.
  3. Perform game actions (collect items, change hotbar selection).
  4. Conclude game via `/rga conclude` or API trigger.
- **Expected Outcome**:
  - World directory `minigame_<uuid>` created under Paper 26.1 standards with datapacks stripped.
  - Hub items (Navigator Compass & Social Head) removed while in minigame.
  - Upon return to Hub, inventory is reset/cleared according to config, and Navigator Compass is placed cleanly into **Slot 8** (index 8) and Social Head into **Slot 7** (index 7).

---

### Test Case 3: Disconnect & Write-Ahead Recovery Verification
- **Actions**:
  1. Start a 2-player session.
  2. Tester Beta forcibly disconnects (Alt+F4 or client drop).
  3. Simulate server restart while session is active (or verify write-ahead session file `.rga_session.json` in world folder).
  4. Restart server and re-join with Tester Beta.
- **Expected Outcome**:
  - Session state persists safely on disk.
  - On restart, `SessionManager` detects session recovery state or flags orphaned state cleanly without NPE log crashes.
  - Rejoining player recovers position or returns safely to Hub.

---

### Test Case 4: Console Log Hygiene & Reload Check
- **Actions**:
  1. Execute `/rga reload` 3 times consecutively from console.
  2. Stop server and restart.
- **Expected Outcome**:
  - ZERO `Could not save worlds.yml because it already exists` or `menus.yml` resource save warnings in console logs.
