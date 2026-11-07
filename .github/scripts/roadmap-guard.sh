#!/usr/bin/env bash
# Cheap, deterministic checks for payer / Da Vinci pull requests, run before the
# roadmap-review agent. Writes payer=true|false and story=<id> to $GITHUB_OUTPUT
# (or stdout) and exits 1 when a payer PR breaks a rule.
# Passing it proves nothing about the work: a pasted story line and any test file
# satisfy it. The roadmap-review agent verifies the claims against the diff.
#
# Env: BASE_REF, or DIFF_RANGE to override the compared range (local testing).
#      PR_BODY, or PR_BODY_FILE pointing to a file holding it.
set -euo pipefail

ROADMAP=docs/roadmap/payer-davinci.md
PAYER_PATHS='^payer-api/|^care-platform/apps/payer-app|^fixtures/davinci/|^clinic-api/src/main/java/.*/(client|interop)/|^care-platform/apps/seed-demo-data/src/features/payer-'

output() {
  echo "$1=$2" >>"${GITHUB_OUTPUT:-/dev/stdout}"
}

fail() {
  echo "::error::$1" >&2
  failed=true
}

files=$(git diff --name-only "${DIFF_RANGE:-origin/${BASE_REF:?}...HEAD}")
body=${PR_BODY:-}
if [[ -n "${PR_BODY_FILE:-}" ]]; then
  body=$(cat "$PR_BODY_FILE")
fi

if ! grep -qE "$PAYER_PATHS" <<<"$files"; then
  output payer false
  output story ""
  echo "No payer paths changed; roadmap checks skipped."
  exit 0
fi
output payer true

failed=false

story=$(grep -oE 'Roadmap story: *E[0-9]+-S[0-9]+' <<<"$body" | head -n 1 | grep -oE 'E[0-9]+-S[0-9]+' || true)
output story "$story"
if [[ -z "$story" ]]; then
  fail "Payer paths changed but the PR description has no 'Roadmap story: E<n>-S<n>' line."
elif ! grep -qE "^#### $story " "$ROADMAP"; then
  fail "Roadmap story $story is not in $ROADMAP."
fi

changed_main_without_tests() {
  local module=$1
  grep -qE "^$module/src/main/" <<<"$files" && ! grep -qE "^$module/src/test/" <<<"$files"
}

if changed_main_without_tests payer-api; then
  fail "payer-api/src/main changed without any change under payer-api/src/test."
fi

if grep -qE '^clinic-api/src/main/java/.*/(client|interop)/' <<<"$files" &&
  ! grep -qE '^clinic-api/src/test/' <<<"$files"; then
  fail "clinic-api payer client/interop code changed without any change under clinic-api/src/test."
fi

if grep -qE '^care-platform/apps/payer-app/src/.*\.ts$' <<<"$files" &&
  ! grep -qE '^care-platform/apps/payer-app/src/.*\.spec\.ts$' <<<"$files"; then
  fail "payer-app source changed without any spec change."
fi

if grep -qE '^fixtures/davinci/' <<<"$files" && ! grep -qiE 'contract change' <<<"$body"; then
  echo "::warning::Golden fixtures changed. Say 'Contract change' in the PR description if the wire format changed."
fi

if [[ "$failed" == true ]]; then
  exit 1
fi
echo "Roadmap guard passed for $story."
