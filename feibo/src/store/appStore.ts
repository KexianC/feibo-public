import { create } from 'zustand';
import type { AppState } from '@/types';
import { fallbackDigitalHumans } from '@/store/constants';
import { createEditorActions } from '@/store/actions/editorActions';
import { createLibraryActions } from '@/store/actions/libraryActions';
import { createResourceActions } from '@/store/actions/resourceActions';
import { loadSavedTheme, loadFromStorage } from '@/store/persistence';
import { createDefaultProject, loadSavedProject } from '@/store/project';
import { STORAGE_KEYS } from '@/types';

const initialProject = loadSavedProject() || createDefaultProject();
const initialTheme = loadSavedTheme();

export const useStore = create<AppState>((set, get) => ({
  currentProject: initialProject,
  selectedElementId: null,
  isPlaying: false,
  currentTime: 0,
  currentTheme: initialTheme,
  digitalHumans: fallbackDigitalHumans,
  selectedCategory: 'all',
  selectedVoice: null,
  voiceSpeed: 1,
  voiceVolume: 100,
  designDrafts: loadFromStorage(STORAGE_KEYS.DESIGN_DRAFTS) || [],
  userWorks: loadFromStorage(STORAGE_KEYS.USER_WORKS) || [],
  history: [initialProject],
  historyIndex: 0,
  isLoadingDigitalHumans: false,
  digitalHumansError: null,
  myModels: [],
  isLoadingMyModels: false,
  myModelsError: null,
  actions: {
    ...createEditorActions(set, get),
    ...createResourceActions(set, get),
    ...createLibraryActions(set, get),
  },
}));
