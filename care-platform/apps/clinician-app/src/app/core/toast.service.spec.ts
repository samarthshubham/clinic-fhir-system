import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should start with no message', () => {
    expect(service.message()).toBeNull();
  });

  it('should show a message and clear it after the default duration', () => {
    service.show('Saved');
    expect(service.message()).toBe('Saved');

    vi.advanceTimersByTime(2499);
    expect(service.message()).toBe('Saved');

    vi.advanceTimersByTime(1);
    expect(service.message()).toBeNull();
  });

  it('should honour a custom duration', () => {
    service.show('Quick', 500);

    vi.advanceTimersByTime(500);

    expect(service.message()).toBeNull();
  });

  it('should restart the timer when a new message replaces the old one', () => {
    service.show('First', 1000);
    vi.advanceTimersByTime(800);

    service.show('Second', 1000);
    vi.advanceTimersByTime(800);
    expect(service.message()).toBe('Second');

    vi.advanceTimersByTime(200);
    expect(service.message()).toBeNull();
  });
});
