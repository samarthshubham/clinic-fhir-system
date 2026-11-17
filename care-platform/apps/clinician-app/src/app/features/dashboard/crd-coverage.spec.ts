import { CoverageInformation } from './dashboard.model';
import { coverageBadge, documentationNeeded } from './crd-coverage';

function coverage(fields: Partial<CoverageInformation>): CoverageInformation {
  return {
    coverageId: 'cov1',
    covered: 'covered',
    paNeeded: 'no-auth',
    docNeeded: null,
    reason: null,
    questionnaire: null,
    date: '2026-11-11',
    coverageAssertionId: 'a1',
    ...fields,
  };
}

describe('coverageBadge', () => {
  it.each([
    ['no coverage information', null, 'Coverage unknown', 'info'],
    ['covered without prior auth', coverage({}), 'Covered', 'success'],
    [
      'prior auth needed',
      coverage({ paNeeded: 'auth-needed', docNeeded: 'clinical' }),
      'PA needed',
      'warning',
    ],
    [
      'payer must perform PA',
      coverage({ paNeeded: 'performpa' }),
      'PA needed',
      'warning',
    ],
    [
      'documentation needed without prior auth',
      coverage({ docNeeded: 'admin' }),
      'Doc needed',
      'warning',
    ],
    [
      'not covered',
      coverage({ covered: 'not-covered', paNeeded: null }),
      'Not covered',
      'critical',
    ],
    [
      'conditional coverage',
      coverage({ covered: 'conditional', paNeeded: null }),
      'Coverage unknown',
      'info',
    ],
  ])('maps %s', (_case, info, label, tone) => {
    expect(coverageBadge(info)).toEqual({ label, tone });
  });

  it('treats a missing coverage field as unknown', () => {
    expect(coverageBadge(undefined)).toEqual({
      label: 'Coverage unknown',
      tone: 'info',
    });
  });
});

describe('documentationNeeded', () => {
  it.each([
    ['no coverage information', null, false],
    ['no doc-needed value', coverage({}), false],
    ['no-doc', coverage({ docNeeded: 'no-doc' }), false],
    ['clinical', coverage({ docNeeded: 'clinical' }), true],
    ['both', coverage({ docNeeded: 'both' }), true],
  ])('is right for %s', (_case, info, expected) => {
    expect(documentationNeeded(info)).toBe(expected);
  });
});
