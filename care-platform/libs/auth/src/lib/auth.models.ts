export type Role = 'ADMIN' | 'CLINICIAN' | 'FRONT_DESK' | 'PATIENT';

export interface AuthUser {
  username: string;
  name: string;
  title: string | null;
  practitionerId: string | null;
  patientId: string | null;
  roles: Role[];
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}
