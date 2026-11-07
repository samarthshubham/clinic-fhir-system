#!/usr/bin/env bash
set -euo pipefail

FHIR_URL="${PAYER_FHIR_URL:-http://localhost:8081/fhir}"
WAIT_SECONDS="${WAIT_SECONDS:-900}"

PROFILES=(
  "PAS Claim|http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim"
  "US Core Patient|http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"
  "HRex Coverage|http://hl7.org/fhir/us/davinci-hrex/StructureDefinition/hrex-coverage"
  "CRD ServiceRequest|http://hl7.org/fhir/us/davinci-crd/StructureDefinition/profile-servicerequest"
  "DTR QuestionnaireResponse|http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/dtr-questionnaireresponse"
  "CDex attachment Task|http://hl7.org/fhir/us/davinci-cdex/StructureDefinition/cdex-task-attachment-request"
  "PDex prior authorization|http://hl7.org/fhir/us/davinci-pdex/StructureDefinition/pdex-priorauthorization"
  "CARIN BB EOB inpatient|http://hl7.org/fhir/us/carin-bb/StructureDefinition/C4BB-ExplanationOfBenefit-Inpatient-Institutional"
  "Plan-Net Practitioner|http://hl7.org/fhir/us/davinci-pdex-plan-net/StructureDefinition/plannet-Practitioner"
  "Formulary drug|http://hl7.org/fhir/us/davinci-drug-formulary/StructureDefinition/usdf-FormularyDrug"
  "ATR Group|http://hl7.org/fhir/us/davinci-atr/StructureDefinition/atr-group"
)

json_field() {
  python3 -c "import json, sys; print(json.load(sys.stdin)$1)"
}

echo "Payer FHIR server: $FHIR_URL"
deadline=$((SECONDS + WAIT_SECONDS))
until curl -sf "$FHIR_URL/metadata" >/dev/null; do
  if ((SECONDS >= deadline)); then
    echo "FAIL: $FHIR_URL/metadata not reachable after ${WAIT_SECONDS}s" >&2
    exit 1
  fi
  sleep 10
done

failed=0
for entry in "${PROFILES[@]}"; do
  label=${entry%%|*}
  url=${entry#*|}
  total=$(curl -sf "$FHIR_URL/StructureDefinition?url=$url&_summary=count" | json_field "['total']")
  if [[ "$total" == "1" ]]; then
    echo "ok   $label ($url)"
  else
    echo "FAIL $label ($url): expected 1 StructureDefinition, got $total" >&2
    failed=1
  fi
done

if curl -sf "$FHIR_URL/metadata" | json_field "['rest'][0].get('operation', [])" | grep -q "'name': 'export'"; then
  echo "ok   bulk export (\$export in CapabilityStatement)"
else
  echo "FAIL bulk export: \$export missing from CapabilityStatement" >&2
  failed=1
fi

if ((failed)); then
  exit 1
fi
echo "Payer HAPI smoke test passed."
