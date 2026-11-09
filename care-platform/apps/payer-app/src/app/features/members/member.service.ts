import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  MemberCriteria,
  MemberResponse,
  MemberSummary,
  PageResponse,
} from './member.models';

@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);

  search(criteria: MemberCriteria): Observable<PageResponse<MemberSummary>> {
    let params = new HttpParams();
    if (criteria.name) params = params.set('name', criteria.name);
    if (criteria.memberId) params = params.set('memberId', criteria.memberId);
    return this.http.get<PageResponse<MemberSummary>>('/api/members', {
      params,
    });
  }

  getMember(id: string): Observable<MemberResponse> {
    return this.http.get<MemberResponse>(
      `/api/members/${encodeURIComponent(id)}`,
    );
  }
}
