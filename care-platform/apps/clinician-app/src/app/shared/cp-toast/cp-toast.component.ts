import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '@core/toast.service';

@Component({
  selector: 'cp-toast',
  templateUrl: './cp-toast.component.html',
  styleUrl: './cp-toast.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpToastComponent {
  protected readonly toast = inject(ToastService);
}
