// AudioWorklet that turns microphone samples into frames of 16 kHz mono Int16 LE PCM, 100 ms each (WEB-08).
// Frames go to the main thread by postMessage (transferred, not copied) and are never kept here (FE-11).
import { PcmFramer } from './pcm-core.js';

class PcmCaptureProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    // `sampleRate` is the rate of the audio context, a global of the worklet scope.
    this.framer = new PcmFramer(sampleRate);
  }

  process(inputs) {
    const channel = inputs[0] && inputs[0][0];
    if (channel) {
      for (const frame of this.framer.push(channel)) {
        this.port.postMessage(frame, [frame]);
      }
    }
    return true;
  }
}

registerProcessor('pcm-capture', PcmCaptureProcessor);
