import { TestBed } from '@angular/core/testing';
import { AUDIO_CONTEXT_FACTORY, AlertNotifier } from './alert-notifier';

describe('AlertNotifier', () => {
  let started: number;
  let created: number;

  beforeEach(() => {
    started = 0;
    created = 0;
    const node = () => ({ connect: (n: unknown) => n, start: () => started++, stop: () => undefined });
    const fakeContext = {
      currentTime: 0,
      destination: {},
      resume: async () => undefined,
      createOscillator: () => ({ ...node(), frequency: { value: 0 } }),
      createGain: () => ({ ...node(), gain: { setValueAtTime: () => undefined, exponentialRampToValueAtTime: () => undefined } }),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: AUDIO_CONTEXT_FACTORY, useValue: () => (created++, fakeContext as unknown as AudioContext) }],
    });
  });

  it('stays silent until the person interacts with the page', () => {
    const notifier = TestBed.inject(AlertNotifier);
    notifier.beep();
    expect(created).toBe(0);
    expect(started).toBe(0);

    document.dispatchEvent(new Event('pointerdown'));
    notifier.beep();
    expect(created).toBe(1);
    expect(started).toBe(3);
  });

  it('does not throw where there is no audio', () => {
    TestBed.overrideProvider(AUDIO_CONTEXT_FACTORY, { useValue: () => null });
    const notifier = TestBed.inject(AlertNotifier);
    document.dispatchEvent(new Event('keydown'));
    expect(() => notifier.beep()).not.toThrow();
    expect(notifier.unlocked).toBe(false);
  });
});
