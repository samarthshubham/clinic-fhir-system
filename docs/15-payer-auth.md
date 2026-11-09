# payer-api authentication and scopes

How payer-api decides who is calling and what they may do. The decision is [ADR 0006](adr/0006-payer-auth.md); the JWT and RBAC parts copy clinic-api ([10-auth.md](10-auth.md)), so this page covers what is different.

## 1. Three kinds of callers

| Caller | Gets a token from | Token carries | Used on |
|---|---|---|---|
| Payer staff (payer-app) | `POST /api/auth/login`, `POST /api/auth/refresh` | `roles` (`UM_REVIEWER`, `PAYER_ADMIN`) | `/api/**` JSON endpoints |
| Systems (clinic-api, another payer) | `POST /oauth/token`, `grant_type=client_credentials` | `scope` with SMART v2 system scopes, `client_id` | `/fhir/**` interop |
| Members (through clinic-api) | `TokenService.issueMemberToken`; the exchange that hands it to clinic-api is E6-S3 | `scope=patient/*.r`, `patient` claim | `/fhir/**` interop, own compartment only |

All three are HS256 JWTs signed with `payer.auth.jwt-secret` (env `PAYER_JWT_SECRET`, at least 32 bytes), issuer `payer-api`, `token_type=access`. Staff refresh tokens have `token_type=refresh` and are rejected as bearer tokens, as in clinic-api.

## 2. Staff: roles → permissions

Same pattern as clinic-api: `AuthProperties.users` holds the BCrypt hash and role, `RolePermissions` maps roles to permissions on every request, and endpoints will check a permission with `@PreAuthorize`.

| Permission | `UM_REVIEWER` | `PAYER_ADMIN` |
|---|---|---|
| `member:read` | ✓ | ✓ |
| `priorauth:read` | ✓ | ✓ |
| `priorauth:decide` | ✓ | |

Only reviewers decide prior auths (roadmap E4). Login returns `{accessToken, refreshToken, tokenType, expiresIn, user}`, the shape `libs/auth` already reads. Errors are `{ "status", "message" }`.

## 3. Systems: `POST /oauth/token`

SMART Backend Services with a client secret instead of a signed JWT assertion (a UDAP-style assertion is a stretch goal in ADR 0006).

```bash
curl -s -u 'clinic-api:dev-only-clinic-api-client-secret' \
  -d grant_type=client_credentials -d 'scope=system/Claim.c system/Questionnaire.r' \
  localhost:9095/oauth/token
```

```json
{"access_token":"eyJ…","token_type":"bearer","expires_in":300,"scope":"system/Claim.c system/Questionnaire.r"}
```

- The client authenticates with HTTP Basic (`client_secret_basic`) or with `client_id` and `client_secret` form fields (`client_secret_post`), not both.
- `scope` is optional. Without it the client gets every scope it is registered for; asking for an unregistered scope fails with `invalid_scope`.
- Tokens live 5 minutes (`payer.auth.client-token-ttl`), as SMART Backend Services recommends. There is no refresh token; the client asks again.
- Responses carry `Cache-Control: no-store` and `Pragma: no-cache`.
- Errors use the OAuth2 shape, not `{status, message}`: `{"error":"invalid_client","error_description":"…"}` with 401 and `WWW-Authenticate: Basic`, or 400 with `invalid_request`, `unsupported_grant_type` or `invalid_scope`.

Clients are registered in `payer.auth.clients` with a BCrypt hash of their secret and their allowed scopes. Startup fails if a registered scope is not a SMART v2 system scope.

## 4. Members: `patient/*.r` and the `patient` claim

A member token has `scope=patient/*.r` and `patient=<payer Patient id>`. ADR 0006 says clinic-api gets it on the patient's behalf; the endpoint that does that exchange arrives with E6-S3 ("My insurance"). Until then `TokenService.issueMemberToken` issues them and the tests use it.

## 5. The interop server: `ScopeInterceptor`

`/fhir/**` has its own Spring Security filter chain. It decodes a bearer token if one is sent (a bad one gets a 401 `OperationOutcome` from `FhirSecurityErrorHandler`), lets every request through to HAPI, and the `ScopeInterceptor` on the `RestfulServer` makes the decision.

`ScopeInterceptor` extends HAPI's `AuthorizationInterceptor` ([ADR 0003](adr/0003-reuse-hapi-features.md): reuse HAPI rather than build), turning the token's scopes into HAPI rules for each request:

| Token | Rules |
|---|---|
| none | `metadata` only; anything else is 401 `OperationOutcome` |
| staff (no `scope`) | `metadata` only, so 403 |
| `system/X.c` | create X |
| `system/X.r` | read and vread X |
| `system/X.s` | search X |
| `system/X.u` / `.d` | update or patch X / delete X |
| `system/*.…` | the same on every type |
| `patient/X.r` + `patient` claim | read X **in the member's Patient compartment** |
| extended operation | only if an `OperationScope` bean declares it, e.g. `new OperationScope("$submit", "Claim", CREATE)` |

- Anything without a rule is denied: 403 with an `OperationOutcome` (NFR-SEC-1).
- Compartment checks are HAPI's. A member reading `Patient/B` or a `Claim` whose patient is B gets 403, including resources inside search results and operation responses (NFR-SEC-2).
- `patient/` scopes count only with a `patient` claim, and `system/` scopes only without one.
- Batch and transaction Bundles get no rules and are denied, because the per-request `r`/`s`/`u` gating can't see the entries inside a Bundle.
- SMART scopes say nothing about operations, so an operation needs an `OperationScope` naming the resource type and permission it requires. Operations without one are denied, so a new operation is closed until its story opens it.
- `GET /fhir/metadata` stays public. Its `rest.security` lists the `SMART-on-FHIR` service and the token URL in the `oauth-uris` extension. The `.well-known/smart-configuration` document comes with E6-S1.

Later stories register their providers as Spring beans (`IResourceProvider`) and declare an `OperationScope` per operation; `RestfulServerConfig` picks both up.

## 6. Configuration and secrets

| What | Base `application.yaml` | `application-dev.yaml` (`./mvnw spring-boot:run` only) |
|---|---|---|
| `payer.auth.jwt-secret` | `${PAYER_JWT_SECRET:}`, so startup fails without it | dev default |
| `payer.auth.users` | none | `reviewer` / `Reviewer#2026` (`UM_REVIEWER`), `payeradmin` / `PayerAdmin#2026` (`PAYER_ADMIN`) |
| `payer.auth.clients` | none | `clinic-api` / `dev-only-clinic-api-client-secret` with `system/Claim.c`, `system/Questionnaire.r`; the hash can be replaced with `PAYER_CLINIC_API_CLIENT_SECRET_HASH` |

`/actuator/health`, `/actuator/info` and `/actuator/prometheus` are public, like clinic-api. Nothing logs tokens, secrets or member data (NFR-SEC-6).

## 7. Where it's tested

| Test | Proves |
|---|---|
| `SmartScopeTest`, `RolePermissionsTest`, `JwtConfigTest`, `AuthProfilesTest` | scope parsing, the permission map, roles → authorities, no demo accounts outside dev |
| `TokenServiceTest`, `AuthServiceTest`, `ClientCredentialsServiceTest` | staff, system and member token claims; login and refresh; client and scope checks |
| `AuthControllerTest`, `OAuthTokenControllerTest`, `SecurityConfigTest` | the JSON and OAuth2 error shapes, Basic and form client auth, 401s |
| `ScopeInterceptorInteropTest` | over real HTTP: a wrong scope gets 403, member A reading member B gets 403, 401s are `OperationOutcome`s, metadata advertises SMART |
| `AuthorizationMatrixTest` | every JSON endpoint and interop operation × anonymous, both staff roles, clinic-api, a system reader and two members |

The interop rows use test-only providers (`support/TestInteropProviders`) until real ones exist. Each story that adds an endpoint or operation adds its row.
