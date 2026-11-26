# Change Log

Notable changes in each release are documented below.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

* **Security**   - in case of vulnerabilities.
* **Changed**    - for changes in existing functionality.
* **Deprecated** - for soon-to-be removed features.
* **Removed**    - for now removed features.
* **Fixed**      - for any bug fixes.

---

⚠️ **Note**: Changes prior to version Frontend 1.0.2 are incomplete or missing. This changelog was started from version 1.2.0 onward.

---

## [Unreleased]

Maps from SNOMED CT (branch `from-snomed`).

#### Added

- Map direction, chosen when a map is created: "To SNOMED CT" (as before) or "From SNOMED CT". A map
  from SNOMED CT takes as its source the active concepts of an ECL expression in a SNOMED CT version
  (at most 10,000; the form can count them first), with terms in the chosen display language, expanded
  into a code set when the map is created. Its targets are chosen from an imported code set.
- A new version of a map from SNOMED CT expands the same ECL and display language again in the chosen
  SNOMED CT version. Concepts still in the result keep their rows and targets, new ones are added
  unmapped, and a dialog then shows how many were kept and added and lists the concepts that are gone
  (with a button to copy the list).
- In the mapping screens of a map from SNOMED CT, a search over the target code set (by code or text)
  and the selected code's details with its additional columns replace the SNOMED CT search,
  hierarchy, properties and refinement panels. Codes are dragged onto a relationship as usual.
- The FHIR ConceptMap export of a map from SNOMED CT has SNOMED CT as the source and the target code
  set's system and version as the target. CSV, TSV and Excel exports are unchanged.
- Inheritance for single author maps from SNOMED CT, chosen when the map is created and changeable when
  editing it: unmapped concepts take the targets of their nearest mapped ancestor (following the real
  SNOMED CT hierarchy), with the relationship adjusted (equivalent becomes broader, narrower becomes
  inexact). A row marked "no map" or "stop inheritance" stops it for its subtree; when the nearest
  mapped ancestors disagree the concept is a conflict and nothing is chosen. The map details show the
  coverage (mapped, inherited, uncovered, in conflict, blocked) with lists to review, the tables show
  what each unmapped concept inherits, and the mapping detail shows what a concept inherits or passes
  on, with the stop switch. Exports include inherited targets with "Mapping origin" and "Inherited
  from" columns (or a FHIR target comment), unless switched off in the export dialog.
- The source of a map from SNOMED CT can also be an imported list of SNOMED CT codes (checked against
  the chosen version when the map is created). Inheritance always applies to every descendant, in one
  of two modes (editable): within the source, where only the source's concepts inherit from each other,
  or beyond the source, where the descendants of mapped or stopping concepts that are not rows of the
  map are also generated at export time ("GENERATED" lines, or FHIR elements with a comment), never
  stored; the map details offer a paged preview of them, with filters by state. Generation is refused
  when a concept, or all together, exceed 50,000 concepts (`snap2snomed.inheritance.max-generated`).
- Maps from SNOMED CT have a "Top level only" switch in the map view and the task view: a skim view of the
  source list with only the concepts that have no ancestor in the source. It is computed once per map
  version with one ECL expansion, `(S) MINUS (< (S))`, where S is the source ECL or the source codes joined
  by OR (sent inline in a POST $expand, as it can be thousands of codes), and cached with the map.
- In maps from SNOMED CT, a small button in the "Source display" header sorts the rows by the length of the
  source term: shortest (usually the most general concepts) first, then longest first, then unsorted.
  Clicking the header itself still sorts alphabetically. API sort key `sourceDisplayLength` (ties in
  alphabetical order).
- A single author map to SNOMED CT can seed a map from SNOMED CT (a swap button in the map list, project
  owners only): a new project whose source is the SNOMED CT concepts the map targets, each mapped back to the
  local codes that targeted it with the relationship inverted. Refined targets are mapped from their focus
  concept (the relationship adjusted first); no-map rows and concepts not active in the version are left out.
  The rows start as DRAFT, the original map does not change, and the dialog reports what was carried over.
- Automap works for the author tasks of maps from SNOMED CT: the term of each unmapped concept is matched
  on the server against the displays of the target code set (tokens normalised for case, accents and
  punctuation, weighted by rarity, typos tolerated through trigrams; negations such as "without" or "sin"
  must agree, and "nonallergic" is never a variant of "allergic"; a match needs a score of at least 0.75)
  and becomes an INEXACT target, its row DRAFT. In a map with inheritance a match is left out when
  the concept already inherits exactly that code, counting the other matches of the run, so automap does
  not break inheritance; rows marked "stop inheritance" are left alone.
- Authors accept the inheritance of rows without targets of their own (inheriting, stopped or blocked),
  from the mapping detail or in bulk (bulk change, "Accept the inheritance of the selected rows"): they
  become MAPPED and go on through the workflow. What a row inherited when accepted is recorded; if it
  changes later the row is flagged ("inheritance changed since accepted") and can be accepted again.
  The coverage shows accepted rows, rows pending acceptance and changed ones (migration V26,
  `POST /map/{mapId}/inheritance/accept`).
- The mapping detail header is more compact: previous and next sit on either side of the status switch.
- The map list has a "Type" column showing each map's direction and whether it uses inheritance.
- An "Inheritance" column in the mapping tables summarises each row (mapped and how many inherit it,
  inherits from, stopped, in conflict, uncovered) and opens a popover with the details.
- Inheritance is recomputed after every change; the descendants of each mapped or stopping concept
  within the map's ECL are expanded once and cached in the database (migration V24).
- API: `POST /importedCodeSets/fromEcl`, `GET /map/{mapId}/targetCodes`,
  `GET /map/{mapId}/sourceChanges`, `GET /map/{mapId}/inheritance` and
  `GET /map/{mapId}/inheritance/generated` (authorised through the map's project roles), the export
  parameter `inherited`, the map field `sourceEcl` and the map row field `stopInheritance` (migration V25) and the map fields `direction` and `targetCodeSet` (migration V23).

#### Changed

- The code set list also shows the code sets the user uploaded, so one can be picked as a target code
  set before any map uses it.

#### Not available for maps from SNOMED CT

- Mapping file import, target validation, target term refresh, target refinement, changing
  the ECL or display language in a new version, and setting targets in bulk change (relationship and
  status can still be changed).

#### Fixed

- The filter chips of the mapping detail showed "[object Object]" for code or term filters (the values
  read back from the URL are lists); typed text is now shown as is instead of going through translation.
- Two requests computing the same inheritance cache entry at once made one of them fail (its transaction
  was left marked for rollback); the duplicate insert is now ignored.
- The International Edition logo beside a SNOMED CT version was loaded from browser.ihtsdotools.org, which
  now denies it, so it showed as a broken image; the SNOMED International icon is now bundled with the app.

## [1.3.0] - 2026-11-25

Map targets can be refined with laterality and severity, and the export asks how to write them.

#### Added

- "Refine target" card in the mapping detail, below the target properties. For the selected
  concept it offers laterality (left, right, both sides) for clinical findings and procedures
  whose finding or procedure site is in the lateralizable body structure reference set, and
  severity (mild, moderate, severe) for clinical findings. The refined target is dragged onto a
  relationship like any other target.
- A refined target is stored as a close-to-user SNOMED CT expression without terms, e.g.
  `71620000:272741003=7771000,246112005=24484000`, always in the same canonical form (laterality
  before severity). Its display is the focus term followed by the value terms, e.g. "Fracture of
  femur - Left - Severe", in the map's display language, and REFRESH TERMS rebuilds it.
- Export dialog (replaces the export menu): format, an option to include notes, authors and
  reviewers in any tabular format, and, when the map has refined targets, how to write them: the
  expression, only the focus concept, or both (extra "Focus concept" columns). With only the focus
  concept, an equivalent relationship is exported as broader and a narrower one as inexact, and
  refined targets of the same source sharing the focus concept are exported once. The last choice
  is remembered in the browser.
- API: `targetForm` parameter (`expression`, `focus`, `both`) on the map export, and
  `GET /mapView/{mapId}/$refinedTargetCount`.
- Mapping import accepts refined targets (in canonical form or with terms and spaces) in the
  target code column.

#### Changed

- Target codes can be up to 255 characters (database migration V22).
- Target validation checks the focus concept of refined targets; clicking a refined target shows
  its focus concept.

## [1.2.0] - 2026-11-24

The user interface is available in seven languages.

#### Added

- Interface language selector in the footer (also on the login page): English, Español, Français,
  Dansk, Nederlands, Magyar and Svenska. The choice applies immediately, is kept in the browser
  (also across logout and login) and otherwise follows the browser language, then `defaultLang`.
- New Spanish, Danish and Swedish translations; the French translation is now complete and the
  Dutch and Hungarian ones are updated to cover the whole interface. The new texts are
  machine-assisted and still need review by native speakers (see `src/assets/i18n/README.md`).
- Dates, the "last updated" times, the target version date and the table paginator follow the
  interface language.
- A test that checks every translation file has the same keys and `{{parameters}}` as English, and a
  development-mode console warning for keys missing from English.

#### Changed

- Remaining hard-coded interface texts (login page, terms of service dialog, table headers and
  filters, tooltips, accessibility labels, error messages) now come from the translation files.
- The map view action buttons wrap onto a second line instead of overlapping when labels are long.

#### Fixed

- Error messages for failed concept lookup, concept search and match suggestions showed their
  translation keys instead of text.
- The SHOW / HIDE column menu showed a translation key for the notes column.
- The map view showed the raw `INREVIEW` status instead of "IN REVIEW".
- Typos in English messages ("occured", missing and doubled spaces).

## [1.1.0] - 2026-11-23

First release of this fork. It starts adapting Snap2SNOMED to terminology servers other than
Ontoserver (Snowstorm, Snowstorm Lite) and adds explicit display language support. From this
release on, the API and the UI share one version number.

#### Added

- Per-map display language: a language or dialect sequence (e.g. `es-X-450828004,en`) used for
  target terms. Each target stores the preferred term of the first dialect that has one. A map
  without a display language keeps the previous behavior (English FSN).
- Display language picker in the create, copy, new version and edit map dialogs. It offers the
  language contexts of the selected edition (from `national-language-metadata.json`, copied from
  the SNOMED implementation demonstrator) or a custom sequence. A test dialog shows the term each
  sequence would store for a concept.
- Target terms are re-resolved in the map's display language on mapping import, new map version,
  and when the display language or target version changes. The export uses these terms and sets
  `ConceptMap.language`.
- Concept search, autosuggest, hierarchy and properties use the map's display language.
- REFRESH TERMS button in the map view (owners and admins), backed by
  `POST /map/{mapId}/refreshTargetDisplays`, to update stored target terms from the terminology
  server.
- Terminology server detection (Snowstorm, Snowstorm Lite, Ontoserver) from the
  CapabilityStatement, used to send dialect sequences in each server's format.
- `devauth` Spring profile: a local, Cognito-compatible identity provider so the app runs
  without AWS Cognito. For local development only; never enable it in a deployed environment.

#### Changed

- Snowstorm Lite searches use an exact match first and retry with fuzzy matching only when the
  exact search returns nothing.
- Concept search waits for at least 3 characters (with a 300 ms debounce) before querying the
  terminology server.
- The map dialog only shows errors from loading target versions, not from other terminology
  requests.

#### Fixed

- Target validation failed on Snowstorm ("/ by zero") because the expansion total was probed
  with `count=0`.

#### Database

- Migration `V21__map_display_language` adds the nullable column `map.display_language` (and
  its audit column). Existing maps keep a null value.

## [Frontend 1.0.2] - 2025-08-06

#### Fixed

- Added Edition Labels for Jamacian, LOINC Ontology, IPS Terminology Module


## [Frontend 1.0.1]

### Security

- Updated library versions to mitigate CVEs:

#### Changed

- SNOMED-457: streamline login process (DEX)
- SNOMED-499: expand export to include additional source columns
- SNOMED-500: export notes, author and reviewer

#### Fixed

- SNOMED-496: Hiding index column causes filters to be misaligned
- SNOMED-475: Import error never goes away
- SNOMED-470: pre-filled fields in "create map" dialog when they should be empty
- SNOMED-408: Member list in the edit/create map dialogue is only returning X number of users
- SNOMED-489: Select all toggle only selects items on page

## [Frontend 1.0.0]
