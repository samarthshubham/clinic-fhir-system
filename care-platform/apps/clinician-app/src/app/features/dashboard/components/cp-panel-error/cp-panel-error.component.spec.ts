import { TestBed } from '@angular/core/testing';
import { CpPanelErrorComponent } from './cp-panel-error.component';

describe('CpPanelErrorComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CpPanelErrorComponent],
    }).compileComponents();
  });

  it('should show the default message', async () => {
    const fixture = TestBed.createComponent(CpPanelErrorComponent);
    await fixture.whenStable();

    expect(
      fixture.nativeElement
        .querySelector('.cp-panel-error__message')
        .textContent.trim(),
    ).toBe('Something went wrong');
  });

  it('should show a custom message', async () => {
    const fixture = TestBed.createComponent(CpPanelErrorComponent);
    fixture.componentRef.setInput('message', 'Timeline unavailable');
    await fixture.whenStable();

    expect(
      fixture.nativeElement
        .querySelector('.cp-panel-error__message')
        .textContent.trim(),
    ).toBe('Timeline unavailable');
  });

  it('should emit retry when the button is clicked', async () => {
    const fixture = TestBed.createComponent(CpPanelErrorComponent);
    const retry = vi.fn();
    fixture.componentInstance.retry.subscribe(retry);
    await fixture.whenStable();

    fixture.nativeElement.querySelector('button').click();

    expect(retry).toHaveBeenCalledTimes(1);
  });
});
