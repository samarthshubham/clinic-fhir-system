# High BP Alerts Demo

Clinicians are alerted when a systolic BP reading above the critical threshold
(`clinical.thresholds.systolic-critical`, default `180`) is recorded.

## How it works

```
POST /api/vitals (systolic 185)
    → clinic-api creates Observation (LOINC 8480-6) in HAPI FHIR
    → HAPI matches Subscription/clinic-high-systolic-bp
        criteria: Observation?code=http://loinc.org|8480-6&value-quantity=gt180
    → HAPI rest-hook POSTs an empty notification to
        http://host.docker.internal:9090/api/alerts/bp-subscription
    → clinic-api searches critical systolic readings updated since the last alert
    → Notifier (default LoggingSmsNotifier) logs one mock SMS per reading
```

- HAPI rest-hook delivery is enabled in `infra/docker-compose.yml`
  (`hapi.fhir.subscription.resthook_enabled`). `extra_hosts` maps
  `host.docker.internal` to the host so HAPI can reach clinic-api running on the host.
- clinic-api creates or updates the Subscription at startup (PUT with a fixed id, so it is
  idempotent). The webhook URL is `clinic.alerts.webhook-url` in `application.yaml`.
- The Subscription has no payload, so the notification body is empty. The alert only carries
  `Patient/{id}` and `Observation/{id}` references, never names or values.
- `spring-boot:run` starts the JVM with `-Djava.net.preferIPv4Stack=true` so Docker Desktop on
  WSL can forward `host.docker.internal:9090` to clinic-api.

## Scripted demo

```bash
./dev.sh start                      # or: docker compose -f infra/docker-compose.yml up -d
                                    #     cd clinic-api && ./mvnw spring-boot:run
```

In a second terminal, from the repo root:

```bash
grep "High BP subscription" logs/clinic-api.log
# High BP subscription registered, notifying http://host.docker.internal:9090/api/alerts/bp-subscription

PATIENT_ID=$(curl -s -X POST http://localhost:9090/api/patients \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Demo","lastName":"Alert","dateOfBirth":"1960-01-01","gender":"male"}' \
  | sed -n 's/^  "id": "\(.*\)",$/\1/p')

curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:9090/api/vitals \
  -H 'Content-Type: application/json' \
  -d "{\"patientId\":\"$PATIENT_ID\",\"effectiveDate\":\"$(date +%F)\",\"systolicBp\":185,\"diastolicBp\":110}"

sleep 5
grep "mock-sms" logs/clinic-api.log
```

Expected, within a few seconds of the vitals call:

```
[mock-sms] Clinician alert: Systolic BP above 180 mmHg for Patient/54 (Observation/55)
```

A reading at or below the threshold (for example `"systolicBp":120`) produces no alert.

## Troubleshooting

- Check the Subscription is `active`:
  `curl -s http://localhost:8080/fhir/Subscription/clinic-high-systolic-bp | grep status`
- Delivery errors are logged by HAPI:
  `docker compose -f infra/docker-compose.yml logs hapi-fhir | grep -i subscription`
- If clinic-api started before HAPI was up, the Subscription is not registered (a warning is
  logged). Restart clinic-api once HAPI is healthy.
