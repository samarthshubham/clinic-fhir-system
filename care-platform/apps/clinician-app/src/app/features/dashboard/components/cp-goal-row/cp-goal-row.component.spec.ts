import { TestBed } from '@angular/core/testing';
import { CpGoalRowComponent } from './cp-goal-row.component';
import { GoalSummary } from '../../dashboard.model';

function goal(overrides: Partial<GoalSummary['progress']> = {}): GoalSummary {
  return {
    id: 'g1',
    description: 'Lower systolic BP',
    status: 'active',
    targetValue: 130,
    targetDate: '2024-12-31',
    progress: {
      currentValue: 140,
      targetValue: 130,
      onTrack: true,
      percentToGoal: 60,
      message: 'Keep going',
      baselineValue: 155,
      ...overrides,
    },
  };
}

describe('CpGoalRowComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CpGoalRowComponent],
    }).compileComponents();
  });

  async function render(g: GoalSummary, expanded = false) {
    const fixture = TestBed.createComponent(CpGoalRowComponent);
    fixture.componentRef.setInput('goal', g);
    fixture.componentRef.setInput('expanded', expanded);
    await fixture.whenStable();
    return fixture;
  }

  it('should clamp the percent between 0 and 100', async () => {
    expect(
      (await render(goal({ percentToGoal: 140 }))).componentInstance.percent(),
    ).toBe(100);
    expect(
      (await render(goal({ percentToGoal: -20 }))).componentInstance.percent(),
    ).toBe(0);
    expect(
      (await render(goal({ percentToGoal: 42 }))).componentInstance.percent(),
    ).toBe(42);
    expect(
      (await render(goal({ percentToGoal: null }))).componentInstance.percent(),
    ).toBeNull();
  });

  it('should render the collapsed row with progress bar and on-track status', async () => {
    const fixture = await render(goal());
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.goal-row__desc')?.textContent).toBe(
      'Lower systolic BP',
    );
    expect(el.querySelector('.goal-row__current')?.textContent).toBe('140');
    expect(el.querySelector('.goal-row__status--on-track')).not.toBeNull();
    expect(el.querySelector('.goal-row__pct')?.textContent).toBe('60%');
    expect(el.querySelector('.goal-row__detail')).toBeNull();
    expect(el.querySelector('.goal-row__chevron')?.textContent).toBe(
      'chevron_right',
    );
  });

  it('should show off-track status and hide the bar when there is no percent', async () => {
    const fixture = await render(
      goal({ onTrack: false, percentToGoal: null, currentValue: null }),
    );
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.goal-row__status--off-track')).not.toBeNull();
    expect(el.querySelector('.goal-row__bar-row')).toBeNull();
    expect(el.querySelector('.goal-row__current')).toBeNull();
  });

  it('should show no status when on-track is unknown', async () => {
    const fixture = await render(goal({ onTrack: null }));

    expect(fixture.nativeElement.querySelector('.goal-row__status')).toBeNull();
  });

  it('should render details when expanded', async () => {
    const fixture = await render(goal(), true);
    const el: HTMLElement = fixture.nativeElement;
    const stats = Array.from(el.querySelectorAll('.goal-row__stat-val')).map(
      (s) => s.textContent,
    );

    expect(stats).toEqual(['155', '140', '130']);
    expect(el.querySelector('.goal-row__target-date')?.textContent).toContain(
      'Dec 31, 2024',
    );
    expect(el.querySelector('.goal-row__msg')?.textContent).toBe('Keep going');
    expect(el.querySelector('.goal-row__chevron')?.textContent).toBe(
      'expand_more',
    );
  });

  it('should fall back to placeholders in expanded details', async () => {
    const g = {
      ...goal({
        baselineValue: null,
        currentValue: null,
        targetValue: null,
        message: null,
      }),
      targetValue: null,
      targetDate: null,
    };
    const fixture = await render(g, true);
    const el: HTMLElement = fixture.nativeElement;
    const stats = Array.from(el.querySelectorAll('.goal-row__stat-val')).map(
      (s) => s.textContent,
    );

    expect(stats).toEqual(['—', '—', '—']);
    expect(el.querySelector('.goal-row__target-date')).toBeNull();
    expect(el.querySelector('.goal-row__msg')).toBeNull();
  });

  it('should emit toggle with the goal id when the row is clicked', async () => {
    const fixture = await render(goal());
    const toggle = vi.fn();
    fixture.componentInstance.goalToggle.subscribe(toggle);

    fixture.nativeElement.querySelector('.goal-row').click();

    expect(toggle).toHaveBeenCalledWith('g1');
  });

  it('should not emit toggle when clicking inside the expanded details', async () => {
    const fixture = await render(goal(), true);
    const toggle = vi.fn();
    fixture.componentInstance.goalToggle.subscribe(toggle);

    fixture.nativeElement.querySelector('.goal-row__detail').click();

    expect(toggle).not.toHaveBeenCalled();
  });

  it('should toggle from the keyboard with Enter or Space', async () => {
    const fixture = await render(goal());
    const toggle = vi.fn();
    fixture.componentInstance.goalToggle.subscribe(toggle);
    const row: HTMLElement = fixture.nativeElement.querySelector('.goal-row');
    const space = new KeyboardEvent('keydown', { key: ' ', cancelable: true });

    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    row.dispatchEvent(space);

    expect(toggle).toHaveBeenCalledTimes(2);
    expect(space.defaultPrevented).toBe(true);
    expect(row.getAttribute('tabindex')).toBe('0');
    expect(row.getAttribute('aria-expanded')).toBe('false');
  });

  it('should not toggle on key presses inside the expanded details', async () => {
    const fixture = await render(goal(), true);
    const toggle = vi.fn();
    fixture.componentInstance.goalToggle.subscribe(toggle);

    fixture.nativeElement
      .querySelector('.goal-row__detail')
      .dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }),
      );

    expect(toggle).not.toHaveBeenCalled();
  });
});
