type MarkdownMode = 'text' | 'fence' | 'thought' | 'inline-code' | 'url' | 'label' | 'after-label' | 'destination' | 'reference';

const URL_PREFIXES = ['https://', 'http://', 'www.'];
const URL_TERMINATOR = /[\s，。！？；：、<>"'）】》\]}]/;

/** Keeps incomplete Markdown constructs out of speech across arbitrary deltas. */
export class StreamingTtsMarkdown {
  private pending = '';
  private mode: MarkdownMode = 'text';
  private marker = '';
  private label = '';
  private depth = 0;
  private atLineStart = true;
  private finished = false;

  push(delta: string): string {
    if (this.finished || !delta) return '';
    this.pending += delta;
    return this.consume(false);
  }

  finish(): string {
    if (this.finished) return '';
    this.finished = true;
    return this.consume(true);
  }

  private consume(final: boolean): string {
    let output = '';
    const emit = (text: string) => {
      output += text;
      if (text) this.atLineStart = /[\r\n]$/.test(text);
    };

    while (this.pending) {
      if (this.mode === 'fence' || this.mode === 'thought') {
        const end = this.mode === 'thought'
          ? this.pending.search(/<\/thought\s*>/i)
          : this.pending.indexOf(this.marker);
        if (end < 0) {
          // Retain only enough suffix to detect a closing delimiter split over deltas.
          this.pending = final ? '' : this.pending.slice(-(this.mode === 'thought' ? 24 : this.marker.length - 1));
          break;
        }
        const closingLength = this.mode === 'thought'
          ? (this.pending.slice(end).match(/^<\/thought\s*>/i)?.[0].length ?? 0)
          : this.marker.length;
        this.pending = this.pending.slice(end + closingLength);
        this.mode = 'text';
        emit('\n');
        continue;
      }

      if (this.mode === 'url') {
        const end = this.pending.search(URL_TERMINATOR);
        if (end < 0) { this.pending = ''; break; }
        this.pending = this.pending.slice(end);
        this.mode = 'text';
        continue;
      }

      if (this.mode === 'label') {
        const character = this.pending[0];
        this.pending = this.pending.slice(1);
        if (character === '[') this.depth += 1;
        if (character === ']') this.depth -= 1;
        if (this.depth === 0) {
          const labelCleaner = new StreamingTtsMarkdown();
          emit(labelCleaner.push(this.label) + labelCleaner.finish());
          this.label = '';
          this.mode = 'after-label';
        } else {
          this.label += character;
        }
        continue;
      }

      if (this.mode === 'after-label') {
        if (this.pending[0] === '(' || this.pending[0] === '[') {
          this.mode = this.pending[0] === '(' ? 'destination' : 'reference';
          this.depth = 1;
          this.pending = this.pending.slice(1);
        } else {
          this.mode = 'text';
        }
        continue;
      }

      if (this.mode === 'destination' || this.mode === 'reference') {
        const character = this.pending[0];
        if (character === '\\' && this.pending.length === 1 && !final) break;
        if (character === '\\') { this.pending = this.pending.slice(2); continue; }
        const opening = this.mode === 'destination' ? '(' : '[';
        const closing = this.mode === 'destination' ? ')' : ']';
        if (character === opening) this.depth += 1;
        if (character === closing) this.depth -= 1;
        this.pending = this.pending.slice(1);
        if (this.depth === 0) this.mode = 'text';
        continue;
      }

      if (this.mode === 'inline-code') {
        const end = this.pending.indexOf(this.marker);
        if (end < 0) {
          if (final) { emit(this.pending.replace(/`/g, '')); this.pending = ''; }
          break;
        }
        emit(this.pending.slice(0, end));
        this.pending = this.pending.slice(end + this.marker.length);
        this.mode = 'text';
        continue;
      }

      if (this.atLineStart) {
        const prefix = this.pending.match(/^[ \t]*(?:#{1,6}[ \t]+|>[ \t]+|[-+*][ \t]+|\d+[.)][ \t]+|\d+、[ \t]*)/);
        if (prefix) { this.pending = this.pending.slice(prefix[0].length); this.atLineStart = false; continue; }
        if (!final && /^[ \t]*(?:#{0,6}|[-+*>]?|\d+[.)]?)$/.test(this.pending)) break;
      }

      if (this.pending.startsWith('```') || this.pending.startsWith('~~~')) {
        this.marker = this.pending.slice(0, 3);
        this.pending = this.pending.slice(3);
        this.mode = 'fence';
        continue;
      }
      if (!final && (/^`{1,2}$/.test(this.pending) || /^~{1,2}$/.test(this.pending))) break;
      if (this.pending[0] === '`') {
        this.marker = this.pending.startsWith('``') ? '``' : '`';
        this.pending = this.pending.slice(this.marker.length);
        this.mode = 'inline-code';
        continue;
      }

      if (this.pending[0] === '<') {
        const end = this.pending.indexOf('>');
        if (end < 0) {
          if (!final && /^<(?:\/?[A-Za-z][^\n]*|)$/.test(this.pending)) break;
          emit('<'); this.pending = this.pending.slice(1); continue;
        }
        const tag = this.pending.slice(0, end + 1);
        if (/^<thought\s*>$/i.test(tag)) this.mode = 'thought';
        if (/^<(?:\/?[A-Za-z]|https?:\/\/)/.test(tag)) {
          this.pending = this.pending.slice(end + 1);
          continue;
        }
      }

      const lower = this.pending.toLowerCase();
      const urlPrefix = URL_PREFIXES.find(prefix => lower.startsWith(prefix));
      if (urlPrefix) {
        this.pending = this.pending.slice(urlPrefix.length);
        this.mode = 'url';
        continue;
      }
      if (!final && URL_PREFIXES.some(prefix => prefix.startsWith(lower))) break;

      if (this.pending.startsWith('![') || this.pending[0] === '[') {
        this.pending = this.pending.slice(this.pending[0] === '!' ? 2 : 1);
        this.mode = 'label';
        this.depth = 1;
        continue;
      }
      if (this.pending === '!' && !final) break;

      if (this.pending[0] === '&') {
        const entity = this.pending.match(/^&(nbsp|amp|lt|gt|quot|apos|#39);/i);
        if (entity) {
          const decoded: Record<string, string> = { nbsp: ' ', amp: '和', lt: '小于', gt: '大于', quot: '', apos: '', '#39': '' };
          emit(decoded[entity[1].toLowerCase()]);
          this.pending = this.pending.slice(entity[0].length);
          continue;
        }
        if (!final && /^&[a-z#0-9]*$/i.test(this.pending)) break;
      }

      const character = this.pending[0];
      if (!final && this.pending.length === 1 && /[\uD800-\uDBFF]/.test(character)) break;
      if (character === '\\' && this.pending.length === 1 && !final) break;
      if (character === '\\' && this.pending.length > 1) {
        emit(this.pending[1]); this.pending = this.pending.slice(2); continue;
      }
      this.pending = this.pending.slice(1);
      if (/[*_~]/.test(character)) continue;
      emit(character === '|' ? '，' : character);
    }

    if (final && this.mode === 'label' && this.label) {
      // An unfinished visible label is useful text; an unfinished destination is not.
      const cleaner = new StreamingTtsMarkdown();
      output += cleaner.push(this.label) + cleaner.finish();
      this.label = '';
    }
    return output;
  }
}
