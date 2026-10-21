import { TestBed } from '@angular/core/testing';
import { CpVitalCellComponent } from './cp-vital-cell.component';

describe('CpVitalCellComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CpVitalCellComponent],
    }).compileComponents();
  });

  it('should render label and value only when optional inputs are empty', async () => {
    const fixture = TestBed.createComponent(CpVitalCellComponent);
    fixture.componentRef.setInput('label', 'BMI');
    fixture.componentRef.setInput('value', '–');
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.vital-head')?.textContent?.trim()).toBe('BMI');
    expect(el.querySelector('.vital-num')?.textContent).toBe('–');
    expect(el.querySelector('.vital-icon')).toBeNull();
    expect(el.querySelector('.vital-unit')).toBeNull();
    expect(el.querySelector('.vital-sub')).toBeNull();
    expect(el.classList.contains('flagged')).toBe(false);
  });

  it('should render icon, unit, sub text and the flagged host class', async () => {
    const fixture = TestBed.createComponent(CpVitalCellComponent);
    fixture.componentRef.setInput('label', 'BP');
    fixture.componentRef.setInput('value', '150/95');
    fixture.componentRef.setInput('unit', 'mmHg');
    fixture.componentRef.setInput('icon', 'favorite');
    fixture.componentRef.setInput('sub', 'high');
    fixture.componentRef.setInput('flagged', true);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.vital-icon')?.textContent).toBe('favorite');
    expect(el.querySelector('.vital-unit')?.textContent).toBe('mmHg');
    expect(el.querySelector('.vital-sub')?.textContent).toBe('high');
    expect(el.classList.contains('flagged')).toBe(true);
  });
});
