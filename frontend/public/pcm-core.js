// Pure audio conversion shared by the AudioWorklet (pcm-worklet.js) and the unit tests (WEB-08).
// No browser APIs here, so it runs in the worklet scope and under Node alike.

/** Wire format of /ws/audio (AUD-01): 16 kHz, mono, 16-bit little endian, 100 ms per frame. */
export const TARGET_RATE = 16000;
export const FRAME_SAMPLES = 1600;
export const FRAME_BYTES = FRAME_SAMPLES * 2;

/**
 * Lowers the sample rate by averaging the input samples that fall into each output sample (a box filter, which
 * also keeps most aliasing out of the speech band). Keeps its position between calls, so the 128-sample blocks of a
 * worklet can be fed one after another without clicks at the block borders.
 */
export class Downsampler {
  /**
   * @param {number} inRate sample rate of the input
   * @param {number} outRate sample rate of the output
   */
  constructor(inRate, outRate = TARGET_RATE) {
    if (!(inRate > 0) || !(outRate > 0)) {
      throw new RangeError('Sample rates must be positive');
    }
    this.ratio = inRate / outRate;
    this.sum = 0;
    this.filled = 0;
  }

  /**
   * @param {Float32Array} input
   * @returns {Float32Array} the samples completed by this input
   */
  push(input) {
    const out = [];
    for (let i = 0; i < input.length; i++) {
      let remaining = 1;
      while (remaining > 1e-9) {
        const take = Math.min(this.ratio - this.filled, remaining);
        this.sum += input[i] * take;
        this.filled += take;
        remaining -= take;
        if (this.filled >= this.ratio - 1e-9) {
          out.push(this.sum / this.ratio);
          this.sum = 0;
          this.filled = 0;
        }
      }
    }
    return Float32Array.from(out);
  }
}

/**
 * Float samples in -1..1 to 16-bit little endian PCM. Out-of-range values are clipped.
 * @param {Float32Array} samples
 * @returns {Uint8Array}
 */
export function floatToInt16LE(samples) {
  const bytes = new Uint8Array(samples.length * 2);
  const view = new DataView(bytes.buffer);
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(i * 2, Math.round(s < 0 ? s * 0x8000 : s * 0x7fff), true);
  }
  return bytes;
}

/**
 * Whole path from the microphone: downsample, convert, cut into frames of exactly FRAME_BYTES.
 * The rest that does not fill a frame yet waits for the next call.
 */
export class PcmFramer {
  /** @param {number} inRate sample rate of the audio context */
  constructor(inRate) {
    this.downsampler = new Downsampler(inRate, TARGET_RATE);
    this.pending = new Uint8Array(0);
  }

  /**
   * @param {Float32Array} input
   * @returns {ArrayBuffer[]} zero or more complete frames
   */
  push(input) {
    const bytes = floatToInt16LE(this.downsampler.push(input));
    const all = new Uint8Array(this.pending.length + bytes.length);
    all.set(this.pending);
    all.set(bytes, this.pending.length);
    const frames = [];
    let offset = 0;
    for (; all.length - offset >= FRAME_BYTES; offset += FRAME_BYTES) {
      frames.push(all.slice(offset, offset + FRAME_BYTES).buffer);
    }
    this.pending = all.slice(offset);
    return frames;
  }
}

/**
 * Loudness of a frame of 16-bit little endian PCM as RMS in 0..1 (1 = full scale).
 * @param {ArrayBuffer} frame
 * @returns {number}
 */
export function frameRms(frame) {
  const view = new DataView(frame);
  const count = Math.floor(view.byteLength / 2);
  if (count === 0) {
    return 0;
  }
  let sum = 0;
  for (let i = 0; i < count; i++) {
    const s = view.getInt16(i * 2, true) / 0x8000;
    sum += s * s;
  }
  return Math.sqrt(sum / count);
}
