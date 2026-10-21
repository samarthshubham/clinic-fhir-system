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

export interface PageResponse<T> {
  items: T[];
  total: number;
}
