# Spike notes – local run, devauth, Snowstorm Lite (2026-11-19)

Status: **done.** The spike's changes are committed in the fork `alopezo/snap2snomed`:
- the `devauth` fake identity provider and the Snowstorm Lite fuzzy-search fallback, on `main`;
- the stub of the private shrimp package (`ui/snapclient/dev-stubs/`), on the `from-snomed` branch.

**How to run it locally now lives in the [README](README.md), under "Running locally without AWS Cognito".**
These notes keep only the findings.

## Findings

### Architecture
- Maven multi-module: `api` (Spring Boot 4.1, Java 21, MySQL/Aurora, Flyway, Spring Data REST, HAPI FHIR client) and `ui` (Angular 21, NgRx). Terraform deploys to AWS: Cognito, ECS Fargate, Aurora MySQL, S3 + CloudFront.
- Auth: UI redirects to the Cognito hosted UI; API is an OAuth2 resource server. Users are created via `PUT /users/token/{id}` with the id token.

### Terminology server
- One server per instance: `snap2snomed.defaultTerminologyServer.url` (default CSIRO public Ontoserver `tx.ontoserver.csiro.au/fhir`; Terraform `fhir_url`).
- The **browser calls FHIR directly** (no token sent): `$expand` (search, hierarchy, ECL validation), `$lookup`, `$validate-code`, `ConceptMap/$translate` (historical associations, automap). Requires CORS on the server.
- The API only uses it for "Validate target codes" (`FhirService.validateValueSetComposition`).
- Automap uses Ontoserver-only features (`codesystem-terms` + `automapstrategy-*`), detected by URL containing `ontoserver`; otherwise it falls back to plain text search.
- `proxyCors` in `token-interceptor.service.ts` is dead code (never assigned). `findOutliers` is unused (and uses `{{C active=true}}`, unsupported by Lite).

### Snowstorm Lite (implementation-demo.snomedtools.org/snowstorm-lite/fhir)
- CORS `*`, single edition (International 20260101); silently answers with it when another version is requested.
- `~` suffix makes the whole filter fuzzy. Always-fuzzy ranks exact matches lower (e.g. `Hypertension~` → "Low blood pressure"), hence the exact-then-fuzzy fallback.
- No `parent` property in `$expand` (UI's non-Ontoserver `$lookup` path covers the tree); no ECL concept filters.
- Search, validate, tree and automap verified working.

### `@csiro/shrimp-hierarchy-view`
- Only used for the alternate "shrimp" hierarchy view (toggle `account_tree` icon in mapping work/detail; default is the project's own tree). Options later: remove the view, or keep the stub as an in-repo lib.

### AWS sizing (from Terraform)
- Current: Fargate 4 vCPU / 8 GB (1 task), Aurora MySQL db.t3.medium (single instance, 1-day backups), 2 NAT gateways, ALB, S3+CloudFront ≈ USD 300–330/month (us-east-1 list prices, approximate).
- No `-Xmx`/`MaxRAMPercentage` → JVM heap ≈ 25% of 8 GB ≈ 2 GB. Imports stream; xlsx export uses SXSSF but loads all `MapView` rows into a list (only memory hotspot).
- Proposal: API 1–2 vCPU / 3–4 GB with `-XX:MaxRAMPercentage=75`, Aurora db.t4g.medium, 1 NAT for non-prod, 7-day backups for prod. Validate with CloudWatch (ECS MemoryUtilization, Aurora CPU/credits) before changing.
