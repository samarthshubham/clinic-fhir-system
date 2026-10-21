import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
} from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { PatientHeaderData } from '../../dashboard.model';
import { ToastService } from '@core/toast.service';

@Component({
  selector: 'cp-patient-header',
  imports: [DatePipe, TitleCasePipe],
  templateUrl: './cp-patient-header.component.html',
  styleUrl: './cp-patient-header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpPatientHeaderComponent {
  @Input({ required: true }) data!: PatientHeaderData;

  private readonly toastService = inject(ToastService);

  get initials(): string {
    const parts = this.data.displayName.split(' ');
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
  }

  placeholder(label: string): void {
    this.toastService.show(`${label} is a placeholder in this prototype`);
  }
}
