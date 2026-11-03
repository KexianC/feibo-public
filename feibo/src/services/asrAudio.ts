export const ASR_SAMPLE_RATE = 16_000;
export const ASR_FRAME_SAMPLES = 320;

/**
 * 使用输入样本的 RMS 判断是否存在足以视为讲话的音频活动。
 * 仅使用去直流的音频声能；识别文字不能作为活动证据，否则静音误识别会延长录音。
 */
export const hasVoiceActivity = (
  input: Float32Array,
  threshold = 0.01,
): boolean => {
  if (!Number.isFinite(threshold) || threshold < 0 || threshold > 1) {
    throw new RangeError('语音活动阈值无效');
  }
  if (input.length === 0) return false;

  let sum = 0;
  let sumOfSquares = 0;
  for (let index = 0; index < input.length; index += 1) {
    const sample = Math.max(-1, Math.min(1, input[index]));
    sum += sample;
    sumOfSquares += sample * sample;
  }
  return Math.sqrt(Math.max(0, sumOfSquares / input.length - (sum / input.length) ** 2)) >= threshold;
};

/** 将浏览器输入线性重采样为阿里云实时识别要求的 16kHz PCM16。 */
export const resampleTo16kPcm = (
  input: Float32Array,
  inputSampleRate: number,
): Int16Array => {
  if (input.length === 0) return new Int16Array(0);
  if (!Number.isFinite(inputSampleRate) || inputSampleRate <= 0) {
    throw new RangeError('音频采样率无效');
  }

  const ratio = inputSampleRate / ASR_SAMPLE_RATE;
  const outputLength = Math.max(1, Math.floor(input.length / ratio));
  const output = new Int16Array(outputLength);

  for (let index = 0; index < outputLength; index += 1) {
    const sourceIndex = index * ratio;
    const leftIndex = Math.floor(sourceIndex);
    const rightIndex = Math.min(leftIndex + 1, input.length - 1);
    const fraction = sourceIndex - leftIndex;
    const sample = (1 - fraction) * input[leftIndex] + fraction * input[rightIndex];
    const clamped = Math.max(-1, Math.min(1, sample));
    output[index] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff;
  }

  return output;
};

export interface PackedPcmFrames {
  frames: Uint8Array[];
  remainder: Int16Array;
}

/**
 * 合并跨回调尾帧，并返回拥有独立 ArrayBuffer 的小端 PCM16 二进制帧。
 */
export const packPcm16Frames = (
  previous: Int16Array,
  incoming: Int16Array,
  flush = false,
  frameSamples = ASR_FRAME_SAMPLES,
): PackedPcmFrames => {
  if (!Number.isInteger(frameSamples) || frameSamples <= 0) {
    throw new RangeError('PCM 帧长度无效');
  }

  const combined = new Int16Array(previous.length + incoming.length);
  combined.set(previous, 0);
  combined.set(incoming, previous.length);
  const framedSampleCount = flush
    ? combined.length
    : combined.length - (combined.length % frameSamples);
  const frames: Uint8Array[] = [];

  for (let offset = 0; offset < framedSampleCount; offset += frameSamples) {
    const sampleCount = Math.min(frameSamples, framedSampleCount - offset);
    const frame = new Uint8Array(sampleCount * Int16Array.BYTES_PER_ELEMENT);
    const dataView = new DataView(frame.buffer);
    for (let index = 0; index < sampleCount; index += 1) {
      dataView.setInt16(
        index * Int16Array.BYTES_PER_ELEMENT,
        combined[offset + index],
        true,
      );
    }
    frames.push(frame);
  }

  return {
    frames,
    remainder: flush ? new Int16Array(0) : combined.slice(framedSampleCount),
  };
};
