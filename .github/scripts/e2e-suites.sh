#!/usr/bin/env bash
# Writes suites=<space-separated Nx e2e projects> to $GITHUB_OUTPUT (or stdout): the e2e
# suites a pull request can break. Empty means e2e is skipped. Anything that isn't a pull
# request runs every suite.
#
# Env: GITHUB_EVENT_NAME, BASE_REF. DIFF_RANGE overrides the compared range (local testing).
set -euo pipefail

ALL_SUITES="clinician-app-e2e patient-app-e2e payer-app-e2e"

finish() {
  echo "suites=$1" >>"${GITHUB_OUTPUT:-/dev/stdout}"
  {
    echo "## E2E suites: ${1:-none}"
    tr ';' '\n' <<<"$2" | sed 's/^/- /'
  } >>"${GITHUB_STEP_SUMMARY:-/dev/stderr}"
  exit 0
}

if [[ "${GITHUB_EVENT_NAME:-}" != "pull_request" && -z "${DIFF_RANGE:-}" ]]; then
  finish "$ALL_SUITES" "not a pull request"
fi

# Files that can't change what the running stack does: docs, unit tests, the seed,
# backend tests and their data.
NOT_RUNTIME='\.md$|\.spec\.ts$|/test-setup\.ts$|/vitest\.config\.[^/]+$|^care-platform/apps/seed-demo-data/|^(clinic-api|payer-api)/src/test/|^clinic-api/(integration-tests|seed-data)/'

files=$(git diff --name-only "${DIFF_RANGE:-origin/${BASE_REF:?}...HEAD}" | grep -vE "$NOT_RUNTIME" || true)

touches() {
  grep -qE "$1" <<<"$files"
}

if touches '^infra/(docker-compose\.yml|postgres/|payer/)|^\.github/(workflows/e2e\.yml|scripts/e2e-suites\.sh)$|^care-platform/(package(-lock)?\.json|nx\.json|tsconfig\.base\.json|libs/)'; then
  finish "$ALL_SUITES" "shared infrastructure, libraries or e2e config changed"
fi

suites=()
reasons=()
if touches '^care-platform/apps/(clinician-app|patient-app|clinician-app-e2e)/|^clinic-api/'; then
  suites+=(clinician-app-e2e)
  reasons+=("clinician-app-e2e: clinician-app, patient-app, clinic-api or the suite changed")
fi
if touches '^care-platform/apps/(patient-app|patient-app-e2e)/|^clinic-api/'; then
  suites+=(patient-app-e2e)
  reasons+=("patient-app-e2e: patient-app, clinic-api or the suite changed")
fi
if touches '^care-platform/apps/(payer-app|payer-app-e2e)/|^payer-api/'; then
  suites+=(payer-app-e2e)
  reasons+=("payer-app-e2e: payer-app, payer-api or the suite changed")
fi

if [[ ${#suites[@]} -eq 0 ]]; then
  finish "" "no app, API or e2e code changed"
fi
finish "${suites[*]}" "$(IFS=';'; echo "${reasons[*]}")"
