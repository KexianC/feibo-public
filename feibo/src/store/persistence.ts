import type { ThemeType } from '@/types';

export function saveToStorage<T>(key: string, data: T) {
  try {
    localStorage.setItem(key, JSON.stringify(data));
  } catch (error) {
    console.error('保存到本地存储失败:', error);
  }
}

export function loadFromStorage<T>(key: string) {
  try {
    const data = localStorage.getItem(key);
    return data ? (JSON.parse(data) as T) : null;
  } catch (error) {
    console.error('从本地存储加载失败:', error);
    return null;
  }
}

export function loadSavedTheme(): ThemeType {
  const savedTheme = loadFromStorage<ThemeType>('app-theme');
  return savedTheme === 'dark' ? savedTheme : 'classic';
}

export function saveTheme(theme: ThemeType) {
  saveToStorage('app-theme', theme);
}
