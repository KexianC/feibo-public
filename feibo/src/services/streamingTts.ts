export interface StreamingTtsChunkerOptions {
  firstSegmentMinimumLength?: number;
  minimumSegmentLength?: number;
  maximumSegmentLength?: number;
}

const DEFAULT_FIRST_SEGMENT_MINIMUM_LENGTH = 8;
const DEFAULT_MINIMUM_SEGMENT_LENGTH = 12;
const DEFAULT_MAXIMUM_SEGMENT_LENGTH = 120;
const STRONG_PAUSES = new Set(['。', '！', '？', '；', '!', '?', ';']);
const CLOSING_PUNCTUATION = new Set(['”', '’', '"', "'", '）', ')', '】', ']', '》', '〉', '」', '』']);

const isDigit = (value: string | undefined): boolean => Boolean(value && /\d/.test(value));

const findSafeSliceEnd = (text: string, maximumLength: number): number => {
  let end = Math.min(maximumLength, text.length);
  if (
    end < text.length
    && end > 0
    && /[\uD800-\uDBFF]/.test(text.charAt(end - 1))
    && /[\uDC00-\uDFFF]/.test(text.charAt(end))
  ) {
    end -= 1;
  }
  return end;
};

const includeClosingPunctuation = (text: string, start: number): number => {
  let end = start;
  while (end < text.length && CLOSING_PUNCTUATION.has(text.charAt(end))) {
    end += 1;
  }
  return end;
};

/**
 * 将流式文本增量收集为适合送入 TTS 的稳定语句。
 *
 * 句末标点到达后才产出，英文句点会等待一个字符的前瞻，避免把 37.5
 * 之类的小数拆开；无标点长段会在安全长度处回退切分。
 */
export class StreamingTtsChunker {
  private pending = '';
  private emittedAny = false;
  private finished = false;
  private readonly firstSegmentMinimumLength: number;
  private readonly minimumSegmentLength: number;
  private readonly maximumSegmentLength: number;

  constructor(options: StreamingTtsChunkerOptions = {}) {
    this.firstSegmentMinimumLength = options.firstSegmentMinimumLength
      ?? DEFAULT_FIRST_SEGMENT_MINIMUM_LENGTH;
    this.minimumSegmentLength = options.minimumSegmentLength
      ?? DEFAULT_MINIMUM_SEGMENT_LENGTH;
    this.maximumSegmentLength = options.maximumSegmentLength
      ?? DEFAULT_MAXIMUM_SEGMENT_LENGTH;

    if (
      this.firstSegmentMinimumLength <= 0
      || this.minimumSegmentLength <= 0
      || this.maximumSegmentLength < this.minimumSegmentLength
    ) {
      throw new RangeError('流式语音分段配置无效');
    }
  }

  push(delta: string): string[] {
    if (this.finished || !delta) return [];
    this.pending += delta;
    return this.extract(false);
  }

  finish(): string[] {
    if (this.finished) return [];
    this.finished = true;
    return this.extract(true);
  }

  get hasPendingText(): boolean {
    return this.pending.trim().length > 0;
  }

  /** Flush a delayed phrase without cutting a trailing number, unit or English word. */
  flushPending(): string[] {
    if (this.finished) return [];
    const minimumLength = this.emittedAny ? this.minimumSegmentLength : this.firstSegmentMinimumLength;
    const tail = this.pending.match(/[A-Za-z0-9.+/%°℃µμ-]+$/)?.[0] ?? '';
    let end = this.pending.length - tail.length;
    for (let index = end - 1; index >= minimumLength; index -= 1) {
      if (/[，,、：:\s]/.test(this.pending[index])) { end = index + 1; break; }
    }
    if (end < minimumLength) return [];
    end = findSafeSliceEnd(this.pending, end);
    const segment = this.pending.slice(0, end).trim();
    this.pending = this.pending.slice(end);
    if (!segment) return [];
    this.emittedAny = true;
    return [segment];
  }

  private extract(flushTail: boolean): string[] {
    const result: string[] = [];

    while (this.pending) {
      const minimumLength = this.emittedAny
        ? this.minimumSegmentLength
        : this.firstSegmentMinimumLength;
      const boundary = this.findBoundary(minimumLength, flushTail);

      if (boundary <= 0) break;
      const segment = this.pending.slice(0, boundary).trim();
      this.pending = this.pending.slice(boundary);
      if (segment) {
        result.push(segment);
        this.emittedAny = true;
      }
    }

    if (flushTail) {
      const tail = this.pending.trim();
      this.pending = '';
      if (tail) {
        result.push(tail);
        this.emittedAny = true;
      }
    }

    return result;
  }

  private findBoundary(minimumLength: number, flushTail: boolean): number {
    const scanEnd = Math.min(this.pending.length, this.maximumSegmentLength);

    for (let index = 0; index < scanEnd; index += 1) {
      const character = this.pending.charAt(index);
      let boundary = 0;

      if (STRONG_PAUSES.has(character)) {
        // Markdown 图片以 ![ 开始，不把这个感叹号误判成句末。
        if (character === '!' && this.pending.charAt(index + 1) === '[') continue;
        boundary = includeClosingPunctuation(this.pending, index + 1);
      } else if (character === '.') {
        const previous = this.pending.charAt(index - 1);
        const next = this.pending.charAt(index + 1);
        if (isDigit(previous) && isDigit(next)) continue;
        // 流还没结束时保留末尾句点，等待下一字符确认它不是小数点。
        if (!next && !flushTail) continue;
        if (!next || /\s/.test(next) || !/[A-Za-z0-9]/.test(next)) {
          boundary = includeClosingPunctuation(this.pending, index + 1);
        }
      } else if (character === '\n') {
        const isParagraphBreak = this.pending.charAt(index + 1) === '\n';
        if (isParagraphBreak || index + 1 >= minimumLength) {
          boundary = isParagraphBreak ? index + 2 : index + 1;
        }
      }

      if (boundary >= minimumLength) return boundary;
    }

    if (this.pending.length < this.maximumSegmentLength) return 0;

    const safeEnd = findSafeSliceEnd(this.pending, this.maximumSegmentLength);
    const softMinimum = Math.max(minimumLength, Math.floor(this.maximumSegmentLength * 0.6));
    for (let index = safeEnd - 1; index >= softMinimum; index -= 1) {
      if (/[，,、：:\s]/.test(this.pending.charAt(index))) return index + 1;
    }
    return safeEnd;
  }
}
