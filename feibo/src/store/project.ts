import { v4 as uuidv4 } from 'uuid';
import type { PageSubtitleConfig, Project } from '@/types';
import { STORAGE_KEYS } from '@/types';
import { loadFromStorage, saveToStorage } from '@/store/persistence';

const defaultSubtitleConfig: PageSubtitleConfig = {
  isEnabled: false,
  contentType: 'subtitles',
  primaryColour: '#000000',
  fontname: 'STSONG',
  bold: '0',
  subtitleUsepx: 'Y',
  fontsize: '35',
  underline: 0,
  italic: 0,
  bottomSpace: 10,
  wordSpace: 0,
};

export function createDefaultProject(): Project {
  const pageId = uuidv4();

  return {
    id: uuidv4(),
    name: '新项目',
    pages: [
      {
        id: pageId,
        name: '页面 1',
        elements: [],
        duration: 5000,
        script: '欢迎使用数字人视频合成平台',
        aspectRatio: '16:9',
        subtitleConfig: defaultSubtitleConfig,
        voiceId: undefined,
        voiceSpeed: 1,
        voiceVolume: 100,
      },
    ],
    currentPageId: pageId,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

export function createDefaultSubtitleConfig(): PageSubtitleConfig {
  return { ...defaultSubtitleConfig };
}

export function deepClone<T>(value: T): T {
  if (value === null || typeof value !== 'object') {
    return value;
  }

  if (value instanceof Date) {
    return new Date(value.getTime()) as T;
  }

  if (Array.isArray(value)) {
    return value.map((item) => deepClone(item)) as T;
  }

  const clonedObject = {} as Record<string, unknown>;

  for (const key in value as Record<string, unknown>) {
    if (Object.prototype.hasOwnProperty.call(value, key)) {
      clonedObject[key] = deepClone((value as Record<string, unknown>)[key]);
    }
  }

  return clonedObject as T;
}

export function autoSaveProject(project: Project) {
  saveToStorage(STORAGE_KEYS.CURRENT_PROJECT, project);
  saveToStorage(STORAGE_KEYS.PROJECTS, [project]);
}

export function loadSavedProject() {
  const savedProject = loadFromStorage<Project>(STORAGE_KEYS.CURRENT_PROJECT);
  if (!savedProject) {
    return null;
  }

  savedProject.createdAt = new Date(savedProject.createdAt);
  savedProject.updatedAt = new Date(savedProject.updatedAt);

  return savedProject;
}
