# National language metadata

`national-language-metadata.json` lists, per SNOMED CT edition, the named language contexts offered
as a map's display language. Each context is an ordered sequence of language dialects in Snowstorm
format (`<lang>-X-<language refset id>`); the terminology server returns the preferred term of the
first dialect that has one for the concept.

## Source

Copied on 2026-11-23 from `sct-implementation-demonstrator` (`src/assets/language/national-language-metadata.json`),
which is ahead of the central copy in
[IHTSDO/snomedct-language-metadata](https://github.com/IHTSDO/snomedct-language-metadata) (the central one has
no Argentina or Uruguay editions). Syncing from a single central source is still to be decided.

## Local changes (keep them when syncing)

- International Edition `moduleUri` changed from `http://snomed.info/sct/11000234105` (not a SNOMED CT
  concept) to `http://snomed.info/sct/900000000000207008`, the URI terminology servers publish.
- Whitespace removed from the Uruguay `languageDialects` sequence.
