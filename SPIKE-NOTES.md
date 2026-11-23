# Spike notes – local run, devauth, Snowstorm Lite (2026-11-19)

Status: **spike stopped.** Changes committed on `main` in the fork `alopezo/snap2snomed` (`origin`);
we work directly on `main`, no feature branches. `upstream` is `IHTSDO/snap2snomed` with push disabled; PRs to upstream only by explicit decision.
The shrimp stub (`ui/snapclient/dev-stubs/`) is still uncommitted (undecided).

## Uncommitted changes

| Change | Files | Status |
|---|---|---|
| `devauth` profile: fake Cognito-compatible IdP inside the API (local dev only) | `api/.../security/DevAuthConfiguration.java`, `api/.../security/DevAuthController.java`, `api/src/main/resources/application-devauth.properties`, `@Profile("!devauth")` on `JwtDecoderConfigurer.java`, README section | Working, tested with curl + browser |
| Snowstorm Lite fuzzy fallback: if `fhirBaseUrl` contains `lite`, search exact first and retry with `~` suffix only on 0 results | `ui/snapclient/src/app/_services/fhir.service.ts` (`isLite`, `findConcepts`) | Working, tested in browser (search, automap) |
| Stub of private `@csiro/shrimp-hierarchy-view` (hand-written Ivy defs, shows a placeholder) | `ui/snapclient/dev-stubs/shrimp-hierarchy-view/` (untracked), installed into `node_modules` only | Working. **Undecided:** commit with an install script, or `.gitignore` |

Unit tests (API Testcontainers suite, UI Karma) were **not** run.

## How to resume

Prereqs: Docker running, JDK 21+ (built fine on 24), Node 20.19+, yarn 1 (`npx yarn@1.22.22` works without installing).

```sh
# DB (data kept in the mysql_db volume)
cd api && docker compose up -d db

# API with fake auth + Snowstorm Lite (omit the last arg to use Ontoserver)
mvn -pl api spring-boot:run -Dspring-boot.run.profiles=local,devauth \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:aws-wrapper:mysql://localhost:3306/s2s --spring.datasource.username=root --snap2snomed.defaultTerminologyServer.url=https://implementation-demo.snomedtools.org/snowstorm-lite/fhir"

# UI (.env already has API_BASE_URL=http://localhost:8080). --ssl=false: the dev cert is self-signed
cd ui/snapclient && npx yarn@1.22.22 start --ssl=false
```

If `yarn install` is ever re-run it fails on the private package (401 from the CSIRO Azure DevOps feed). Workaround used:
temporarily point the dependency to `file:./dev-stubs/shrimp-hierarchy-view`, install, then `git checkout -- package.json yarn.lock`.

Gotchas: devauth signing key is in memory → log in again after every API restart (if a page is blank, go Home and log in).

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
