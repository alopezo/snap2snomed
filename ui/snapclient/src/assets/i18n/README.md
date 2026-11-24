# User interface translations

One [ngx-translate](https://github.com/ngx-translate/core) file per interface language. `en.json` is the
source and the fallback: a key missing from another language shows the English text.

Not to be confused with a map's *display language* (`src/assets/language/`), which selects the SNOMED CT
terms shown for concepts.

| Code | Language   | Status                                                         |
|------|------------|----------------------------------------------------------------|
| en   | English    | Source                                                         |
| es   | Español    | Machine-assisted, needs review by a native speaker             |
| fr   | Français   | Machine-assisted, needs review by a native speaker             |
| da   | Dansk      | Machine-assisted, needs review by a native speaker             |
| nl   | Nederlands | Earlier human translation, completed machine-assisted          |
| hu   | Magyar     | Earlier human translation, completed machine-assisted          |
| sv   | Svenska    | Machine-assisted, needs review by a native speaker             |

## How the language is chosen

`UiLanguageService` (`src/app/_services/ui-language.service.ts`) uses the language picked in the footer
selector (stored in the browser), else the browser language, else `defaultLang` from the app config,
else English.

## Adding or changing texts

- Add the key to `en.json` first, then to every other file. `src/app/i18n-files.spec.ts` fails when a
  file is missing a key, has an extra key, an empty text, or different `{{parameters}}` than English.
- Keep `{{parameters}}` and HTML markup (the `TERMS` section) unchanged in translations.
- In development builds, a key missing from English is logged as `Missing translation key: ...`.

## Adding a language

1. Add `<code>.json` with every key of `en.json`.
2. Add the language to `UI_LANGUAGES` and register its Angular locale data in `ui-language.service.ts`.
3. Import the file in `src/app/i18n-files.spec.ts`.
