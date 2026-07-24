# Architectural Decision Record (ADR): Testing Framework Strategy (#35)

## 1. Context

During the evaluation of testing strategies for the **Ronlab Game Assistant (RGA)** codebase and event API (`com.ronlab:rga-api`), the engineering team analyzed whether to adopt **MockBukkit** as a primary mock server environment or to continue leveraging the existing **Dynamic Proxy & Mockito** testing pattern.

RGA relies on event dispatching (`MinigameStartEvent`, `MinigameConcludeEvent`), party state management (`PartyManager`), session persistence (`SessionManager`), and inventory group isolation (`InventoryManager`). Effective unit testing requires deterministic verification of event firing, state mutations, and error handling without flakiness or excessive execution overhead.

---

## 2. Decision Outcome

**Decision**: Continue using the existing **Proxy / Mockito testing pattern** for unit and integration testing across `rga-api` and `rga-plugin`.

Do **not** introduce `MockBukkit` as a dependency at this time.

---

## 3. Rationale

The choice to proceed with the Proxy/Mockito pattern is driven by three primary architectural factors:

1. **Paper 26.1.2 & Java 25 Compatibility**:
   - RGA targets Minecraft / Paper 26.1.2 running on Java 25.
   - MockBukkit currently lacks official support for Paper 26.1+ and Java 25 runtime environments. Attempting to run MockBukkit under Java 25 triggers bytecode inspection errors and classloader incompatibilities due to modern JVM module boundaries and reflection restrictions.

2. **Fast Unit Test Execution**:
   - The Proxy/Mockito pattern executes unit tests in milliseconds without initializing a full Bukkit server runtime (`Server`, `PluginManager`, `ItemFactory`, dynamic registries).
   - Test suites execute rapidly in CI/CD pipelines without memory inflation or slow bootstrap cycles.

3. **Explicit Control Over Event Firing & State Verification**:
   - Mockito mocks and dynamic proxies afford exact control over listener invocations, cancellation inspection, and return values (e.g., [ConcludeResult](file:///m:/projects/RonlabGameAssistant/rga-api/src/main/java/com/ronlab/rga/api/event/ConcludeResult.java)).
   - Tests isolate logic state (e.g., score mutations, rollback on start cancellation) without dealing with unhandled side effects from simulated Bukkit ticks or world loaders.

---

## 4. Re-evaluation Triggers

The decision to omit MockBukkit will be re-evaluated under the following conditions:

1. **Official Framework Support**:
   - MockBukkit releases an official build fully supporting Paper 26.1+ and Java 25 runtime environments without requiring unsafe reflection or JVM flag workarounds.

2. **Complex Tick & Scheduler Requirements**:
   - Future integration test suites require complex Bukkit scheduler tick simulation (`BukkitScheduler`), custom item meta / NBT tick processing, or physics event propagation that custom Mockito proxies cannot reasonably or cleanly replicate.

---

## Related Documentation

- [EVENT_API_KNOWN_LIMITATIONS.md](file:///m:/projects/RonlabGameAssistant/EVENT_API_KNOWN_LIMITATIONS.md)
- [ConcludeResult.java](file:///m:/projects/RonlabGameAssistant/rga-api/src/main/java/com/ronlab/rga/api/event/ConcludeResult.java)
