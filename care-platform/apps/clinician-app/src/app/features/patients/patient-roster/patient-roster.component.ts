import {
  Component,
  AfterViewInit,
  ViewChild,
  ChangeDetectionStrategy,
  signal,
  computed,
  inject,
  DestroyRef,
} from '@angular/core';
import { TitleCasePipe, DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  Subject,
  combineLatest,
  of,
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs';
import {
  takeUntilDestroyed,
  toObservable,
  toSignal,
} from '@angular/core/rxjs-interop';
import { PatientService } from '@features/patients/services/patient.service';
import { PatientListItem } from '@features/patients/models/patient-list-item.model';

@Component({
  selector: 'app-patient-roster',
  standalone: true,
  imports: [
    TitleCasePipe,
    DatePipe,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
  ],
  templateUrl: './patient-roster.component.html',
  styleUrl: './patient-roster.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientRosterComponent implements AfterViewInit {
  private readonly svc = inject(PatientService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly displayedColumns = [
    'name',
    'gender',
    'dob',
    'lastEncounter',
    'status',
    'actions',
  ];
  readonly dataSource = new MatTableDataSource<PatientListItem>([]);

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;

  readonly searchTerm = signal('');

  private readonly reload$ = new Subject<void>();

  private readonly rawPatients$ = this.reload$.pipe(
    startWith(undefined),
    switchMap(() =>
      this.svc.getPatients().pipe(
        catchError(() => of('error' as const)),
        startWith(null),
      ),
    ),
    shareReplay(1),
  );

  private readonly search$ = toObservable(this.searchTerm).pipe(
    debounceTime(250),
    distinctUntilChanged(),
    startWith(''),
  );

  private readonly filtered$ = combineLatest([
    this.rawPatients$,
    this.search$,
  ]).pipe(
    map(([patients, term]) => {
      if (!Array.isArray(patients)) return patients;
      if (!term) return patients;
      const q = term.toLowerCase();
      return patients.filter(
        (p) => p.name.toLowerCase().includes(q) || p.dob.includes(q),
      );
    }),
  );

  readonly filtered = toSignal(this.filtered$, {
    initialValue: null as PatientListItem[] | null | 'error',
  });
  readonly isLoading = computed(() => this.filtered() === null);
  readonly hasError = computed(() => this.filtered() === 'error');

  readonly totalCount = toSignal(
    this.rawPatients$.pipe(map((p) => (Array.isArray(p) ? p.length : 0))),
    { initialValue: 0 },
  );

  readonly filteredCount = computed(() => {
    const f = this.filtered();
    return Array.isArray(f) ? f.length : 0;
  });

  constructor() {
    this.filtered$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        if (Array.isArray(result)) {
          this.dataSource.data = result;
          this.dataSource.paginator?.firstPage();
        }
      });
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  reload(): void {
    this.reload$.next();
  }

  setSearch(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value.trim());
  }

  clearSearch(input: HTMLInputElement): void {
    input.value = '';
    this.searchTerm.set('');
  }

  viewPatient(id: string): void {
    this.router.navigate(['/patients', id]);
  }

  initials(name: string): string {
    return name
      .split(/\s+/)
      .slice(0, 2)
      .map((s) => s[0])
      .join('')
      .toUpperCase();
  }

  onNewPatient(): void {
    // placeholder — no form yet
  }
}
