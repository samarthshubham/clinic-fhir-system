import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal<string | null>(null, { equal: () => false });

  private timer: ReturnType<typeof setTimeout> | null = null;

  show(text: string, duration = 2500): void {
    if (this.timer) clearTimeout(this.timer);
    this.message.set(text);
    this.timer = setTimeout(() => this.message.set(null), duration);
  }
}
