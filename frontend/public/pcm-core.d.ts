// Types for pcm-core.js, which lives in public/ because the AudioWorklet loads it as a plain file.
export declare const TARGET_RATE: 16000;
export declare const FRAME_SAMPLES: 1600;
export declare const FRAME_BYTES: 3200;
export declare class Downsampler {
  constructor(inRate: number, outRate?: number);
  push(input: Float32Array): Float32Array;
}
export declare function floatToInt16LE(samples: Float32Array): Uint8Array;
export declare class PcmFramer {
  constructor(inRate: number);
  push(input: Float32Array): ArrayBuffer[];
}
export declare function frameRms(frame: ArrayBuffer): number;
