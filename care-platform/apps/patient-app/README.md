# patient-app

Angular application for patients — part of the `care-platform` Nx monorepo.

## Prerequisites

- Node.js 20+
- Run `npm install` from `care-platform/` once before any commands

`nx` is a local devDependency. Run it via `npx nx` from the `care-platform/` directory, or install globally with `npm i -g nx`.

## Development

All commands must be run from the `care-platform/` directory.

```bash
# Serve with hot reload (default port 4200)
npx nx serve patient-app

# Serve on a different port (needed when running alongside clinician-app)
npx nx serve patient-app --port=4300
```

## Other commands

```bash
# Production build → dist/apps/patient-app/
npx nx build patient-app

# Unit tests (vitest)
npx nx test patient-app

# Lint
npx nx lint patient-app

# Run all targets together
npx nx run-many -t build test lint -p patient-app
```

## Running both apps simultaneously

`clinician-app` also defaults to port 4200. Run one of them on a different port:

```bash
# Terminal 1
npx nx serve clinician-app --port=4200

# Terminal 2
npx nx serve patient-app --port=4300
```
