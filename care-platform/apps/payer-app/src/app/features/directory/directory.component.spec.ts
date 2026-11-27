import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CpDirectoryComponent } from './directory.component';

const provider = {
  id: 'role:location',
  name: 'Aisha Patel',
  specialties: ['Internal Medicine'],
  location: 'Praxis Clinic Springfield',
  address: '400 Example Avenue, Springfield, IL, 62701',
  organization: 'Praxis Clinic',
};

describe('CpDirectoryComponent', () => {
  function render() {
    TestBed.configureTestingModule({
      imports: [CpDirectoryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(CpDirectoryComponent);
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
    http.expectOne((request) => request.url === '/api/directory').flush([]);
    submit(el, ' Internal Medicine ', 'Springfield');
    fixture.detectChanges();
    expect(el.querySelector('mat-progress-bar')).toBeTruthy();
    const request = http.expectOne(
      (request) => request.url === '/api/directory',
    );
    expect(request.request.params.get('specialty')).toBe('Internal Medicine');
    expect(request.request.params.get('city')).toBe('Springfield');

    request.flush([provider]);
    fixture.detectChanges();
    expect(el.textContent).toContain('Aisha Patel');
    expect(el.textContent).toContain('Springfield');

    http.verify();
  });

  it('can retry after a directory error and shows an empty result', () => {
    const { fixture, http, el } = render();

    http
      .expectOne((request) => request.url === '/api/directory')
      .flush(null, {
        status: 503,
        statusText: 'Service Unavailable',
      });
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')).toBeTruthy();
    submit(el, 'Neurology', '');
    http.expectOne((request) => request.url === '/api/directory').flush([]);
    fixture.detectChanges();
    expect(el.textContent).toContain('No providers match');
    expect(el.querySelector('[role="alert"]')).toBeNull();
    http.verify();
  });
});
