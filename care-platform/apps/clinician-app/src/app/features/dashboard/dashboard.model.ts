export interface VitalReading {
  value: number | null;
  unit: string;
  date: string;
  flagged: boolean;
}

export interface AlertItem {
  severity: string;
  type: string;
  message: string;
  resourceId: string;
}

export interface LatestVitals {
  systolicBp: VitalReading | null;
  diastolicBp: VitalReading | null;
  weightKg: VitalReading | null;
  spo2Percent: VitalReading | null;
  heartRate: VitalReading | null;
  temperature: VitalReading | null;
}

export interface ConditionSummary {
  id: string;
  display: string;
  onsetDate: string | null;
  status: string;
}

export interface MedicationSummary {
  id: string;
  name: string;
  dosage: string | null;
  status: string;
}

export interface SnapshotResponse {
  activeConditions: ConditionSummary[];
  currentMedications: MedicationSummary[];
  latestVitals: LatestVitals | null;
  alerts: AlertItem[];
}

export interface GoalProgress {
  currentValue: number | null;
  targetValue: number | null;
  onTrack: boolean | null;
  percentToGoal: number | null;
  message: string | null;
  baselineValue: number | null;
}

export interface GoalSummary {
  id: string;
  description: string | null;
  status: string | null;
  targetValue: number | null;
  targetDate: string | null;
  progress: GoalProgress;
}

export interface ActiveCarePlan {
  id: string;
  title: string | null;
  status: string | null;
  goals: GoalSummary[];
}

export interface PendingOrder {
  id: string;
  code: string | null;
  category: string | null;
  orderedOn: string | null;
  priority: string;
  status: string | null;
  orderedBy: string | null;
}

export interface DataPoint {
  date: string;
  value: number;
}

export interface BpSeries {
  systolic: DataPoint[];
  diastolic: DataPoint[];
  referenceRange: Record<string, number>;
}

export interface SimpleSeries {
  values: DataPoint[];
  unit: string;
  referenceRange: Record<string, unknown>;
}

export interface TrendsResponse {
  period: string;
  from: string;
  to: string;
  series: Record<string, BpSeries | SimpleSeries>;
}

export interface GroupedObservation {
  type: string;
  display: string;
  value: number;
  unit: string;
  flagged: boolean;
}

export interface TimelineEvent {
  id: string;
  type: string;
  date: string;
  title: string;
  subtitle: string | null;
  status: string | null;
  resourceId: string;
  metadata: Record<string, unknown>;
  chiefComplaint: string | null;
  note: string | null;
  groupedObservations: GroupedObservation[] | null;
  linkedTo: string | null;
  linkedFrom: string | null;
}

export interface PatientSummary {
  id: string;
  firstName: string | null;
  lastName: string | null;
  dob: string | null;
  gender: string | null;
  mrn: string;
  phone: string | null;
  email: string | null;
}

export interface DashboardResponse {
  patient: PatientSummary;
  careTeam: Record<string, unknown>;
  snapshot: SnapshotResponse;
  upcomingAppointment: Record<string, unknown> | null;
  recentEncounters: Record<string, unknown>[];
  pendingServiceRequests: PendingOrder[];
  activeCarePlan: ActiveCarePlan | null;
  recentDiagnosticReport: Record<string, unknown> | null;
  warnings: string[];
}

export interface PatientHeaderData {
  id: string;
  displayName: string;
  age: number | null;
  gender: string;
  dob: string | null;
  mrn: string;
  pcp: string;
  org: string;
}

export function extractPatientHeader(
  p: PatientSummary,
  careTeam?: Record<string, unknown>,
): PatientHeaderData {
  const displayName =
    [p.firstName, p.lastName].filter(Boolean).join(' ') || '—';
  const age = p.dob
    ? Math.floor((Date.now() - Date.parse(p.dob)) / 31_557_600_000)
    : null;
  const primaryDoctor = careTeam?.['primaryDoctor'] as
    | Record<string, unknown>
    | undefined;
  const pcp = (primaryDoctor?.['name'] as string) ?? '—';
  const organization = careTeam?.['organization'] as
    | Record<string, unknown>
    | undefined;
  const org = (organization?.['name'] as string) ?? '—';

  return {
    id: p.id,
    displayName,
    age,
    gender: p.gender ?? '—',
    dob: p.dob,
    mrn: p.mrn,
    pcp,
    org,
  };
}
