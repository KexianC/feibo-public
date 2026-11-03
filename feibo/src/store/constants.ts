import type { DigitalHuman, ThemeType } from '@/types';

export const THEME_CONFIG: Record<
  ThemeType,
  {
    name: string;
    backgroundImage: string;
    primaryColor: string;
    accentColor: string;
    isDark: boolean;
  }
> = {
  classic: {
    name: '传统版配色',
    backgroundImage: '/img/headbg.png',
    primaryColor: '#1f2937',
    accentColor: '#fbbf24',
    isDark: false,
  },
  dark: {
    name: '夜间模式',
    backgroundImage: '/img/headbg.png',
    primaryColor: '#111827',
    accentColor: '#3b82f6',
    isDark: true,
  },
};

export const fallbackDigitalHumans: DigitalHuman[] = [
  {
    id: 'fallback-1',
    name: '商务男士',
    avatar:
      'https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt=professional%20businessman%20avatar%20portrait%2C%20clean%20studio%20lighting%2C%20realistic%2C%20white%20background&image_size=square',
    category: 'business',
    modelUrl: '/models/business-man.glb',
  },
  {
    id: 'fallback-2',
    name: '商务女士',
    avatar:
      'https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt=professional%20businesswoman%20avatar%20portrait%2C%20clean%20studio%20lighting%2C%20realistic%2C%20white%20background&image_size=square',
    category: 'business',
    modelUrl: '/models/business-woman.glb',
  },
  {
    id: 'fallback-3',
    name: '教师',
    avatar:
      'https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt=friendly%20teacher%20avatar%20portrait%2C%20soft%20studio%20lighting%2C%20realistic%2C%20white%20background&image_size=square',
    category: 'education',
    modelUrl: '/models/teacher.glb',
  },
];
