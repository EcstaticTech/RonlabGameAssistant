# RGA Sprint Review Protocol

This document is the standing checklist for agentic sprint agents and human reviewers.
Run these steps **after every sprint or individual commit** before marking any work done.

---

## Step 1 — Version consistency check

Inspect all three fields and confirm they agree:

```bash
# In repo root:
grep "<version>" pom.xml | head -1
grep "^version:" src/main/resources/paper-plugin.yml
grep "RonlabGameAssistant-" .github/workflows/build.yml
```

If any field disagrees: **stop, report the exact mismatch, make no version changes.**

---

## Step 2 — Roadmap and issue alignment

1. Confirm `GITHUB_ISSUE_ROADMAP.md` marks the implemented issue as `[x]`
2. Confirm `CHANGELOG.md` has an entry under the correct version heading
3. Close the corresponding GitHub issue **only after the PR has been merged AND CI is green**:

```bash
gh issue close <N> --repo EcstaticTech641/RonlabGameAssistant
```

> [!WARNING]
> Do NOT self-close a tracking issue before independent CI verification and merge.
> Self-closing before green CI loses visibility into broken merges.

---

## Step 3 — Semver decision table

| Change type | Bump |
|-------------|------|
| Bug fix, no API surface change | patch (`1.10.x → 1.10.x+1`) |
| New feature, backward-compatible | minor (`1.10.x → 1.11.0`) |
| Breaking API change (field removed, package rename) | major (`1.x → 2.0.0`) |
| Documentation, tests, housekeeping only | **none** |
| Fixing stale version references | **none** |

---

## Step 4 — Apply the bump (if warranted)

Execute in this exact order — partial updates cause the inconsistency this
protocol exists to prevent:

1. Update `pom.xml` `<version>X.Y.Z</version>`
2. Update `src/main/resources/paper-plugin.yml` `version: 'X.Y.Z'`
3. `build.yml` uses `*.jar` wildcards — no edit needed unless switching to explicit
4. Add CHANGELOG entry under `### vX.Y.Z` heading
5. Commit: `git commit -m "chore: bump version to X.Y.Z"`
6. Tag: `git tag -a vX.Y.Z -m "Release vX.Y.Z — <description>"`
7. Push: `git push origin vX.Y.Z`
8. Mark roadmap items `[x]`, close GitHub issues

---

## Step 5 — Files to check every sprint

```
docs/HISTORICAL_PLAN.md                         (roadmap context)
README.md                                       (current-version field)
CHANGELOG.md                                    (latest entry)
docs/GITHUB_ISSUE_ROADMAP.md                    (issue status)
rga-plugin/src/main/resources/paper-plugin.yml (version:)
pom.xml                                         (<version>)
.github/workflows/build.yml                     (JAR filename — should be *.jar)
```

Quick GitHub check:
```bash
gh issue list --repo EcstaticTech641/RonlabGameAssistant --state open
```

---

## Step 6 — Sprint-agent-specific guards

These apply specifically when an autonomous agent is executing a sprint:

### Before starting any implementation

1. **Verify issue numbering live** — do not trust cross-reference numbers baked into
   planning documents. Run:
   ```bash
   gh issue list --repo EcstaticTech641/RonlabGameAssistant --state all \
     --limit 5 --json number | jq '.[0].number'
   ```
   If the highest issue number has drifted from what the plan expected, re-derive
   all `Depends on: #NN` references before proceeding.

2. **Verify JitPack coordinates** — re-derive from `git remote -v` at execution time.
   Do not trust coordinates baked into planning documents:
   ```bash
   git remote -v
   # JitPack groupId = com.github.<org> from the remote URL
   ```

3. **#36 validation is a hard Gate 1 blocker** — issues #32, #33, and #34 must not
   begin until the `rga-api` module has been extracted (#36) AND a throwaway
   `compileOnly` test project successfully resolves the artifact from JitPack.
   A module split that hasn't been end-to-end validated is worse than no split.

4. **Honor scope guards** — issue #31 explicitly prohibits adding event classes,
   `com.ronlab.rga.api` scaffolding, or `fireEvent()` calls. Each issue's body
   contains its scope boundary; read it before writing any code.

5. **One issue per PR** — do not batch multiple issues into a single diff. The
   dependency graph (#31→#33, #36→#32→#33/#34) requires sequential, reviewable merges.
