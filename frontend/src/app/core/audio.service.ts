import { DestroyRef, Injectable, Signal, inject, signal } from '@angular/core';
// The conversion lives in public/ because the AudioWorklet loads it as a plain file; the types are in pcm-core.d.ts.
import { frameRms } from '../../../public/pcm-core.js';
import { AudioError, INSECURE_CONTEXT, NEEDS_TAP, closeError, microphoneError } from './audio-errors';
import { backendLocation } from './backend-origin';
import { WEB_SOCKET_FACTORY } from './events.service';

export type AudioState = 'idle' | 'requesting' | 'listening' | 'paused' | 'error';

/** Audio capture for the current call: microphone to /ws/audio as 16 kHz PCM frames (WEB-08, AUD-01). */
@Injectable({ providedIn: 'root', useFactory: () => new BrowserAudioService() })
export abstract class AudioService {
  abstract readonly state: Signal<AudioState>;
  /** Loudness of the last 100 ms, RMS in 0..1. 0 when not listening. */
  abstract readonly level: Signal<number>;
  /** Set while `state` is 'error'. */
  abstract readonly error: Signal<AudioError | null>;
  /** Ask for the microphone and start listening. Must run from a user gesture (WEB-07). Never rejects. */
  abstract start(): Promise<void>;
  /** Stop listening for the rest of this call: no audio is sent until resume(). */
  abstract pause(): void;
  abstract resume(): void;
  /** End the call and release the microphone. */
  abstract stop(): void;
}

/** How long to wait for the audio context to start by itself. */
const RESUME_WAIT_MS = 1500;

/** Above this the connection is too slow: frames are dropped instead of piling up in memory (FE-11). */
const MAX_BUFFERED_BYTES = 64 * 1024;

/**
 * Real implementation. The microphone goes through an AudioWorklet (public/pcm-worklet.js); the frames it makes are
 * sent as they come and never stored (FE-11). No MediaRecorder (WEB-08).
 */
export class BrowserAudioService extends AudioService {
  private readonly _state = signal<AudioState>('idle');
  private readonly _level = signal(0);
  private readonly _error = signal<AudioError | null>(null);
  readonly state = this._state.asReadonly();
  readonly level = this._level.asReadonly();
  readonly error = this._error.asReadonly();

  private readonly createSocket = inject(WEB_SOCKET_FACTORY);
  private socket: WebSocket | null = null;
  private stream: MediaStream | null = null;
  private context: AudioContext | null = null;
  private node: AudioWorkletNode | null = null;
  /** Bumped by every start and stop, so a start that is overtaken does not finish. */
  private attempt = 0;

  constructor() {
    super();
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  override async start(): Promise<void> {
    if (this._state() === 'requesting' || this._state() === 'listening' || this._state() === 'paused') {
      return;
    }
    this.release();
    const attempt = ++this.attempt;
    this._error.set(null);
    this._state.set('requesting');
    if (!globalThis.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      this.fail(INSECURE_CONTEXT);
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: false, noiseSuppression: true, autoGainControl: true },
      });
      if (attempt !== this.attempt) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      this.stream = stream;
      this.context = new AudioContext();
      // Without a tap the browser may keep the context suspended and resume() never settles: do not wait for it.
      await Promise.race([this.context.resume(), new Promise<void>((done) => setTimeout(done, RESUME_WAIT_MS))]);
      if (attempt !== this.attempt) {
        return;
      }
      if (this.context.state !== 'running') {
        this.fail(NEEDS_TAP);
        return;
      }
      await this.context.audioWorklet.addModule(new URL('pcm-worklet.js', document.baseURI).href);
      if (attempt !== this.attempt) {
        return;
      }
      this.node = new AudioWorkletNode(this.context, 'pcm-capture', { numberOfInputs: 1, numberOfOutputs: 0 });
      this.node.port.onmessage = (e: MessageEvent<ArrayBuffer>) => this.onFrame(e.data);
      this.context.createMediaStreamSource(this.stream).connect(this.node);
      this.openSocket(attempt);
    } catch (error) {
      if (attempt === this.attempt) {
        this.fail(microphoneError(error));
      }
    }
  }

  override pause(): void {
    if (this._state() !== 'listening') {
      return;
    }
    this.control('pause');
    this.setMicrophone(false);
    this._level.set(0);
    this._state.set('paused');
  }

  override resume(): void {
    if (this._state() !== 'paused') {
      return;
    }
    this.setMicrophone(true);
    this.control('resume');
    this._state.set('listening');
  }

  override stop(): void {
    this.attempt++;
    this.control('stop');
    this.release();
    this._error.set(null);
    this._state.set('idle');
  }

  private openSocket(attempt: number): void {
    const backend = backendLocation();
    const scheme = backend.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = this.createSocket(`${scheme}//${backend.host}/ws/audio`);
    socket.binaryType = 'arraybuffer';
    this.socket = socket;
    socket.onopen = () => {
      if (attempt !== this.attempt || this.socket !== socket) {
        return;
      }
      this.control('start');
      this._state.set('listening');
    };
    socket.onclose = (e: CloseEvent) => {
      // Our own stop() already detached this socket.
      if (this.socket === socket) {
        this.fail(closeError(e.code, e.reason));
      }
    };
    socket.onerror = () => undefined; // the close event that follows says what happened
  }

  private onFrame(frame: ArrayBuffer): void {
    const socket = this.socket;
    if (this._state() !== 'listening' || !socket || socket.readyState !== WebSocket.OPEN) {
      return;
    }
    this._level.set(frameRms(frame));
    if (socket.bufferedAmount <= MAX_BUFFERED_BYTES) {
      socket.send(frame);
    }
  }

  private control(type: 'start' | 'stop' | 'pause' | 'resume'): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type }));
    }
  }

  /** A paused microphone gives silence and the browser's recording indicator goes off. */
  private setMicrophone(enabled: boolean): void {
    this.stream?.getAudioTracks().forEach((track) => (track.enabled = enabled));
  }

  private fail(error: AudioError): void {
    this.release();
    this._error.set(error);
    this._state.set('error');
  }

  /** Closes everything that start() opened. Safe to call twice. */
  private release(): void {
    const socket = this.socket;
    this.socket = null; // before close(): its onclose must not look like a failure
    if (socket && socket.readyState <= WebSocket.OPEN) {
      socket.close(1000);
    }
    if (this.node) {
      this.node.port.onmessage = null;
      this.node.disconnect();
      this.node = null;
    }
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
    void this.context?.close().catch(() => undefined);
    this.context = null;
    this._level.set(0);
  }
}
