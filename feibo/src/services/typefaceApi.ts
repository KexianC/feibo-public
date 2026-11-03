export interface TypefaceItem {
  label: string;
  value: string;
}

/**
 * 获取字体选项
 * 仅保留思源黑体与宋体两种可选字体
 */
export const getTypefaceOptions = (): TypefaceItem[] => [
  { label: '思源黑体', value: 'Source Han Sans SC Medium' },
  { label: '宋体', value: 'STSong' }
];
