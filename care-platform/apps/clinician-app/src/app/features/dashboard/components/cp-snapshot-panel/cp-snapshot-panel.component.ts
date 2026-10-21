import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { DatePipe, UpperCasePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { CpVitalCellComponent } from '@features/dashboard/components/cp-vital-cell/cp-vital-cell.component';
import { SnapshotResponse } from '@features/dashboard/dashboard.model';

@Component({
  selector: 'cp-snapshot-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, UpperCasePipe, MatIconModule, CpVitalCellComponent],
  templateUrl: './cp-snapshot-panel.component.html',
  styleUrl: './cp-snapshot-panel.component.scss',
})
export class CpSnapshotPanelComponent {
  @Input({ required: true }) snapshot!: SnapshotResponse;

  private readonly today = new Date();

  get bpValue(): string {
    const s = this.snapshot.latestVitals?.systolicBp?.value;
    const d = this.snapshot.latestVitals?.diastolicBp?.value;
    return s != null && d != null ? `${Math.round(s)}/${Math.round(d)}` : '–';
  }

  get bpFlagged(): boolean {
    const v = this.snapshot.latestVitals;
    return (v?.systolicBp?.flagged || v?.diastolicBp?.flagged) ?? false;
  }

  get weightValue(): string {
    const w = this.snapshot.latestVitals?.weightKg?.value;
    return w != null ? w.toString() : '–';
  }

  get spo2Value(): string {
    const s = this.snapshot.latestVitals?.spo2Percent?.value;
    return s != null ? s.toString() : '–';
  }

  get spo2Flagged(): boolean {
    return this.snapshot.latestVitals?.spo2Percent?.flagged ?? false;
  }

  get hrValue(): string {
    const h = this.snapshot.latestVitals?.heartRate?.value;
    return h != null ? Math.round(h).toString() : '–';
  }

  get hrFlagged(): boolean {
    return this.snapshot.latestVitals?.heartRate?.flagged ?? false;
  }

  get tempValue(): string {
    const t = this.snapshot.latestVitals?.temperature?.value;
    return t != null ? t.toFixed(1) : '–';
  }

  get tempFlagged(): boolean {
    return this.snapshot.latestVitals?.temperature?.flagged ?? false;
  }

  get latestVitalsDate(): string {
    const v = this.snapshot.latestVitals;
    const raw = v?.systolicBp?.date ?? v?.weightKg?.date ?? v?.heartRate?.date;
    if (!raw) return '–';
    return new DatePipe('en-US').transform(raw, 'MMM d') ?? '–';
  }

  get daysAgoLabel(): string {
    const v = this.snapshot.latestVitals;
    const raw = v?.systolicBp?.date ?? v?.weightKg?.date ?? v?.heartRate?.date;
    if (!raw) return '';
    const diff = Math.floor(
      (this.today.getTime() - new Date(raw).getTime()) / 86_400_000,
    );
    return diff === 0 ? 'today' : `${diff}d ago`;
  }
}
