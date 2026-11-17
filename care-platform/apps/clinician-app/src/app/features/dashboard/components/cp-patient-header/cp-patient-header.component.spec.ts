import { TestBed } from '@angular/core/testing';
import { CpPatientHeaderComponent } from './cp-patient-header.component';
import { PatientHeaderData } from '@features/dashboard/dashboard.model';
import { ToastService } from '@core/toast.service';

const data: PatientHeaderData = {
  id: 'p1',
  displayName: 'jane doe',
  age: 44,
  gender: 'female',
  dob: '1980-06-15',
  mrn: 'MRN-1',
  pcp: 'Dr. Smith',
  org: 'City Clinic',
};

describe('CpPatientHeaderComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CpPatientHeaderComponent],
    }).compileComponents();
  });

  async function render(d: PatientHeaderData) {
    const fixture = TestBed.createComponent(CpPatientHeaderComponent);
    fixture.componentRef.setInput('data', d);
    await fixture.whenStable();
    return fixture;
  }

  it('should render patient details', async () => {
    const fixture = await render(data);
    const el: HTMLElement = fixture.nativeElement;

    expect(
      el.querySelector('.cp-patient-header__avatar')?.textContent?.trim(),
    ).toBe('JD');
    expect(el.querySelector('.cp-patient-header__meta')?.textContent).toContain(
      '44 yo',
    );
    expect(el.querySelector('.cp-patient-header__meta')?.textContent).toContain(
      'Female',
    );
    const sub = el.querySelector('.cp-patient-header__sub')?.textContent ?? '';
    expect(sub).toContain('MRN-1');
    expect(sub).toContain('06/15/1980');
    expect(sub).toContain('Dr. Smith');
    expect(sub).toContain('City Clinic');
  });

  it('should show a dash when age is unknown', async () => {
    const fixture = await render({ ...data, age: null });

    expect(
      fixture.nativeElement.querySelector('.cp-patient-header__meta')
        .textContent,
    ).toContain('—');
  });

  it('should show a dash when date of birth is unknown', async () => {
    const fixture = await render({ ...data, dob: null });

    expect(
      fixture.nativeElement
        .querySelectorAll('.cp-patient-header__sub .cp-font-mono')[1]
        .textContent.trim(),
    ).toBe('—');
  });

  it('should build initials from a single name', async () => {
    const fixture = await render({ ...data, displayName: 'Cher' });

    expect(fixture.componentInstance.initials).toBe('C');
  });

  it('should emit newOrder from the New order button without a toast', async () => {
    const fixture = await render(data);
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    let emitted = 0;
    fixture.componentInstance.newOrder.subscribe(() => emitted++);

    newOrderButton(fixture.nativeElement)?.click();

    expect(emitted).toBe(1);
    expect(show).not.toHaveBeenCalled();
  });

  it('should hide the New order button when the user cannot order', async () => {
    const fixture = TestBed.createComponent(CpPatientHeaderComponent);
    fixture.componentRef.setInput('data', data);
    fixture.componentRef.setInput('canOrder', false);
    await fixture.whenStable();

    expect(newOrderButton(fixture.nativeElement)).toBeUndefined();
  });

  it('should show a placeholder toast for Note and Open chart', async () => {
    const fixture = await render(data);
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    );

    buttons
      .filter((b) => !b.textContent?.includes('New order'))
      .forEach((b) => b.click());

    expect(show.mock.calls.map((c) => c[0])).toEqual([
      'Note is a placeholder in this prototype',
      'Open chart is a placeholder in this prototype',
    ]);
  });
});

function newOrderButton(el: HTMLElement): HTMLButtonElement | undefined {
  return Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(
    (b) => b.textContent?.includes('New order'),
  );
}
