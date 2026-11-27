import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { CpReferralDirectoryComponent } from './cp-referral-directory.component';

const provider = {
  id: 'role:location',
  name: 'Aisha Patel',
  specialties: ['Internal Medicine'],
  location: 'Praxis Clinic Springfield',
  address: '400 Example Avenue, Springfield, IL, 62701',
  organization: 'Praxis Clinic',
};

describe('CpReferralDirectoryComponent', () => {
  function render() {
    TestBed.configureTestingModule({
      imports: [CpReferralDirectoryComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: 'patient-1' },
      ],
    });
    const fixture = TestBed.createComponent(CpReferralDirectoryComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    return { fixture, http, el: fixture.nativeElement as HTMLElement };
  }

  function submit(el: HTMLElement, specialty: string, city: string) {
    for (const [key, value] of Object.entries({ specialty, city })) {
      const input = el.querySelector<HTMLInputElement>(
        `input[formcontrolname="${key}"]`,
      );
      if (!input) throw new Error(`Missing ${key} filter`);
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    const form = el.querySelector('form');
    if (!form) throw new Error('Missing directory search form');
    form.dispatchEvent(new Event('submit'));
  }

  it('searches by specialty and location and displays the published listing', () => {
    const { fixture, http, el } = render();

    submit(el, ' Internal Medicine ', 'Springfield');
    fixture.detectChanges();
    expect(el.querySelector('mat-progress-bar')).toBeTruthy();
    const request = http.expectOne(
      (request) => request.url === '/api/directory',
    );
    expect(request.request.params.get('specialty')).toBe('Internal Medicine');
    expect(request.request.params.get('city')).toBe('Springfield');
    expect(request.request.params.get('patientId')).toBe('patient-1');
    request.flush({ planName: 'Praxis Gold PPO', providers: [provider] });
    fixture.detectChanges();
    expect(el.textContent).toContain('Aisha Patel');
    expect(el.textContent).toContain('Springfield');
    expect(el.textContent).toContain('Praxis Gold PPO');
    http.verify();
  });

  it('can retry after a directory error and shows an empty result', () => {
    const { fixture, http, el } = render();
    submit(el, '', '');
    http
      .expectOne((request) => request.url === '/api/directory')
      .flush(null, {
        status: 503,
        statusText: 'Service Unavailable',
      });
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')).toBeTruthy();
    submit(el, 'Neurology', '');
    http
      .expectOne((request) => request.url === '/api/directory')
      .flush({ planName: 'Praxis Gold PPO', providers: [] });
    fixture.detectChanges();
    expect(el.textContent).toContain('No providers match');
    expect(el.querySelector('[role="alert"]')).toBeNull();
    http.verify();
  });

  it('explains when the chart has no directory plan match', () => {
    const { fixture, http, el } = render();
    submit(el, '', '');
    http
      .expectOne((request) => request.url === '/api/directory')
      .flush({ planName: null, providers: [] });
    fixture.detectChanges();
    expect(el.querySelector('[role="status"]')?.textContent).toContain(
      'An active plan network could not be found',
    );
    http.verify();
  });
});
