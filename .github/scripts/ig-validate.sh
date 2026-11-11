#!/usr/bin/env bash
# Validates the golden fixtures and the FHIR written by mapper tests against the IG packages
# the payer HAPI loads (read from infra/docker-compose.yml). Fails on any validation error.
#
# Usage: ig-validate.sh [file or directory ...]
#   Defaults to fixtures/davinci plus clinic-api/target/fhir-out and payer-api/target/fhir-out.
# Codes are checked against tx.fhir.org; answers are kept in TX_CACHE_DIR between runs.
# Env: VALIDATOR_DIR (where validator_cli.jar is cached), TX_CACHE_DIR,
#      VALIDATOR_TIMEOUT (seconds, default 900).
set -euo pipefail

VALIDATOR_VERSION=6.10.4
VALIDATOR_SHA256=1106b9d58f9e363e47bea7c4fc065841e5fc91fe9d062775c3bfdd212bd653cc

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
validator_dir=${VALIDATOR_DIR:-$HOME/.cache/hl7-validator/$VALIDATOR_VERSION}
tx_cache=${TX_CACHE_DIR:-$HOME/.cache/hl7-validator/tx-cache}
jar=$validator_dir/validator_cli.jar

download_validator() {
  mkdir -p "$validator_dir"
  if [[ ! -f "$jar" ]]; then
    echo "Downloading validator_cli.jar $VALIDATOR_VERSION"
    curl -sSfL --retry 3 --max-time 600 -o "$jar.part" \
      "https://github.com/hapifhir/org.hl7.fhir.core/releases/download/$VALIDATOR_VERSION/validator_cli.jar"
    mv "$jar.part" "$jar"
  fi
  if ! echo "$VALIDATOR_SHA256  $jar" | sha256sum --check --status; then
    rm -f "$jar"
    echo "::error::validator_cli.jar does not match the pinned SHA-256" >&2
    exit 1
  fi
}

ig_arguments() {
  local compose=$repo_root/infra/docker-compose.yml
  local key
  for key in $(sed -nE 's/^ *hapi\.fhir\.implementationguides\.([a-z0-9]+)\.name:.*/\1/p' "$compose"); do
    local name version
    name=$(sed -nE "s/^ *hapi\.fhir\.implementationguides\.$key\.name: *([^ ]+).*/\1/p" "$compose")
    version=$(sed -nE "s/^ *hapi\.fhir\.implementationguides\.$key\.version: *([^ ]+).*/\1/p" "$compose")
    if [[ -z "$name" || -z "$version" ]]; then
      echo "::error::IG '$key' in $compose has no name or version" >&2
      exit 1
    fi
    printf -- '-ig\n%s#%s\n' "$name" "$version"
  done
}

collect_files() {
  local target
  for target in "$@"; do
    [[ -e "$target" ]] || continue
    find "$target" -type f -name '*.json' | sort
  done
}

# A CDS Hooks request or response is not a FHIR resource; validate the resources inside it.
embedded_dir=$(mktemp -d)
trap 'rm -rf "$embedded_dir"' EXIT

expand_cds_hooks_json() {
  local file
  for file in "$@"; do
    if jq -e 'has("resourceType")' "$file" >/dev/null; then
      echo "$file"
      continue
    fi
    local name i=0
    name=$(basename "$file" .json)
    while IFS= read -r resource; do
      i=$((i + 1))
      echo "$resource" >"$embedded_dir/$name.$i.json"
      echo "$embedded_dir/$name.$i.json"
    done < <(jq -c 'def outermost: if type == "object" and has("resourceType") then .
      elif type == "object" or type == "array" then .[] | outermost else empty end; outermost' "$file")
    if [[ $i -eq 0 ]]; then
      echo "::error::$file has no resourceType and no embedded FHIR resources" >&2
      exit 1
    fi
  done
}

if [[ $# -eq 0 ]]; then
  set -- "$repo_root/fixtures/davinci" "$repo_root/clinic-api/target/fhir-out" "$repo_root/payer-api/target/fhir-out"
fi

mapfile -t json_files < <(collect_files "$@")
if [[ ${#json_files[@]} -eq 0 ]]; then
  echo "::error::No FHIR JSON files found in: $*" >&2
  exit 1
fi
expanded=$(expand_cds_hooks_json "${json_files[@]}")
mapfile -t files <<<"$expanded"

mapfile -t igs < <(ig_arguments)
if [[ ${#igs[@]} -eq 0 ]]; then
  echo "::error::No implementation guides found in infra/docker-compose.yml" >&2
  exit 1
fi

download_validator
mkdir -p "$tx_cache"

echo "Validating ${#files[@]} files against: ${igs[*]//-ig/}"
timeout "${VALIDATOR_TIMEOUT:-900}" java -Xmx3g -Djava.net.preferIPv4Stack=true -jar "$jar" "${files[@]}" \
  -version 4.0.1 "${igs[@]}" -txCache "$tx_cache" -advisor-file "$repo_root/.github/scripts/ig-validate-advisor.txt" \
  -output-style compact
