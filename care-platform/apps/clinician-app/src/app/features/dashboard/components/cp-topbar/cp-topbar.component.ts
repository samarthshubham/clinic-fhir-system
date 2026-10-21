import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ThemeService } from '@core/theme.service';
import { ToastService } from '@core/toast.service';

@Component({
  selector: 'cp-topbar',
  imports: [RouterLink, RouterLinkActive, MatIconModule],
  templateUrl: './cp-topbar.component.html',
  styleUrl: './cp-topbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpTopbarComponent {
  protected readonly theme = inject(ThemeService);
  private readonly toastService = inject(ToastService);

  placeholder(label: string): void {
    this.toastService.show(`${label} is a placeholder in this prototype`);
  }
}
