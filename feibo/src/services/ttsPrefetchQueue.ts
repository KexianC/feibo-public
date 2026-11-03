export type TtsPrefetchTaskState = 'queued' | 'synthesizing' | 'ready' | 'failed';

export type TtsPrefetchResult<T> =
  | { ok: true; value: T }
  | { ok: false; error: unknown };

export interface TtsPrefetchTask<TInput, TOutput> {
  readonly index: number;
  readonly input: TInput;
  state: TtsPrefetchTaskState;
  readonly result: Promise<TtsPrefetchResult<TOutput>>;
}

interface MutableTtsPrefetchTask<TInput, TOutput>
  extends TtsPrefetchTask<TInput, TOutput> {
  start: () => void;
}

/**
 * 有序、有限前瞻的异步预取队列。
 *
 * 队首任务会立即开始；消费队首时再补齐 lookahead 个后续任务，
 * 因此 TTS 可以在播放当前句时只预合成下一句，避免无上限并发请求。
 * 任务结果始终 resolve，防止尚未轮到播放的预取失败形成未处理拒绝。
 */
export class TtsPrefetchQueue<TInput, TOutput> {
  private readonly pending: Array<MutableTtsPrefetchTask<TInput, TOutput>> = [];
  private nextIndex = 1;

  constructor(
    private readonly load: (input: TInput, index: number) => Promise<TOutput>,
    private readonly lookahead = 1,
  ) {
    if (!Number.isInteger(lookahead) || lookahead <= 0) {
      throw new RangeError('TTS 预取数量必须是正整数');
    }
  }

  get length(): number {
    return this.pending.length;
  }

  enqueue(input: TInput): TtsPrefetchTask<TInput, TOutput> {
    const index = this.nextIndex;
    this.nextIndex += 1;

    const task = this.createTask(input, index);
    this.pending.push(task);
    this.fillLookahead();
    return task;
  }

  /** Explicit retry keeps the logical segment identity and the untouched following queue. */
  retry(task: TtsPrefetchTask<TInput, TOutput>): TtsPrefetchTask<TInput, TOutput> {
    const retried = this.createTask(task.input, task.index);
    retried.start();
    return retried;
  }

  private createTask(input: TInput, index: number): MutableTtsPrefetchTask<TInput, TOutput> {

    let start: () => void = () => undefined;
    const task: MutableTtsPrefetchTask<TInput, TOutput> = {
      index,
      input,
      state: 'queued',
      result: new Promise<TtsPrefetchResult<TOutput>>(resolve => {
        start = () => {
          if (task.state !== 'queued') return;
          task.state = 'synthesizing';
          void Promise.resolve()
            .then(() => this.load(task.input, task.index))
            .then(
              value => {
                task.state = 'ready';
                resolve({ ok: true, value });
              },
              error => {
                task.state = 'failed';
                resolve({ ok: false, error });
              },
            );
        };
      }),
      start: () => start(),
    };

    return task;
  }

  enqueueMany(inputs: Iterable<TInput>): void {
    for (const input of inputs) this.enqueue(input);
  }

  take(): TtsPrefetchTask<TInput, TOutput> | null {
    const task = this.pending.shift();
    if (!task) return null;

    task.start();
    this.fillLookahead();
    return task;
  }

  private fillLookahead(): void {
    let prefetched = this.pending.reduce(
      (count, task) => count + (task.state === 'queued' ? 0 : 1),
      0,
    );

    for (const task of this.pending) {
      if (prefetched >= this.lookahead) break;
      if (task.state !== 'queued') continue;
      task.start();
      prefetched += 1;
    }
  }
}
