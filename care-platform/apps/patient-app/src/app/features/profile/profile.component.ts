import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { AuthService } from '@care-platform/auth';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { catchError, of, startWith } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { PatientAppService } from '../../shared/services/patient-app.service';
import { PatientSelectService } from '../../shared/services/patient-select.service';
import { ThemeService } from '../../theme.service';
import { CpAvatarComponent } from '../../shared/components/cp-avatar/cp-avatar.component';

@Component({
  selector: 'cp-profile',
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatSlideToggleModule, CpAvatarComponent],
})
export class ProfileComponent {
  private readonly svc = inject(PatientAppService);
  private readonly selectSvc = inject(PatientSelectService);
  private readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);

  private readonly patientId = this.selectSvc.selectedId()!;

  readonly dashboard = toSignal(
    this.svc.getDashboard(this.patientId).pipe(
      catchError(() => of(null)),
      startWith(null),
    ),
    { initialValue: null },
  );

  readonly patient = computed(() => this.dashboard()?.patient ?? null);

  readonly initials = computed(() => {
    const p = this.patient();
    if (!p) return '?';
    return `${p.firstName[0] ?? ''}${p.lastName[0] ?? ''}`.toUpperCase();
  });

  readonly displayName = computed(() => {
    const p = this.patient();
    return p ? `${p.firstName} ${p.lastName}` : '—';
  });

  readonly age = computed(() => {
    const p = this.patient();
    if (!p?.dob) return null;
    const birth = new Date(p.dob);
    const today = new Date();
    let age = today.getFullYear() - birth.getFullYear();
    const m = today.getMonth() - birth.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--;
    return age;
  });

  readonly pronouns = computed(() => {
    const g = this.patient()?.gender?.toLowerCase();
    if (g === 'male') return 'he/him';
    if (g === 'female') return 'she/her';
    return 'they/them';
  });

  readonly formattedDob = computed(() => {
    const p = this.patient();
    if (!p?.dob) return '—';
    return new Date(p.dob).toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    });
  });

  readonly formattedGender = computed(() => {
    const g = this.patient()?.gender;
    if (!g) return '—';
    return g.charAt(0).toUpperCase() + g.slice(1);
  });

  readonly avatarHue = computed(() => {
    const id = this.patientId;
    let hash = 0;
    for (let i = 0; i < id.length; i++) {
      hash = ((hash << 5) - hash + id.charCodeAt(i)) | 0;
    }
    return ((hash % 360) + 360) % 360;
  });

  get isDark(): boolean {
    return this.theme.isDark();
  }

  toggleTheme(checked: boolean): void {
    if (checked !== this.theme.isDark()) {
      this.theme.toggle();
    }
  }

  signOut(): void {
    this.auth.logout();
  }
}
