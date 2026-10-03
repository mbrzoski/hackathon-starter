import { Injectable } from '@angular/core';

/** Audio capture control for the current call. The real implementation comes with the audio capture task. */
@Injectable({ providedIn: 'root', useFactory: () => new NoopAudioService() })
export abstract class AudioService {
  /** Start capturing after the senior taps "Włącz ochronę" (WEB-07). */
  abstract start(): void;
  /** Stop listening for the rest of this call. */
  abstract pause(): void;
  /** Start listening again. */
  abstract resume(): void;
}

/** Placeholder until audio capture exists: does nothing. */
export class NoopAudioService extends AudioService {
  start(): void {
    // TODO: getUserMedia + AudioWorklet to /ws/audio (WEB-08) once audio capture is implemented.
  }

  pause(): void {
    // TODO: send `pause` on /ws/audio once audio capture is implemented.
  }

  resume(): void {
    // TODO: send `resume` on /ws/audio once audio capture is implemented.
  }
}
