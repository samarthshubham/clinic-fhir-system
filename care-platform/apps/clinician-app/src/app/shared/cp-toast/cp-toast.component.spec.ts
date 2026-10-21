import { TestBed } from '@angular/core/testing';
import { CpToastComponent } from './cp-toast.component';
import { ToastService } from '../../toast.service';

describe('CpToastComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CpToastComponent],
    }).compileComponents();
  });

  it('should render nothing when there is no message', async () => {
    const fixture = TestBed.createComponent(CpToastComponent);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.cp-toast')).toBeNull();
  });

  it('should render the current toast message', async () => {
    const fixture = TestBed.createComponent(CpToastComponent);
    TestBed.inject(ToastService).message.set('Saved');
    await fixture.whenStable();

    const toast = fixture.nativeElement.querySelector('.cp-toast');
    expect(toast.textContent.trim()).toBe('Saved');
    expect(toast.getAttribute('role')).toBe('status');
  });
});
