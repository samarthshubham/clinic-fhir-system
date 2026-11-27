# clinician-app

Angular application for clinicians — part of the `care-platform` Nx monorepo.

## Prerequisites

- Node.js 20+
- Run `npm install` from `care-platform/` once before any commands

`nx` is a local devDependency. Run it via `npx nx` from the `care-platform/` directory, or install globally with `npm i -g nx`.

## Development

All commands must be run from the `care-platform/` directory.

```bash
# Serve with hot reload (default port 4200)
npx nx serve clinician-app

# Serve on a different port (needed when running alongside patient-app)
npx nx serve clinician-app --port=4200
```

## Other commands

```bash
# Production build → dist/apps/clinician-app/
npx nx build clinician-app

# Unit tests (vitest)
npx nx test clinician-app

# Lint
npx nx lint clinician-app

# Run all targets together
npx nx run-many -t build test lint -p clinician-app
```

## Running both apps simultaneously

`patient-app` also defaults to port 4200. Run one of them on a different port:

```bash
# Terminal 1
npx nx serve clinician-app --port=4200

# Terminal 2
npx nx serve patient-app --port=4300
```
