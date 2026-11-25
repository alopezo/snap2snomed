# SNOMED CT → local code system maps: how it was implemented

Status: implemented on branch `from-snomed` of the fork `alopezo/snap2snomed`; not released or merged.
Companion to [plan.md](plan.md), the design draft this work started from. This document describes what was
built, how it differs from the plan, and what is still open.

## Summary

A map can now go **from SNOMED CT** to a local code system. Its source is a set of SNOMED CT concepts, given
as an ECL expression or as a list of codes. Its targets are the codes of an imported code set. Authors map a
small number of representative concepts. With **inheritance** switched on, every other concept takes the
local code of its nearest mapped ancestor. This is computed live while authoring and generated at export
time, and never stored.

The plan reached the same goal with another constraint: keep authoring local → SNOMED CT exactly as it is,
and derive the reverse lookup at export. We chose a map type of its own instead. The reverse direction is
then an ordinary map, so inheritance can be seen, reviewed and accepted during authoring, not only when
exporting. An existing map to SNOMED CT can still be reused: it can *seed* a new map from SNOMED CT (see
[Seeding from a map to SNOMED CT](#seeding-from-a-map-to-snomed-ct)).

## Requirements from the plan and how they are met

| Requirement (from the plan) | How it is met |
|---|---|
| Represent how SNOMED CT concepts correspond to a smaller local code system, for exporting a lookup table | `FROM_SNOMED` maps, exported as TSV/CSV/XLSX or as a FHIR ConceptMap |
| A dense result without authoring every concept | Inheritance from the nearest mapped ancestor, within the source or beyond it |
| Only intensional rows are maintained; generated rows are not stored | Inherited targets are computed; generated concepts exist only in exports and previews |
| Reuse the authoring loop | Same tasks, statuses, review, bulk change, relationships and notes. AutoMap works through its own matcher (see [AutoMap](#automap)) |
| Existing local → SNOMED CT maps unaffected | `direction` defaults to `TO_SNOMED`; every new behaviour is conditional on `FROM_SNOMED` |
| Clear roles (which side is SNOMED CT) | "Type" column in the map list, source and target labels by direction, direction chosen in the map form |
| Correct relationship semantics | SNOMED CT is the real source, so nothing is inverted for display. Inherited targets adjust the relationship |
| Stop inheritance for a divergent subtree, distinct from "not mapped" | Per-row "stop inheritance" switch (`map_row.stop_inheritance`) |
| Authoring feedback: descendants affected, coverage and gaps | Inheritance column and popover, row detail ("inherits from" / "passes on to N"), coverage summary with lists |
| Polyhierarchy conflicts never resolved silently | Stricter than the plan: no tie-break at all. The concept is a `CONFLICT` and the author decides |
| Concepts with no mapped ancestor are not dropped | `UNCOVERED`, listed and exported |
| Asserted vs inferred tagged per row in exports | "Mapping origin" and "Inherited from" columns: `ASSERTED`, `INHERITED`, `GENERATED`, `CONFLICT`, `BLOCKED`, `NO_MAP`, `UNCOVERED`, `STOPPED` |
| Tied to a SNOMED CT version | Everything is per map version. A new version re-expands the source ECL and reports kept, added and removed concepts |
| Preview before publishing | Paged preview of the generated concepts, filterable by state |
| Live view and export consistent | One implementation, `InheritanceCalculator`, used by both |
| Where the initial working set comes from (open question) | An ECL expression, a TSV of SNOMED CT codes, or seeding from an existing map to SNOMED CT |

## Data model

The model was extended rather than redesigned.

- **`map`**:
  - `direction`: `TO_SNOMED` (default) or `FROM_SNOMED`.
  - `target_code_set_id`: the imported code set the targets come from.
  - `source_ecl`: the ECL the source was expanded from, or null for a list of codes.
  - `inheritance`: a boolean.
  - `inheritance_mode`: `WITHIN_SOURCE` (default) or `BEYOND_SOURCE`.
  - For maps from SNOMED CT, `to_version` and `display_language` describe the source side. `to_scope` holds
    the source ECL, or `*` for a list of codes.
- **`map_row`**:
  - `stop_inheritance`.
  - `accepted_inheritance`: a fingerprint of what the row inherited when the author accepted it, e.g.
    `INHERITED from 195967001: J45.9`.
- **`map_inheritance_descendants`**: a lazily filled cache of the descendants of each anchor concept, per
  map. Rows are `code<TAB>term` lines, or `!TOO_LARGE n` when there are too many. The key `!TOP_LEVEL` holds
  the top-level concepts of the source. A map version, source and SNOMED CT version never change, so the
  cache is never stale. It is cleared when the inheritance mode changes, and deleted with the map.
- **Migrations** V23 (direction and target code set) to V27 (inheritance mode).

## Creating a map from SNOMED CT

In the map form, choose the direction "From SNOMED CT" and then the source:

- **ECL.** The ECL is expanded in the chosen SNOMED CT version and display language and frozen into a new
  imported code set (`POST /importedCodeSets/fromEcl`). There is a cap of 10,000 concepts, and the form
  offers a "count concepts" check.
- **A list of SNOMED CT codes.** An imported code set (TSV) whose codes are checked on creation: they must be
  active in the version, and there are at most 10,000.

The target is any imported code set. Inheritance and its mode can be set on creation and changed later.

A new version of a map with an ECL source expands the ECL again in the new version. A dialog then lists the
concepts that kept their rows, the new ones (unmapped) and the ones removed. The ECL and the display language
are kept.

The following features are not available for maps from SNOMED CT: mapping file import, target validation,
target term refresh, target refinement (laterality and severity), and setting targets in bulk change.

## Inheritance

### Rules (`InheritanceCalculator`)

A row without targets takes the targets of its **nearest mapped ancestor** among the map's rows. Ancestry
follows the real SNOMED CT hierarchy of the map's version. A row's state is one of:

- `ASSERTED`: it has targets of its own.
- `INHERITED`: it takes its nearest mapped ancestor's targets.
- `NO_MAP`: it is marked "no map", which also stops inheritance for its subtree.
- `STOPPED`: it has no targets and is marked "stop inheritance".
- `BLOCKED`: its nearest anchor is a no-map or stopped row.
- `CONFLICT`: its nearest mapped ancestors (polyhierarchy) have different targets. Nothing is chosen.
- `UNCOVERED`: no mapped ancestor among the rows.

The relationship of an inherited target is adjusted, because the descendant is narrower than the mapped
concept: `EQUIVALENT` becomes `BROADER`, and `NARROWER` becomes `INEXACT`.

### Within or beyond the source

Inheritance always applies to every descendant. The mode only says which concepts take part:

- **Within the source**: only the source's concepts inherit from each other, and nothing is generated.
  Descendants come from `(< concept) AND (source ECL)`. For a list of codes they come from `< concept`,
  filtered to the rows.
- **Beyond the source**: the descendants that are not rows are also generated at export (`< concept`). This
  is the plan's "dense export".

There are two limits, both set by `snap2snomed.inheritance.max-generated` (default 50,000):

- A concept with more descendants than the limit is cached as too large, and generation is refused for it.
- If all the generated concepts together exceed the limit, generation is refused as well.

### Authoring

- **Coverage summary** in the map details: mapped, inherited, uncovered, in conflict, blocked, accepted and
  pending. The counts that need attention open a list of the rows.
- **Inheritance column** in the map view, the task view and the mapping table. It shows a short summary
  ("✓ ↓125", "↳ 195967001") and opens a popover with the details.
- **Mapping detail**: what the concept inherits or passes on, the stop switch, and "accept inheritance".
- **Accepting inheritance.** A row without targets of its own (inheriting, stopped or blocked) becomes MAPPED
  with no targets, and so goes through the workflow. This can be done one row at a time or in bulk ("Accept
  the inheritance of the selected rows"). What it inherited is recorded. If that changes later, the row is
  flagged "inheritance changed since accepted" and can be accepted again. Exports are unchanged: an accepted
  row still exports as inherited.
- **Top level only.** A switch in the map and task views shows only the concepts with no ancestor in the
  source, as a skim view of the source list. It takes one ECL expansion, `(S) MINUS (< (S))`. For a list of
  codes, S is the codes joined by `OR`, sent inline in a POST `$expand`. The result is cached per map.
- **Type column** in the map list: the direction, plus an "Inheritance" badge whose tooltip shows the mode.

### Export

The export dialog has an option to include inherited targets (on by default for maps with inheritance).
Tabular formats add "Mapping origin" and "Inherited from" columns. In FHIR, inherited and generated
elements carry a comment instead. Refined targets do not apply to maps from SNOMED CT.

## AutoMap

AutoMap in an author task of a map from SNOMED CT runs on the server (`POST /task/{id}/automapFromSnomed`).
For each unmapped row, `TermMatcher` matches the concept's term (in the map's display language) against the
displays of the target code set:

- Case, accents and punctuation are normalised, and the FSN semantic tag is removed.
- Tokens are weighted by how rare they are in the code set. Typos and plurals are tolerated through
  character trigrams.
- Negations ("without", "sin", "sans", "uden", …) must agree, and "nonallergic" is never a variant of
  "allergic".
- The score is the harmonic mean of how much of the term the display covers and how much of the display the
  term covers. A match needs at least 0.75. Below that, the row stays unmapped.

A match becomes an `INEXACT` target and the row becomes `DRAFT`, as AutoMap does for maps to SNOMED CT.

In a map with inheritance, a match is left out when the concept would already inherit exactly that code
(counting the other matches of the same run). Otherwise its own target would add nothing and would stop it
passing on its ancestor's mapping. Rows marked "stop inheritance" are left alone. The dialog reports how
many rows got a target and how many matches were left out for that reason.

## Seeding from a map to SNOMED CT

In the map list, a button on single-author maps to SNOMED CT (project owners only) creates a map from
SNOMED CT **in a new project** (`POST /map/{id}/invert`):

- Its source is the SNOMED CT concepts the map targets, with their terms in the map's display language.
- Each concept is mapped back to the local codes that targeted it, with the relationship inverted:
  `BROADER` ↔ `NARROWER`, while `EQUIVALENT` and `INEXACT` stay the same.
- A refined target (a laterality or severity expression) is mapped from its focus concept, with the
  relationship adjusted first.
- No-map rows and concepts that are not active in the version are left out and reported.
- Rows start as `DRAFT`. The original map does not change.

This is a one-off starting point, not a live inversion. The new map is reviewed and maintained like any
other map from SNOMED CT.

## API

| Method and path | Purpose |
|---|---|
| `POST /importedCodeSets/fromEcl` | Expand an ECL into a source code set |
| `GET /map/{id}/targetCodes` | Search the target code set (by code or text) |
| `GET /map/{id}/sourceChanges?previousMapId=` | Source differences between versions |
| `GET /map/{id}/inheritance` | Coverage and the inheritance of every row |
| `GET /map/{id}/inheritance/generated` | Page of generated concepts, optionally of one state |
| `POST /map/{id}/inheritance/accept` | Accept the inheritance of rows (given ones, or all) |
| `POST /task/{id}/automapFromSnomed` | AutoMap for a task of a map from SNOMED CT |
| `POST /map/{id}/invert` | Seed a map from SNOMED CT from a map to SNOMED CT |
| `GET /mapView/{id}?topLevel=true` | (and the task view) Only top-level concepts of the source |
| `GET /mapView/{id}` (CSV/TSV) `?inherited=` | Export with or without inherited targets |

## Terminology server

Two kinds of request are made to the terminology server:

- **ECL expansion with `$expand` by GET.** It is used for the source ECL, for descendants, and for the
  top-level concepts of an ECL source.
- **`$expand` by POST, with the value set given inline** (an ECL `constraint` filter). It is needed when the
  ECL is too long for a URL: the top-level concepts of a list of codes, and the concepts of a seeded map.

Everything was tested against **SnowstormX** (`implementation-demo.snomedtools.org/fhir`). For example, a
POST with an ECL of 4,253 codes joined by `OR` took about 10 seconds. Ontoserver and Snowstorm Lite were not
tested with these features.

## Tests

- **API unit tests**: `InheritanceCalculatorTest` (states, conflicts, stop, generated, "would inherit without
  its own target"), `InheritanceExportTest`, `InheritanceServiceTest`, `EclCodeSetServiceTest`,
  `TermMatcherTest` (accents, word order, typos, negations, narrower terms), and `MapInversionServiceTest`.
- **UI**: component tests for the new dialogs and the inheritance components (319 tests in total).
- The integration test suite (Testcontainers) was **not** run, and there is no dedicated regression test for
  maps to SNOMED CT.

## Known limitations and open items

- **Dual author mode** is not supported for inheritance or for seeding.
- **Limits**: 10,000 source concepts, and 50,000 descendants per concept or generated in total. A dense lookup
  over very broad hierarchies may need higher limits.
- **AutoMap quality** depends on the local code set's language and wording. The 0.75 threshold was tuned on a
  small test code set only and should be checked against a real one.
- **AutoMap with inheritance** may cost one ECL expansion per new anchor on the first run. These are cached
  afterwards.
- **Terminology servers** other than SnowstormX are untested.
- **Integration tests** should be run, and a regression test for maps to SNOMED CT added, before merging.

## Running it locally

See "Running locally without AWS Cognito" in the [README](../../README.md). It covers MySQL in Docker, the
`devauth` profile (a fake Cognito inside the API) and the UI. For these features, point the API at SnowstormX
with `--snap2snomed.defaultTerminologyServer.url=https://implementation-demo.snomedtools.org/fhir`. The
migrations (V23–V27) run on startup.
