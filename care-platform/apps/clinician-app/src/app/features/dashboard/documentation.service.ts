import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  DocumentationRequest,
  DocumentationResponse,
} from './documentation.model';

@Injectable({ providedIn: 'root' })
export class DocumentationService {
  private readonly http = inject(HttpClient);

  get(orderId: string) {
    return this.http.get<DocumentationResponse>(this.url(orderId));
  }

  save(orderId: string, request: DocumentationRequest) {
    return this.http.put<DocumentationResponse>(this.url(orderId), request);
  }

  complete(orderId: string, request: DocumentationRequest) {
    return this.http.post<DocumentationResponse>(
      this.url(orderId) + '/complete',
      request,
    );
  }

  private url(orderId: string): string {
    return (
      '/api/service-requests/' + encodeURIComponent(orderId) + '/documentation'
    );
  }
}
