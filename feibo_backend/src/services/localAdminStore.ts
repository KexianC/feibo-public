export interface LocalRecord {
  id: string;
  createTime?: string;
  updateTime?: string;
}

const nowText = () => new Date().toISOString();

const canUseStorage = () => typeof window !== 'undefined' && Boolean(window.localStorage);

export function loadLocalRecords<T extends LocalRecord>(key: string, seed: T[]): T[] {
  if (!canUseStorage()) return seed;

  const raw = window.localStorage.getItem(key);
  if (!raw) {
    window.localStorage.setItem(key, JSON.stringify(seed));
    return seed;
  }

  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : seed;
  } catch {
    return seed;
  }
}

export function saveLocalRecords<T extends LocalRecord>(key: string, records: T[]) {
  if (!canUseStorage()) return;
  window.localStorage.setItem(key, JSON.stringify(records));
}

export function createLocalRecord<T extends LocalRecord>(record: Omit<T, 'id' | 'createTime' | 'updateTime'>): T {
  const time = nowText();
  return {
    ...(record as T),
    id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    createTime: time,
    updateTime: time,
  };
}

export function touchLocalRecord<T extends LocalRecord>(record: T): T {
  return {
    ...record,
    updateTime: nowText(),
  };
}
