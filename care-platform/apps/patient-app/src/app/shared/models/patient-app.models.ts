export interface PageResponse<T> {
  items: T[];
  total: number;
}

export interface PatientResponse {
  id: string;
  firstName: string | null;
  lastName: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  phone: string | null;
  email: string | null;
  active: boolean | null;
}

export interface AppointmentResponse {
  id: string;
  patientId: string | null;
  practitionerId: string | null;
  start: string | null;
  end: string | null;
  description: string | null;
  status: string | null;
}

export interface PatientListItem {
  id: string;
  firstName: string;
  lastName: string;
  dob: string;
  gender: string;
  avatarHue: number;
}

export interface VitalReading {
  value: number | null;
  unit: string;
  date: string;
  flagged: boolean;
}

export interface LatestVitals {
  systolicBp: VitalReading | null;
  diastolicBp: VitalReading | null;
  weightKg: VitalReading | null;
  spo2Percent: VitalReading | null;
  heartRate: VitalReading | null;
  temperature: VitalReading | null;
}

export interface AlertItem {
  severity: string;
  type: string;
  message: string;
  resourceId?: string;
}

export interface SnapshotResponse {
  activeConditions: { display: string; [key: string]: unknown }[];
  currentMedications: {
    name: string;
    dosage: string;
    [key: string]: unknown;
  }[];
  latestVitals: LatestVitals | null;
  alerts: AlertItem[];
}

export interface DataPoint {
  date: string;
  value: number;
}

export interface BpSeriesData {
  systolic: DataPoint[];
  diastolic: DataPoint[];
  referenceRange?: { systolicMax?: number; diastolicMax?: number };
}

export interface SimpleSeriesData {
  values: DataPoint[];
  unit: string;
  referenceRange?: { min?: number } | null;
}

export interface TrendsApiResponse {
  period: string;
  from: string;
  to: string;
  series: {
    bp?: BpSeriesData;
    spo2?: SimpleSeriesData;
    weight?: SimpleSeriesData;
  };
}

export interface AppointmentItem {
  id: string;
  start: string;
  description: string;
  status: string;
}

export interface PatientSummary {
  id: string;
  firstName: string;
  lastName: string;
  dob: string;
  gender: string;
}

export interface DashboardResponse {
  patient: PatientSummary;
  snapshot: SnapshotResponse;
  upcomingAppointment: AppointmentItem | null;
  warnings?: string[];
}

export interface PostVitalsRequest {
  patientId: string;
  encounterId?: string;
  effectiveDate: string;
  systolicBp?: number | null;
  diastolicBp?: number | null;
  weightKg?: number | null;
  spo2Percent?: number | null;
  heartRateBpm?: number | null;
}
