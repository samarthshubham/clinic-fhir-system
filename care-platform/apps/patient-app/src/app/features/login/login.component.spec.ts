import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '@care-platform/auth';
import { LoginComponent } from './login.component';

describe('LoginComponent (patient-app)', () => {
  let login: ReturnType<typeof vi.fn>;
  let logout: ReturnType<typeof vi.fn>;
  let navigateByUrl: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    login = vi.fn(() => of({ roles: ['PATIENT'] }));
    logout = vi.fn();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { login, logout } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();
    navigateByUrl = vi
      .spyOn(TestBed.inject(Router), 'navigateByUrl')
      .mockResolvedValue(true) as unknown as ReturnType<typeof vi.fn>;
  });

  async function render(): Promise<ComponentFixture<LoginComponent>> {
    const fixture = TestBed.createComponent(LoginComponent);
    await fixture.whenStable();
    return fixture;
  }

  async function signIn(
    fixture: ComponentFixture<LoginComponent>,
    username: string,
    password: string,
  ) {
    for (const [name, value] of [
      ['username', username],
      ['password', password],
    ]) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(
        `input[formControlName="${name}"]`,
      );
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    await fixture.whenStable();
  }

  function alertText(fixture: ComponentFixture<LoginComponent>): string {
    return fixture.nativeElement
      .querySelector('[role="alert"]')
      .textContent.trim();
  }

  it('should sign a patient in and go home', async () => {
    const fixture = await render();

    await signIn(fixture, 'tony', 'pw');

    expect(login).toHaveBeenCalledWith('tony', 'pw');
    expect(navigateByUrl).toHaveBeenCalledWith('/home');
  });

  it('should refuse staff accounts and sign them out', async () => {
    login.mockReturnValue(of({ roles: ['CLINICIAN'] }));
    const fixture = await render();

    await signIn(fixture, 'clinician', 'pw');

    expect(logout).toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(alertText(fixture)).toBe(
      'This app is for patients. Staff use the clinician app.',
    );
  });

  it('should explain wrong credentials and an unreachable server', async () => {
    login.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );
    const fixture = await render();

    await signIn(fixture, 'tony', 'wrong');
    expect(alertText(fixture)).toBe('Invalid username or password');

    login.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );
    await signIn(fixture, 'tony', 'pw');
    expect(alertText(fixture)).toBe('Could not reach the server. Try again.');
  });

  it('should not call the server with empty fields', async () => {
    const fixture = await render();

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    await fixture.whenStable();

    expect(login).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Username is required');
  });

  it('should offer one-click demo patients in development', async () => {
    const fixture = await render();
    const rows: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.pa-select-row'),
    );

    expect(
      rows.map((r) => r.querySelector('.pa-select-name')?.textContent?.trim()),
    ).toEqual(['Tony Stark', 'Priya Nair']);

    rows[1].click();
    await fixture.whenStable();

    expect(login).toHaveBeenCalledWith('priya', 'Priya#2026');
  });
});
