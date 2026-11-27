export interface AlertResponse {
  id: string;
  patientId: string | null;
  observationId: string | null;
  type?: 'high-systolic-bp' | 'prior-auth-decision' | null;
  reason: string | null;
  raisedAt: string | null;
  status: 'active' | 'inactive' | null;
}

export interface AlertPage {
  items: AlertResponse[];
  total: number;
}
