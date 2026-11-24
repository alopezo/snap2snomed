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
