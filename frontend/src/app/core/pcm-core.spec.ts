import { Downsampler, FRAME_BYTES, PcmFramer, floatToInt16LE, frameRms } from '../../../public/pcm-core.js';

const int16 = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  return Array.from({ length: bytes.length / 2 }, (_, i) => view.getInt16(i * 2, true));
};

describe('Downsampler', () => {
  it('averages groups of three samples for 48 kHz to 16 kHz', () => {
    const out = new Downsampler(48000).push(Float32Array.from([0.3, 0.6, 0.9, -0.3, -0.6, -0.9]));
    expect(out.length).toBe(2);
    expect(out[0]).toBeCloseTo(0.6);
    expect(out[1]).toBeCloseTo(-0.6);
  });

  it('keeps its position between blocks: block borders do not change the result', () => {
    const input = Float32Array.from({ length: 480 }, (_, i) => Math.sin(i / 7));
    const whole = new Downsampler(44100).push(input);
    const parts = new Downsampler(44100);
    const pieces = [parts.push(input.subarray(0, 128)), parts.push(input.subarray(128, 301)), parts.push(input.subarray(301))];
    const joined = Float32Array.from(pieces.flatMap((p) => Array.from(p)));
    expect(joined.length).toBe(whole.length);
    joined.forEach((v, i) => expect(v).toBeCloseTo(whole[i], 5));
  });

  it('produces one output sample per 2.75 input samples at 44.1 kHz', () => {
    const out = new Downsampler(44100).push(new Float32Array(44100));
    expect(out.length).toBeGreaterThanOrEqual(15999);
    expect(out.length).toBeLessThanOrEqual(16000);
  });

  it('passes 16 kHz through unchanged', () => {
    const input = Float32Array.from([0.1, -0.2, 0.3]);
    expect(Array.from(new Downsampler(16000).push(input))).toEqual(Array.from(input));
  });

  it('rejects a rate that is not positive', () => {
    expect(() => new Downsampler(0)).toThrow(RangeError);
  });
});

describe('floatToInt16LE', () => {
  it('writes little endian 16-bit samples', () => {
    const bytes = floatToInt16LE(Float32Array.from([0, 1, -1, 0.5]));
    expect(Array.from(bytes.subarray(0, 2))).toEqual([0, 0]);
    expect(Array.from(bytes.subarray(2, 4))).toEqual([0xff, 0x7f]); // 32767
    expect(Array.from(bytes.subarray(4, 6))).toEqual([0x00, 0x80]); // -32768
    expect(int16(bytes)[3]).toBe(16384);
  });

  it('clips values outside -1..1', () => {
    expect(int16(floatToInt16LE(Float32Array.from([2.5, -3])))).toEqual([32767, -32768]);
  });
});

describe('PcmFramer', () => {
  it('cuts frames of exactly 3200 bytes (1600 samples, 100 ms)', () => {
    const framer = new PcmFramer(16000);
    const frames = framer.push(new Float32Array(1600 * 2 + 100));
    expect(frames.length).toBe(2);
    frames.forEach((f) => expect(f.byteLength).toBe(FRAME_BYTES));
  });

  it('holds the rest back and completes the frame with the next block', () => {
    const framer = new PcmFramer(16000);
    expect(framer.push(new Float32Array(1000)).length).toBe(0);
    expect(framer.push(new Float32Array(599)).length).toBe(0);
    expect(framer.push(new Float32Array(1)).length).toBe(1);
  });

  it('turns 128-sample blocks of a 48 kHz context into 100 ms frames', () => {
    const framer = new PcmFramer(48000);
    let frames = 0;
    // 100 ms at 48 kHz is 4800 samples = 37.5 blocks; 75 blocks (200 ms) must give 2 frames.
    for (let i = 0; i < 75; i++) {
      frames += framer.push(new Float32Array(128)).length;
    }
    expect(frames).toBe(2);
  });

  it('keeps the samples in order across frames', () => {
    const framer = new PcmFramer(16000);
    const input = Float32Array.from({ length: 3200 }, (_, i) => (i % 100) / 200);
    const [a, b] = framer.push(input);
    const samples = [...int16(new Uint8Array(a)), ...int16(new Uint8Array(b))];
    expect(samples.length).toBe(3200);
    expect(samples[50]).toBe(Math.round(0.25 * 0x7fff));
    expect(samples[1650]).toBe(Math.round(0.25 * 0x7fff));
  });
});

describe('frameRms', () => {
  it('is 0 for silence and about 1 for a full-scale square wave', () => {
    expect(frameRms(new ArrayBuffer(3200))).toBe(0);
    const loud = floatToInt16LE(Float32Array.from({ length: 1600 }, (_, i) => (i % 2 ? 1 : -1)));
    expect(frameRms(loud.buffer as ArrayBuffer)).toBeGreaterThan(0.99);
    expect(frameRms(loud.buffer as ArrayBuffer)).toBeLessThanOrEqual(1);
  });

  it('is 0 for an empty frame', () => {
    expect(frameRms(new ArrayBuffer(0))).toBe(0);
  });
});
