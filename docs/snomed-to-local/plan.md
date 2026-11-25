# Plan: Supporting SNOMED CT → Local Code System Maps

## Status
Draft — design discussion, not yet scheduled or estimated.

## Background

Snap2SNOMED currently supports one map direction: a flat local code system
(`ImportedCodeSet` / `ImportedCode`) is mapped onto SNOMED CT, which acts as
the rich, hierarchy- and ECL-capable target. AutoMap, scope (`Map.toVersion` /
`Map.toScope`), and the `MappingRelationship` enum are all implicitly
target-relative-to-source and SNOMED-is-the-target.

There is demand for the reverse: representing how SNOMED CT concepts
correspond to a (usually much smaller, flatter) local code system — e.g. for
exporting a lookup table a local system can use to translate incoming SNOMED
CT codes.

A naive "just swap source and target" produces a very sparse map, because
the local code system has far fewer, much coarser-grained codes than SNOMED
CT. The chosen approach: author a dense-but-small set of intensional
mappings from representative SNOMED concepts to local codes (reusing
today's authoring loop unchanged), then generate a dense map at export time
by having unmapped descendants inherit their nearest mapped ancestor's local
code. This mirrors the authoring pattern used for SNOMED CT's own complex
maps (e.g. SNOMED CT → ICD-10-CM), where a representative concept is mapped
and descendants inherit via subsumption rules unless overridden.

See conversation discussion (2026-11-24) for the full architectural survey
and semantic analysis this plan is based on.

## Key design decision

**Authoring stays exactly as it is today.** AutoMap, scope/ECL search, the
`MapRow`/`MapRowTarget` model, and the relationship enum are reused
unchanged. The only new things are:

1. A **map direction** flag, changing presentation (which column is shown
   as source vs target) and enabling export-time behavior.
2. An **export-time descendant-expansion transform** that is not persisted
   — generated rows never become `MapRow`/`MapRowTarget` records, so "only
   intensional rows are maintained" holds automatically.
3. A small amount of **new authoring-time UI** to mark subtree boundaries
   where inheritance should *not* apply, and to show coverage/gap feedback
   during authoring.

No new `MappingRelationship` values are needed — BROADER/NARROWER are
already a symmetric pair; reversed presentation is a display-layer swap.

## Implementation phases

### Phase 1 — Map direction metadata & presentation
- Add a direction field to `Map` (e.g. `mapDirection`: `LOCAL_TO_TARGET` /
  `TARGET_TO_LOCAL`), default `LOCAL_TO_TARGET` for backward compatibility.
- UI: when direction is `TARGET_TO_LOCAL`, swap which column renders as
  "source" vs "target" in the mapping grid, task list, and review screens.
  Underlying `MapRow`/`MapRowTarget` data and AutoMap flow are unchanged —
  this is purely a view-layer transform.
- Invert displayed relationship labels to match the shown direction
  (`TARGET_NARROWER` → "source is narrower than target" when SNOMED is
  shown as source), without adding new enum values.
- Update any map-level summary/progress UI to describe the correct roles
  ("local code" vs "SNOMED CT concept") based on direction.

### Phase 2 — Authoring-time guidance for this map type
- When direction is `TARGET_TO_LOCAL`, surface descendant-count context
  when an author assigns a correlation to a SNOMED concept (e.g. "this
  concept has 42 descendants in scope; they will inherit this mapping at
  export unless mapped separately or excluded").
- Add a per-row **"stop inheritance here"** marker (new boolean/status on
  `MapRow`, or a new relationship-adjacent status), distinct from "not yet
  mapped": it tells the export transform that this concept's subtree should
  *not* inherit any ancestor's mapping, without itself asserting a local
  code. This is the one new authoring concept beyond what exists today —
  without it, unmapped-but-divergent branches would silently inherit an
  incorrect ancestor mapping.
- Add a live coverage/gap view: using the map's existing ECL scope, compute
  what fraction of in-scope SNOMED concepts would be covered (directly or
  by inheritance) if exported now, highlighting uncovered branches.

### Phase 3 — Export-time descendant expansion
- New export mode/option: "expand descendants" (only offered for
  `TARGET_TO_LOCAL` maps).
- Algorithm, run against the map's `toVersion`/`toScope`:
  1. Compute the ECL-scoped concept set (reuse existing scope validation
     path in `FhirService`).
  2. For each concept in scope, find the nearest ancestor (including
     itself) that has an authored, non-"stop-inheritance" `MapRowTarget`.
  3. **Polyhierarchy conflicts**: where a concept has multiple mapped
     ancestors via different parents with different local codes, do not
     silently pick one — apply a deterministic tie-break (e.g. shortest
     path) but flag the concept in the export report as ambiguous/
     reviewed-by-rule rather than author-asserted.
  4. Respect "stop inheritance here" markers: a concept under such a
     marker, with no closer authored mapping, is excluded/marked
     unmappable in the dense export, not left to inherit from further up.
  5. Emit each row tagged **asserted** (author-chosen) or **inferred**
     (descendant-inherited), so downstream consumers can judge reliability
     per row.
- Output formats: keep existing export formats, add the asserted/inferred
  tag as a column/attribute; no change needed to non-expanded export mode.

### Phase 4 — Validation & testing
- Unit tests for the nearest-ancestor resolution algorithm, including:
  multiple inheritance/conflict cases, "stop inheritance" boundaries,
  concepts with no mapped ancestor at all (must be marked unmappable, not
  silently dropped), and version-over-version stability (expansion must be
  re-run against a specific `toVersion`, not assumed stable across SNOMED
  CT releases).
- Regression tests confirming `LOCAL_TO_TARGET` maps are entirely
  unaffected (no behavior change, no schema migration impact beyond the
  new nullable/defaulted direction field).
- Manual review workflow: author should be able to preview the expanded
  export before committing to it, to catch over-generalization before
  publishing.

## Open questions

- Where does the initial "working set" of local codes to drive AutoMap
  come from for this map type (since there's no full enumerable list of
  SNOMED concepts the way there is for local codes today)? Likely answer:
  authors still start from the finite list of local codes, AutoMap each to
  its best SNOMED match exactly as today — the SNOMED side only grows to
  cover "representative" concepts, not an enumerated target list.
- Tie-break rule for polyhierarchy conflicts: shortest path vs explicit
  author priority vs always-flag-for-review. Needs a decision before
  Phase 3 implementation.
- Should "inferred" rows be re-computed live in the UI (coverage view) using
  the exact same code path as export, to guarantee consistency between
  preview and final export? (Recommended: yes, single shared implementation.)
