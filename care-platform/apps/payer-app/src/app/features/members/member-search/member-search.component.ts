import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ViewChild,
  computed,
  inject,
} from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { ReactiveFormsModule, FormControl, FormGroup } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  Subject,
  catchError,
  combineLatest,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs';
import { EmptyStateComponent } from '@shared/empty-state/empty-state.component';
import { MemberService } from '@features/members/member.service';
import {
  MemberCriteria,
  MemberSummary,
  coverageTone,
  displayName,
} from '@features/members/member.models';

export interface MemberRow extends MemberSummary {
  name: string;
}

type SearchResult = { rows: MemberRow[]; total: number } | 'error' | null;

const sameCriteria = (a: MemberCriteria, b: MemberCriteria) =>
  a.name === b.name && a.memberId === b.memberId;

@Component({
  selector: 'cp-member-search',
  imports: [
    DatePipe,
    TitleCasePipe,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    EmptyStateComponent,
  ],
  templateUrl: './member-search.component.html',
  styleUrl: './member-search.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemberSearchComponent implements AfterViewInit {
  private readonly members = inject(MemberService);

  readonly displayedColumns = [
    'memberId',
    'name',
    'dateOfBirth',
    'planName',
    'coverageStatus',
  ];
  readonly dataSource = new MatTableDataSource<MemberRow>([]);
  readonly coverageTone = coverageTone;

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;

  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true }),
    memberId: new FormControl('', { nonNullable: true }),
  });

  private readonly reload$ = new Subject<void>();

  private readonly criteria$ = this.form.valueChanges.pipe(
    debounceTime(250),
    startWith(this.form.getRawValue()),
    map(
      (): MemberCriteria => ({
        name: this.form.controls.name.value.trim(),
        memberId: this.form.controls.memberId.value.trim(),
      }),
    ),
    distinctUntilChanged(sameCriteria),
  );

  private readonly result$ = combineLatest([
    this.criteria$,
    this.reload$.pipe(startWith(undefined)),
  ]).pipe(
    switchMap(([criteria]) =>
      this.members.search(criteria).pipe(
        map(
          (page): SearchResult => ({
            rows: page.items.map((member) => ({
              ...member,
              name: displayName(member),
            })),
            total: page.total,
          }),
        ),
        catchError(() => of('error' as const)),
        startWith(null),
      ),
    ),
    shareReplay(1),
  );

  readonly result = toSignal(this.result$, {
    initialValue: null as SearchResult,
  });
  readonly isLoading = computed(() => this.result() === null);
  readonly hasError = computed(() => this.result() === 'error');
  readonly isEmpty = computed(() => {
    const result = this.result();
    return result !== null && result !== 'error' && result.rows.length === 0;
  });
  readonly showTable = computed(
    () => !this.isLoading() && !this.hasError() && !this.isEmpty(),
  );
  readonly total = computed(() => {
    const result = this.result();
    return result && result !== 'error' ? result.total : 0;
  });

  constructor() {
    this.result$.pipe(takeUntilDestroyed()).subscribe((result) => {
      if (result && result !== 'error') {
        this.dataSource.data = result.rows;
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

  clear(): void {
    this.form.reset();
  }
}
