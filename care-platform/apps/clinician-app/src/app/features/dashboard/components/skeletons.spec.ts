import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CpCareSkeletonComponent } from './cp-care-skeleton/cp-care-skeleton.component';
import { CpPatientHeaderSkeletonComponent } from './cp-patient-header-skeleton/cp-patient-header-skeleton.component';
import { CpSnapshotSkeletonComponent } from './cp-snapshot-skeleton/cp-snapshot-skeleton.component';
import { CpTimelineSkeletonComponent } from './cp-timeline-skeleton/cp-timeline-skeleton.component';

async function render<T>(component: Type<T>): Promise<HTMLElement> {
  await TestBed.configureTestingModule({
    imports: [component],
  }).compileComponents();
  const fixture = TestBed.createComponent(component);
  await fixture.whenStable();
  return fixture.nativeElement;
}

describe('Skeleton components', () => {
  it('should render the patient header skeleton hidden from assistive tech', async () => {
    const el = await render(CpPatientHeaderSkeletonComponent);

    expect(el.querySelector('.ph-skeleton')?.getAttribute('aria-hidden')).toBe(
      'true',
    );
    expect(el.querySelectorAll('.ph-skeleton__line').length).toBe(3);
  });

  it('should render the snapshot skeleton tiles and rows', async () => {
    const el = await render(CpSnapshotSkeletonComponent);

    expect(el.querySelectorAll('.vitals-grid .px-shimmer').length).toBe(6);
    expect(el.querySelectorAll('.px-shimmer').length).toBe(6 + 3 + 3 + 3);
  });

  it('should render the timeline skeleton pills and cards', async () => {
    const el = await render(CpTimelineSkeletonComponent);
    const cards = Array.from(el.querySelectorAll<HTMLElement>('.tl-sk-card'));

    expect(el.querySelectorAll('.tl-sk-pills .px-shimmer').length).toBe(5);
    expect(cards.map((c) => c.style.height)).toEqual([
      '64px',
      '80px',
      '60px',
      '92px',
      '64px',
    ]);
  });

  it('should render the care skeleton goals, orders and charts', async () => {
    const el = await render(CpCareSkeletonComponent);

    expect(el.querySelectorAll('.care-sk-goal').length).toBe(2);
    expect(el.querySelectorAll('.care-sk-order').length).toBe(3);
    expect(el.querySelectorAll('.care-sk-chart').length).toBe(3);
  });
});
