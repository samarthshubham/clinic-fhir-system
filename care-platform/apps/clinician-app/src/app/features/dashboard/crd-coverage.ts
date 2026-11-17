import { CoverageInformation } from './dashboard.model';

export type CoverageBadgeTone = 'success' | 'warning' | 'critical' | 'info';

export interface CoverageBadge {
  label: string;
  tone: CoverageBadgeTone;
}

const UNKNOWN: CoverageBadge = { label: 'Coverage unknown', tone: 'info' };
const PRIOR_AUTH_NEEDED = ['auth-needed', 'performpa'];

export function documentationNeeded(
  coverage: CoverageInformation | null | undefined,
): boolean {
  return !!coverage?.docNeeded && coverage.docNeeded !== 'no-doc';
}

export function coverageBadge(
  coverage: CoverageInformation | null | undefined,
): CoverageBadge {
  if (!coverage) return UNKNOWN;
  if (coverage.covered === 'not-covered')
    return { label: 'Not covered', tone: 'critical' };
  if (PRIOR_AUTH_NEEDED.includes(coverage.paNeeded ?? ''))
    return { label: 'PA needed', tone: 'warning' };
  if (documentationNeeded(coverage))
    return { label: 'Doc needed', tone: 'warning' };
  if (coverage.covered === 'covered')
    return { label: 'Covered', tone: 'success' };
  return UNKNOWN;
}
