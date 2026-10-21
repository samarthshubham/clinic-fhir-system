# High BP Alerts Demo

Clinicians are alerted when a systolic BP reading above the critical threshold
(`clinical.thresholds.systolic-critical`, default `180`) is recorded.

## How it works

```
POST /api/vitals (systolic 185)
    → clinic-api creates Observation (LOINC 8480-6) in HAPI FHIR
    → HAPI matches Subscription/clinic-high-systolic-bp
        criteria: Observation?code=http://loinc.org|8480-6&value-quantity=gt180
    → HAPI rest-hook POSTs an empty notification with header X-Alert-Secret to
        http://host.docker.internal:9090/api/alerts/bp-subscription
    → clinic-api rejects the call with 401 unless the secret matches
    → clinic-api searches critical systolic readings updated since the last alert,
      following every next page of results
    → Notifier (default LoggingSmsNotifier) logs one mock SMS per reading
```

- HAPI rest-hook delivery is enabled in `infra/docker-compose.yml`
  (`hapi.fhir.subscription.resthook_enabled`). `extra_hosts` maps
  `host.docker.internal` to the host so HAPI can reach clinic-api running on the host.
- clinic-api creates or updates the Subscription at startup (PUT with a fixed id, so it is
  idempotent). The webhook URL is `clinic.alerts.webhook-url` in `application.yaml`.
- The Subscription sends `X-Alert-Secret: <clinic.alerts.webhook-secret>` as a channel header.
  The dev default lives in `application.yaml`; override it with the
  `CLINIC_ALERTS_WEBHOOK_SECRET` environment variable. Calls without the right secret get `401`.
- The Subscription has no payload, so the notification body is empty. The alert only carries
  `Patient/{id}` and `Observation/{id}` references, never names or values.
- `spring-boot:run` starts the JVM with `-Djava.net.preferIPv4Stack=true` so Docker Desktop on
  WSL can forward `host.docker.internal:9090` to clinic-api. The flag is set only for
  `spring-boot:run` (`pom.xml`); a packaged jar needs `java -Djava.net.preferIPv4Stack=true -jar ...`.

## Delivery guarantees

- clinic-api remembers the `lastUpdated` of the last reading it alerted on, in memory only.
- After a restart the first notification looks back `clinic.alerts.first-lookback`
  (default `24h`). Missing an alert is worse than sending it twice, so a reading already alerted
  in that window **can be alerted again after a restart**.
- If the notifier fails for a reading, the error is logged with references only and the
  remaining readings are still sent. The high-water mark stops before the first failed reading,
  so the next notification retries it (and may repeat readings sent after it).
- Registration runs once at startup. If HAPI is unreachable or rejects the Subscription, clinic-api
  only logs a warning (`High BP subscription not registered, alerts disabled`) and keeps running.
  A Subscription registered by an earlier run keeps working; otherwise restart clinic-api once
  HAPI is healthy.

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
- `401` responses on the webhook mean the Subscription header and `clinic.alerts.webhook-secret`
  differ. Restart clinic-api so it re-registers the Subscription with the current secret.
