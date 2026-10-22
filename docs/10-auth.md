# Authentication & RBAC

How clinic-api decides **who** is calling (authentication) and **what they may do** (authorization), and how clinician-app and the seeder take part.

## 1. Two questions, two status codes

| Question | Fails with | Example |
|---|---|---|
| **Authentication**: who are you? | `401 Unauthorized` | no token, a tampered token, an expired token, wrong password |
| **Authorization**: you're known, but may you do *this*? | `403 Forbidden` | `frontdesk` posting vitals |

Despite its name, `401` means "unauthenticated". Every error uses the usual `{ "status", "message" }` body, written by `JsonSecurityErrorHandler`.

## 2. What happens to a request

Spring Security is a **filter chain**: servlet filters that run *before* any controller. `SecurityConfig` builds it.

```
GET /api/vitals?patientId=p1      Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
  │
  ├─ BearerTokenAuthenticationFilter   (added by .oauth2ResourceServer().jwt())
  │     reads the header → JwtDecoder (JwtConfig.jwtDecoder):
  │       1. signature matches HMAC-SHA256(secret)?    no → 401 "Invalid or expired token"
  │       2. exp in the future, iss == clinic-api?     no → 401
  │       3. token_type == "access"?                   no → 401
  │     JwtAuthenticationConverter: roles claim → authorities
  │       ["CLINICIAN"] → ROLE_CLINICIAN + clinical:read + vitals:write + …
  │     stores the Authentication in the SecurityContext (this request only)
  │
  ├─ AuthorizationFilter (URL rules in SecurityConfig)
  │     public path? → continue.  Otherwise authenticated?  no → 401
  │
  ├─ DispatcherServlet → @Valid body validation (400 happens here)
  │
  ├─ @PreAuthorize("hasAuthority('clinical:read')")    (method security)
  │     authority present?  no → 403 "Access denied"
  │
  └─ VitalsController.getVitals(...)
```

**Stateless** means no `JSESSIONID` and no server-side session. The token is checked on every request, so the server never looks up "who is logged in".

**CSRF is disabled.** A CSRF attack relies on the browser attaching a cookie automatically. A bearer token lives in a header that only our own JavaScript adds, so another site can't make the browser send it.

Because security runs before routing, an anonymous call to a path that doesn't exist returns `401`, not `404`. Callers can't probe which endpoints exist.

## 3. Anatomy of our JWT

A JWT is `base64url(header).base64url(payload).base64url(signature)`. Decode one at jwt.io and you'll see:

```json
// header
{ "alg": "HS256" }

// payload (claims)
{
  "iss": "clinic-api",
  "sub": "clinician",
  "iat": 1792671000,
  "exp": 1792671900,
  "token_type": "access",
  "roles": ["CLINICIAN"],
  "name": "Anika Patel",
  "practitioner_id": "user-clinician"
}
```

- **Signed, not encrypted.** Anyone can read the payload, so never put secrets or PHI in it. What the signature gives you is *integrity*. Change `"FRONT_DESK"` to `"ADMIN"` and the signature no longer matches; `TokenServiceTest.tamperedTokenFailsSignatureCheck` proves this.
- **HS256** is a symmetric HMAC: the same secret (`clinic.auth.jwt-secret` / `CLINIC_JWT_SECRET`) signs and verifies. That's fine when one service does both jobs. `JwtConfig` refuses secrets shorter than 32 bytes.
- **Verification is local.** The server recomputes the HMAC and checks `exp` and `iss`, with no database or HAPI lookup. That's why stateless JWT scales well, and also why it's hard to revoke (see §8).

## 4. Login and refresh

```
clinician-app                         clinic-api                              HAPI FHIR
    │ POST /api/auth/login {username,password}
    │──────────────────────────────────▶│ AuthenticationManager
    │                                    │   UserDetailsService ← clinic.auth.users (config)
    │                                    │   BCrypt.matches(password, passwordHash)
    │                                    │ PractitionerService.findByUsername ───────▶│ Practitioner?identifier=
    │                                    │                                            │  …/username|clinician
    │                                    │ TokenService.issueTokens
    │◀───────────────────────────────────│ { accessToken (15m), refreshToken (7d), user }
```

**Why two tokens?** The access token is sent on every call, so it should be short-lived: a leaked one is only useful for 15 minutes. The refresh token is sent to one endpoint only. It lets the app get a new access token without asking for the password again.

**Why the `token_type` claim?** Both tokens are signed with the same key. Without the claim, a 7-day refresh token would also work as an access token. The access decoder requires `token_type=access` and the refresh decoder requires `refresh`.

**Refresh re-reads the account.** `AuthService.refresh` looks the username up in config again. If a user is deleted, their next refresh fails ("Account no longer exists"), and a role change applies from that refresh onward.

## 5. Where users live

| What | Where | Why |
|---|---|---|
| Username, **BCrypt hash**, **role** | `clinic.auth.users`: demo users in `application-dev.yaml` (the `dev` profile only) | config is trusted: only whoever deploys the app can change it |
| Display name, title, `practitionerId` | FHIR `Practitioner/user-<username>` with identifier `http://praxis.clinic/fhir/identifier/username` | staff are real clinic people, so they belong in the clinical record |

Roles are kept out of HAPI on purpose. HAPI has no authentication of its own, so anyone who can reach port 8080 could promote themselves. `StaffAccountBootstrap` upserts the Practitioners at startup. If HAPI is down, login still works and falls back to the name in config.

BCrypt is slow on purpose and salts every hash, so leaked hashes are expensive to brute-force. `$2a$10$` means BCrypt with cost 10, which is 2¹⁰ rounds. Login returns the same message for an unknown user and a wrong password, so callers can't discover which usernames exist.

## 6. Roles → permissions → `@PreAuthorize`

```
token.roles = ["FRONT_DESK"]
      │  RolePermissions (server-side, Map<Role, Set<Permission>>)
      ▼
authorities = ROLE_FRONT_DESK, patient:read, patient:write,
              appointment:read, appointment:write, practitioner:read
      │
      ▼
@PreAuthorize("hasAuthority('vitals:write')")  →  missing  →  403
```

- **Roles** describe who you are. **Permissions** describe what an endpoint needs. Endpoints check a permission, never a role, so adding a role such as `NURSE` means editing one map and no controllers.
- Permissions are worked out from the roles on **every request**; they aren't copied into the token. Change the map, restart, and existing tokens immediately get the new rights.
- **Scopes** (`scope` / `scp` claims, e.g. SMART on FHIR's `user/Observation.read`) are a different idea. They record what a *third-party app* is allowed to do on a user's behalf, which is OAuth2 delegated access. We have no third-party apps, so we don't use scopes. By default Spring maps the `scope` claim to `SCOPE_*` authorities; `JwtConfig` replaces that with our `roles` mapping.
- `@PreAuthorize` runs after `@Valid`, so an invalid body gets `400` before the permission check can return `403`.

The full matrix is in [02-api-design.md](./02-api-design.md#permissions), and `AuthorizationMatrixTest` checks every endpoint for every role.

## 7. The browser side (clinician-app)

| Piece | File | Job |
|---|---|---|
| `AuthService` | `core/auth.service.ts` | login / refresh / logout; `user()` and `hasRole()` signals |
| `authInterceptor` | `core/auth.interceptor.ts` | adds `Authorization` to `/api/*` (not `/api/auth/*`); on `401` refreshes once and retries |
| `authGuard`, `roleGuard`, `guestGuard` | `core/auth.guard.ts` | send signed-out users to `/login?returnUrl=…`; keep the front desk out of `/patients/:id` |
| `LoginComponent` | `features/login/` | the form, plus quick-login buttons from `demo-accounts.ts`; production builds replace that file with `demo-accounts.prod.ts` (an empty list) |

- **Token storage.** The access token is kept **in memory** (a signal), so XSS can't read it from storage and it disappears when the tab closes. The refresh token is kept in `localStorage` so a page reload can restore the session through an `APP_INITIALIZER` refresh. The trade-off: script running on the page through XSS could still read `localStorage`. The stronger option is an `HttpOnly; Secure; SameSite=Strict` cookie for the refresh token, which JavaScript can't read. That brings CSRF back for the refresh endpoint, though.
- **The refresh race.** When the access token expires, several requests can get `401` at the same moment. `AuthService.refresh()` shares one in-flight call (`shareReplay`), so they all wait for a single refresh instead of firing five.
- **Guards are UX, not security.** Hiding the chart link from the front desk is a convenience. Anyone can edit the JavaScript or call the API with curl, and the server's `403` is the real gate.

## 8. Trade-offs and what production would change

| Choice here | Production would… |
|---|---|
| Stateless JWT, logout only discards tokens client-side | keep a deny-list or rotating refresh tokens stored server-side, so you can revoke immediately |
| HS256 with a shared secret | use RS256/ES256: an auth server signs with a private key, APIs verify with a public key from a JWKS URL (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`), or use a real IdP (Keycloak, Auth0, Entra) |
| Demo users and a dev secret default exist only in the `dev` profile; without it the app fails to start until `CLINIC_JWT_SECRET` is set | supply `clinic.auth.users` and `CLINIC_JWT_SECRET` from a secret store |
| No login rate limiting or lockout | throttle `/api/auth/login` per IP and per user |
| Refresh token in `localStorage` | an `HttpOnly` cookie plus CSRF protection on `/api/auth/refresh` |
| Coarse `clinical:read` | finer permissions, or patient-level checks (e.g. only patients on your care team) |

## 9. Try it

```bash
curl -i localhost:9090/api/patients
```
`401`, because there's no token.

```bash
TOKEN=$(curl -s -X POST localhost:9090/api/auth/login -H 'Content-Type: application/json' -d '{"username":"frontdesk","password":"FrontDesk#2026"}' | jq -r .accessToken)
```

```bash
curl -i localhost:9090/api/patients -H "Authorization: Bearer $TOKEN"
```
`200`: the front desk can search patients.

```bash
curl -i -X POST localhost:9090/api/vitals -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"patientId":"p1","effectiveDate":"2026-10-22","systolicBp":120}'
```
`403`: the front desk can't record vitals.

```bash
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null; echo
```
Prints the payload. It's readable by anyone, which is why it holds no secrets.

## 10. Where it's tested

| Test | Proves |
|---|---|
| `SecurityConfigTest` | no token → JSON 401; valid token → 200; refresh token as bearer → 401; webhook stays public |
| `TokenServiceTest` | claims, expiry, tampering, access vs refresh separation |
| `JwtConfigTest`, `RolePermissionsTest` | short secrets rejected; roles → authorities; the permission map |
| `AuthServiceTest`, `AuthControllerTest` | BCrypt login, same error for unknown user and wrong password, refresh, removed accounts |
| `AuthorizationMatrixTest` | 25 endpoints × (anonymous + 3 roles): 401 / 403 / 2xx |
| `auth.service.spec.ts`, `auth.interceptor.spec.ts`, `auth.guard.spec.ts`, `login.component.spec.ts` | storage, shared refresh, retry on 401, guards, login form |
