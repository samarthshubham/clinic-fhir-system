export interface PageResponse<T> {
  items: T[];
  total: number;
}

export interface MemberSummary {
  id: string;
  memberId: string | null;
  firstName: string | null;
  lastName: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  planName: string | null;
  coverageStatus: string | null;
}

export interface PlanSummary {
  id: string | null;
  planId: string | null;
  name: string | null;
  type: string | null;
}

export interface MemberCoverage {
  id: string;
  status: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  payerName: string | null;
  relationship: string | null;
  plan: PlanSummary | null;
}

export interface PriorAuthSummary {
  id: string;
  status: string | null;
  submitted: string | null;
  service: string | null;
  outcome: string | null;
  preAuthRef: string | null;
}

export interface MemberResponse {
  id: string;
  memberId: string | null;
  firstName: string | null;
  lastName: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  coverages: MemberCoverage[];
  priorAuths: PriorAuthSummary[];
}

export interface MemberCriteria {
  name: string;
  memberId: string;
}

export type BadgeTone = 'success' | 'warning' | 'critical' | 'info';

export function coverageTone(status: string | null): BadgeTone {
  if (status === 'active') return 'success';
  if (status === 'cancelled') return 'warning';
  return 'info';
}

export function displayName(member: {
  firstName: string | null;
  lastName: string | null;
}): string {
  return (
    [member.lastName, member.firstName].filter(Boolean).join(', ') || 'Unknown'
  );
}
