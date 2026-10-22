# @care-platform/auth

Shared JWT login for the Angular apps: `AuthService` (login, refresh, logout, current user and roles), `authInterceptor` (adds the bearer token and refreshes once on 401), and `authGuard` / `guestGuard` / `roleGuard`.

Each app configures its own paths and storage key:

```ts
provideAuth({ loginPath: '/login', homePath: '/patients', refreshTokenKey: 'praxis.refreshToken' })
```

Run the tests with `npx nx test auth --coverage --watch=false`.
