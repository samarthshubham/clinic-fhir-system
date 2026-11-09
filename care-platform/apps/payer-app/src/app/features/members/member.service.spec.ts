import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { MemberService } from './member.service';
import { coverageTone, displayName } from './member.models';

describe('MemberService', () => {
  let service: MemberService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MemberService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should search members by name and member id', () => {
    service.search({ name: 'Nair', memberId: 'PHP-10001' }).subscribe();

    const req = http.expectOne(
      (r) =>
        r.url === '/api/members' &&
        r.params.get('name') === 'Nair' &&
        r.params.get('memberId') === 'PHP-10001',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ items: [], total: 0 });
  });

  it('should leave out empty criteria', () => {
    service.search({ name: '', memberId: '' }).subscribe();

    const req = http.expectOne('/api/members');
    expect(req.request.params.keys()).toEqual([]);
    req.flush({ items: [], total: 0 });
  });

  it('should load one member by id', () => {
    service.getMember('m 1').subscribe();

    http.expectOne('/api/members/m%201').flush({});
  });
});

describe('member display helpers', () => {
  it.each([
    ['active', 'success'],
    ['cancelled', 'warning'],
    ['draft', 'info'],
    [null, 'info'],
  ])('should show %s coverage as %s', (status, tone) => {
    expect(coverageTone(status)).toBe(tone);
  });

  it('should show last name first and fall back to Unknown', () => {
    expect(displayName({ firstName: 'Priya', lastName: 'Nair' })).toBe(
      'Nair, Priya',
    );
    expect(displayName({ firstName: null, lastName: 'Nair' })).toBe('Nair');
    expect(displayName({ firstName: null, lastName: null })).toBe('Unknown');
  });
});
