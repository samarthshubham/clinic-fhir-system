#!/usr/bin/env bash
# Writes backend=true|false and frontend=true|false to $GITHUB_OUTPUT (or stdout),
# so CI skips test jobs whose code a pull request does not touch.
# Pushes to main always run everything. Changing ci.yml runs everything.
#
# Env: GITHUB_EVENT_NAME, BASE_REF. DIFF_RANGE overrides the compared range (local testing).
set -euo pipefail

output() {
  echo "$1=$2" >>"${GITHUB_OUTPUT:-/dev/stdout}"
}

if [[ "${GITHUB_EVENT_NAME:-}" != "pull_request" && -z "${DIFF_RANGE:-}" ]]; then
  output backend true
  output frontend true
  exit 0
fi

files=$(git diff --name-only "${DIFF_RANGE:-origin/${BASE_REF:?}...HEAD}")

touches() {
  local include=$1 exclude=$2
  grep -qxF '.github/workflows/ci.yml' <<<"$files" && return 0
  grep -E "$include" <<<"$files" | grep -qvE "$exclude"
}

if touches '^clinic-api/' '^clinic-api/(integration-tests|seed-data)/|\.md$'; then
  output backend true
else
  output backend false
fi

if touches '^care-platform/' '\.md$'; then
  output frontend true
else
  output frontend false
fi
