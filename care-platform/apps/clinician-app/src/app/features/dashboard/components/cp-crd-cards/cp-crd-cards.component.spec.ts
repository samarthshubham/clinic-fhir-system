import { TestBed } from '@angular/core/testing';
import {
  CoverageInformation,
  CrdCard,
} from '@features/dashboard/dashboard.model';
import { CpCrdCardsComponent } from './cp-crd-cards.component';

const paNeeded: CoverageInformation = {
  coverageId: 'cov1',
  covered: 'covered',
  paNeeded: 'auth-needed',
  docNeeded: 'clinical',
  reason:
    'Clinical documentation is needed for the prior authorization request',
  questionnaire:
    'http://praxishealthplan.example/fhir/Questionnaire/mri-lumbar-spine',
  date: '2026-11-16',
  coverageAssertionId: 'a1',
};

const paCard: CrdCard = {
  summary: 'Prior authorization needed: MRI lumbar spine without contrast',
  indicator: 'warning',
  detail: 'Complete the documentation questionnaire.',
  source: 'Praxis Health Plan',
};

const unknownCard: CrdCard = {
  summary: 'Coverage unknown',
  indicator: 'warning',
  detail: 'The payer could not be reached. Check coverage before scheduling.',
  source: 'clinic-api',
};

describe('CpCrdCardsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CpCrdCardsComponent],
    }).compileComponents();
  });

  async function render(
    coverage: CoverageInformation | null | undefined,
    cards: CrdCard[] | null | undefined,
    compact = false,
  ): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(CpCrdCardsComponent);
    fixture.componentRef.setInput('coverage', coverage);
    fixture.componentRef.setInput('cards', cards);
    fixture.componentRef.setInput('compact', compact);
    await fixture.whenStable();
    return fixture.nativeElement;
  }

  function text(el: HTMLElement, selector: string): string[] {
    return Array.from(el.querySelectorAll(selector)).map(
      (e) => e.textContent?.trim() ?? '',
    );
  }

  it('should show the PA needed badge, the card text and the documentation action', async () => {
    const el = await render(paNeeded, [paCard]);

    const badge = el.querySelector('.cp-status-badge');
    expect(badge?.textContent?.trim()).toBe('PA needed');
    expect(badge?.classList).toContain('cp-status-badge--warning');
    expect(text(el, '.app-crd-card__summary')).toEqual([paCard.summary]);
    expect(text(el, '.app-crd-card__detail')).toEqual([paCard.detail]);
    expect(text(el, '.app-crd-card__source')).toEqual([paCard.source]);
    expect(el.querySelector('.app-crd-card')?.classList).toContain(
      'app-crd-card--warning',
    );
    const action = el.querySelector<HTMLButtonElement>(
      '.app-crd-cards__documentation',
    );
    expect(action?.textContent?.trim()).toBe('Complete documentation');
    expect(action?.getAttribute('aria-disabled')).toBe('true');
  });

  it('should show the coverage unknown case when the payer gave no answer', async () => {
    const el = await render(null, [unknownCard]);

    const badge = el.querySelector('.cp-status-badge');
    expect(badge?.textContent?.trim()).toBe('Coverage unknown');
    expect(badge?.classList).toContain('cp-status-badge--info');
    expect(text(el, '.app-crd-card__summary')).toEqual(['Coverage unknown']);
    expect(text(el, '.app-crd-card__detail')).toEqual([unknownCard.detail]);
    expect(el.querySelector('.app-crd-cards__documentation')).toBeNull();
  });

  it('should say there is no payer answer when there are no cards', async () => {
    const el = await render(undefined, undefined);

    expect(text(el, '.cp-status-badge')).toEqual(['Coverage unknown']);
    expect(text(el, '.app-crd-cards__empty')).toEqual([
      'No coverage answer from the payer for this order.',
    ]);
  });

  it('should show a covered badge without the documentation action', async () => {
    const el = await render(
      { ...paNeeded, paNeeded: 'no-auth', docNeeded: null },
      [
        {
          ...paCard,
          summary: 'No prior authorization needed',
          indicator: 'info',
        },
      ],
    );

    expect(el.querySelector('.cp-status-badge')?.classList).toContain(
      'cp-status-badge--success',
    );
    expect(text(el, '.cp-status-badge')).toEqual(['Covered']);
    expect(el.querySelector('.app-crd-cards__documentation')).toBeNull();
  });

  it('should show only summaries in compact mode', async () => {
    const el = await render(
      paNeeded,
      [paCard, { ...paCard, summary: null, detail: null }],
      true,
    );

    expect(text(el, '.app-crd-card__summary')).toEqual([paCard.summary]);
    expect(el.querySelector('.app-crd-card__detail')).toBeNull();
    expect(el.querySelector('.app-crd-card__source')).toBeNull();
    expect(el.querySelector('.app-crd-cards__empty')).toBeNull();
    expect(el.querySelector('.app-crd-cards__documentation')).not.toBeNull();
  });

  it('should hide compact empty text when there are no cards', async () => {
    const el = await render(null, [], true);

    expect(el.querySelector('.app-crd-cards__empty')).toBeNull();
    expect(el.querySelector('.app-crd-cards__list')).toBeNull();
  });
});
