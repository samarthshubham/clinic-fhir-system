import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export type PriorAuthStatus =
  | 'SUBMITTED'
  | 'PENDED'
  | 'APPROVED'
  | 'DENIED'
  | 'NOT_REQUIRED'
  | 'MIXED';

export const PRIOR_AUTH_STATUS_LABEL: Record<PriorAuthStatus, string> = {
  SUBMITTED: 'Submitted',
  PENDED: 'Pended',
  APPROVED: 'Approved',
  DENIED: 'Denied',
  NOT_REQUIRED: 'Not required',
  MIXED: 'Decision updated',
};

export interface PriorAuthResponse {
  orderId: string;
  status: PriorAuthStatus;
  authorizationNumber: string | null;
  denialReasonCode: string | null;
  denialReason: string | null;
}

interface BillingCode {
  system: string;
  code: string;
}

export interface PriorAuthRequest {
  orderId: string;
  service: BillingCode | null;
  placeOfService: string;
  serviceCategory: string;
  diagnoses: BillingCode[];
}

@Injectable({ providedIn: 'root' })
export class PriorAuthService {
  private readonly http = inject(HttpClient);

  list(patientId: string) {
    return this.http.get<PriorAuthResponse[]>('/api/prior-auths', {
      params: { patientId },
    });
  }

  submit(request: PriorAuthRequest) {
    return this.http.post<PriorAuthResponse>('/api/prior-auths', request);
  }
}
