import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { AuthService } from '@care-platform/auth';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let login: ReturnType<typeof vi.fn>;
  let navigateByUrl: ReturnType<typeof vi.fn>;
  let returnUrl: string | null;

  beforeEach(async () => {
    login = vi.fn(() => of({ roles: ['UM_REVIEWER'] }));
    returnUrl = null;
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { login } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              get queryParamMap() {
                return convertToParamMap(returnUrl ? { returnUrl } : {});
              },
            },
          },
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

  function type(
    fixture: ComponentFixture<LoginComponent>,
    name: string,
    value: string,
  ) {
    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      `input[formControlName="${name}"]`,
    );
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(fixture: ComponentFixture<LoginComponent>) {
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    await fixture.whenStable();
  }

  function alertText(fixture: ComponentFixture<LoginComponent>): string {
    return fixture.nativeElement
      .querySelector('[role="alert"]')
      .textContent.trim();
  }

  it('should sign in and go to the queue', async () => {
    const fixture = await render();
    type(fixture, 'username', 'reviewer');
    type(fixture, 'password', 'pw');

    await submit(fixture);

    expect(login).toHaveBeenCalledWith('reviewer', 'pw');
    expect(navigateByUrl).toHaveBeenCalledWith('/queue');
  });

  it('should return to the page that required sign-in', async () => {
    returnUrl = '/members';
    const fixture = await render();
    type(fixture, 'username', 'reviewer');
    type(fixture, 'password', 'pw');

    await submit(fixture);

    expect(navigateByUrl).toHaveBeenCalledWith('/members');
  });

  it('should not call the server with empty fields', async () => {
    const fixture = await render();

    await submit(fixture);

    expect(login).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Username is required');
    expect(fixture.nativeElement.textContent).toContain('Password is required');
  });

  it('should explain rejected credentials', async () => {
    login.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );
    const fixture = await render();
    type(fixture, 'username', 'reviewer');
    type(fixture, 'password', 'wrong');

    await submit(fixture);

    expect(alertText(fixture)).toBe('Invalid username or password');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('should explain an unreachable server', async () => {
    login.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );
    const fixture = await render();
    type(fixture, 'username', 'reviewer');
    type(fixture, 'password', 'pw');

    await submit(fixture);

    expect(alertText(fixture)).toBe('Could not reach the server. Try again.');
  });

  it('should disable the form while signing in', async () => {
    login.mockReturnValue(NEVER);
    const fixture = await render();
    type(fixture, 'username', 'reviewer');
    type(fixture, 'password', 'pw');

    await submit(fixture);

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    );
    expect(button.disabled).toBe(true);
    expect(button.textContent?.trim()).toBe('Signing in…');
  });

  it('should offer one-click demo accounts in dev mode', async () => {
    const fixture = await render();
    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.login__demo button'),
    );

    expect(buttons.map((b) => b.textContent?.trim())).toEqual([
      'Reviewer',
      'Payer admin',
    ]);

    buttons[0].click();
    await fixture.whenStable();

    expect(login).toHaveBeenCalledWith('reviewer', 'Reviewer#2026');
  });
});
