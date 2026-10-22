import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ThemeService } from '@core/theme.service';
import { ToastService } from '@core/toast.service';
import { AuthService, Role } from '@care-platform/auth';
import { AlertService } from '@core/alert.service';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Admin',
  CLINICIAN: 'Clinician',
  FRONT_DESK: 'Front desk',
  PATIENT: 'Patient',
};

@Component({
  selector: 'cp-topbar',
  imports: [RouterLink, RouterLinkActive, MatIconModule],
  templateUrl: './cp-topbar.component.html',
  styleUrl: './cp-topbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpTopbarComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly auth = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly alerts = inject(AlertService);

  protected readonly activeAlertCount = toSignal(
    this.alerts.activeAlerts$.pipe(map((page) => page?.total ?? 0)),
    { initialValue: 0 },
  );

  constructor() {
    this.alerts.newAlertCount$
      .pipe(takeUntilDestroyed())
      .subscribe((count) =>
        this.toastService.show(
          count === 1 ? 'New high BP alert' : `${count} new high BP alerts`,
        ),
      );
  }

  protected readonly canSeeClinical = computed(
    () => !!this.auth.user() && this.auth.hasRole('ADMIN', 'CLINICIAN'),
  );
  protected readonly initials = computed(() =>
    (this.auth.user()?.name ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join(''),
  );
  protected readonly roleLabel = computed(() =>
    (this.auth.user()?.roles ?? []).map((role) => ROLE_LABELS[role]).join(', '),
  );

  placeholder(label: string): void {
    this.toastService.show(`${label} is a placeholder in this prototype`);
  }
}
