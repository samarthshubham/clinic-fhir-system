import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  FhirBundle,
  FhirPatient,
  FhirEncounter,
  FhirObservation,
  isFhirPatient,
  isFhirEncounter,
  isFhirObservation,
} from '../models/fhir-bundle.model';
import { PatientListItem } from '../models/patient-list-item.model';
import {
  PatientSummary,
  EncounterItem,
  VitalsSnapshot,
  VitalReading,
  ObservationPoint,
} from '../models/patient-detail.model';

const LOINC_SYSTOLIC = '8480-6';
const LOINC_DIASTOLIC = '8462-4';
const LOINC_WEIGHT = '29463-7';
const LOINC_SPO2 = '59408-5';

const LOINC_META: Record<
  string,
  { type: ObservationPoint['vitalType']; label: string }
> = {
  [LOINC_SYSTOLIC]: { type: 'systolic', label: 'Systolic BP' },
  [LOINC_DIASTOLIC]: { type: 'diastolic', label: 'Diastolic BP' },
  [LOINC_WEIGHT]: { type: 'weight', label: 'Weight' },
  [LOINC_SPO2]: { type: 'spo2', label: 'SpO₂' },
};

@Injectable({ providedIn: 'root' })
export class PatientService {
  private readonly http = inject(HttpClient);

  getPatients(): Observable<PatientListItem[]> {
    return this.http
      .get<FhirBundle>('/api/patients')
      .pipe(map((bundle) => this.mapBundle(bundle)));
  }

  getPatientSummary(id: string): Observable<PatientSummary> {
    return this.http
      .get<FhirBundle>(`/api/patients/${id}/summary`)
      .pipe(map((bundle) => this.mapSummaryBundle(bundle)));
  }

  private mapBundle(bundle: FhirBundle): PatientListItem[] {
    return (bundle.entry ?? [])
      .map((e) => e.resource)
      .filter(isFhirPatient)
      .map((p) => this.mapPatient(p));
  }

  private mapPatient(p: FhirPatient): PatientListItem {
    const nameObj = p.name?.[0];
    const given = nameObj?.given?.join(' ') ?? '';
    const family = nameObj?.family ?? '';
    const fullName =
      nameObj?.text ?? ([given, family].filter(Boolean).join(' ') || 'Unknown');

    return {
      id: p.id ?? '',
      name: fullName,
      gender: p.gender ?? 'unknown',
      dob: p.birthDate ?? '',
      status: p.active === false ? 'inactive' : 'active',
      lastEncounter: undefined,
    };
  }

  private mapSummaryBundle(bundle: FhirBundle): PatientSummary {
    const resources = (bundle.entry ?? []).map((e) => e.resource);
    const fhirPatient = resources.find(isFhirPatient);
    const encounters = resources.filter(isFhirEncounter);
    const observations = resources.filter(isFhirObservation);

    const nameObj = fhirPatient?.name?.[0];
    const given = nameObj?.given?.join(' ') ?? '';
    const family = nameObj?.family ?? '';
    const fullName =
      nameObj?.text ?? ([given, family].filter(Boolean).join(' ') || 'Unknown');

    const phone = fhirPatient?.telecom?.find(
      (t) => t.system === 'phone',
    )?.value;
    const email = fhirPatient?.telecom?.find(
      (t) => t.system === 'email',
    )?.value;

    const mrnEntry = fhirPatient?.identifier?.find((id) =>
      id.type?.coding?.some((c) => c.code === 'MR'),
    );
    const mrn = mrnEntry?.value;

    const addr = fhirPatient?.address?.[0];
    const address = addr
      ? {
          line: addr.line?.join(', ') ?? '',
          city: addr.city ?? '',
          state: addr.state ?? '',
          postalCode: addr.postalCode ?? '',
        }
      : undefined;

    const maritalStatus =
      fhirPatient?.maritalStatus?.text ??
      fhirPatient?.maritalStatus?.coding?.[0]?.display;

    const language =
      fhirPatient?.communication?.[0]?.language?.text ??
      fhirPatient?.communication?.[0]?.language?.coding?.[0]?.display;

    return {
      id: fhirPatient?.id ?? '',
      name: fullName,
      dob: fhirPatient?.birthDate ?? '',
      gender: fhirPatient?.gender ?? 'unknown',
      mrn,
      phone,
      email,
      address,
      maritalStatus,
      language,
      status: fhirPatient?.active === false ? 'inactive' : 'active',
      encounters: this.mapEncounters(encounters),
      vitals: this.mapVitals(observations),
      allObservations: this.mapAllObservations(observations),
    };
  }

  private mapEncounters(encounters: FhirEncounter[]): EncounterItem[] {
    return encounters
      .map((e) => ({
        id: e.id ?? '',
        date: e.period?.start ?? '',
        reason: e.reasonCode?.[0]?.text ?? '—',
        status: e.status ?? 'unknown',
      }))
      .sort((a, b) => b.date.localeCompare(a.date));
  }

  private mapVitals(observations: FhirObservation[]): VitalsSnapshot {
    const pick = (loincCode: string): VitalReading | undefined => {
      const matches = observations.filter((o) =>
        o.code?.coding?.some((c) => c.code === loincCode),
      );
      if (!matches.length) return undefined;

      const latest = matches.sort((a, b) =>
        (b.effectiveDateTime ?? '').localeCompare(a.effectiveDateTime ?? ''),
      )[0];

      const value = latest.valueQuantity?.value;
      if (value === undefined) return undefined;

      return {
        value,
        unit: latest.valueQuantity?.unit ?? '',
        date: latest.effectiveDateTime ?? '',
      };
    };

    return {
      systolic: pick(LOINC_SYSTOLIC),
      diastolic: pick(LOINC_DIASTOLIC),
      weight: pick(LOINC_WEIGHT),
      spo2: pick(LOINC_SPO2),
    };
  }

  private mapAllObservations(
    observations: FhirObservation[],
  ): ObservationPoint[] {
    return observations
      .flatMap((o) => {
        const code = o.code?.coding?.[0]?.code;
        const meta = code ? LOINC_META[code] : undefined;
        const value = o.valueQuantity?.value;
        const date = o.effectiveDateTime;
        if (!code || !meta || value === undefined || !date) return [];
        return [
          {
            id: o.id ?? '',
            loincCode: code,
            vitalType: meta.type,
            label: meta.label,
            value,
            unit: o.valueQuantity?.unit ?? '',
            date,
          },
        ];
      })
      .sort((a, b) => a.date.localeCompare(b.date));
  }
}
