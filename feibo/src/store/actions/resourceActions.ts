import type { AppState, DigitalHuman, Project } from '@/types';
import { fetchDigitalHumans } from '@/services/digitalHumanApi';
import { fetchMyModels } from '@/services/digitalHuman/modelApi';
import { autoSaveProject } from '@/store/project';
import type { StoreGet, StoreSet } from '@/store/storeTypes';

type ResourceActionKeys =
  | 'loadDigitalHumans'
  | 'refreshDigitalHumans'
  | 'loadMyModels'
  | 'refreshMyModels'
  | 'setMyModels'
  | 'setSelectedVoice'
  | 'setVoiceSpeed'
  | 'setVoiceVolume';

function updateVoiceState(
  set: StoreSet,
  currentProject: Project | null,
  selectedField: 'voiceId' | 'voiceSpeed' | 'voiceVolume',
  value: string | number | undefined,
) {
  if (!currentProject) {
    return;
  }

  const nextProject: Project = {
    ...currentProject,
    pages: currentProject.pages.map((page) =>
      page.id === currentProject.currentPageId ? { ...page, [selectedField]: value } : page,
    ),
    updatedAt: new Date(),
  };

  set({ currentProject: nextProject });
  autoSaveProject(nextProject);
}

export function createResourceActions(
  set: StoreSet,
  get: StoreGet,
): Pick<AppState['actions'], ResourceActionKeys> {
  return {
    loadDigitalHumans: async () => {
      set({ isLoadingDigitalHumans: true, digitalHumansError: null });

      try {
        const digitalHumans = await fetchDigitalHumans();
        set({
          digitalHumans,
          isLoadingDigitalHumans: false,
          digitalHumansError: null,
        });
      } catch (error) {
        console.error('加载数字人数据失败:', error);
        set({
          isLoadingDigitalHumans: false,
          digitalHumansError: error instanceof Error ? error.message : '加载数字人数据失败',
        });
      }
    },

    refreshDigitalHumans: async () => {
      await get().actions.loadDigitalHumans();
    },

    loadMyModels: async () => {
      set({ isLoadingMyModels: true, myModelsError: null });

      try {
        const models = await fetchMyModels();
        set({ myModels: models, isLoadingMyModels: false });
      } catch (error) {
        console.error('加载我的模型失败:', error);
        set({
          myModelsError: error instanceof Error ? error.message : '加载我的模型失败',
          isLoadingMyModels: false,
        });
      }
    },

    refreshMyModels: async () => {
      await get().actions.loadMyModels();
    },

    setMyModels: (models: DigitalHuman[]) => {
      set({ myModels: models });
    },

    setSelectedVoice: (voiceId) => {
      const state = get();
      set({ selectedVoice: voiceId });
      updateVoiceState(set, state.currentProject, 'voiceId', voiceId ?? undefined);
    },

    setVoiceSpeed: (speed) => {
      const clampedSpeed = Math.max(0.5, Math.min(2, speed));
      const state = get();
      set({ voiceSpeed: clampedSpeed });
      updateVoiceState(set, state.currentProject, 'voiceSpeed', clampedSpeed);
    },

    setVoiceVolume: (volume) => {
      const clampedVolume = Math.max(0, Math.min(100, volume));
      const state = get();
      set({ voiceVolume: clampedVolume });
      updateVoiceState(set, state.currentProject, 'voiceVolume', clampedVolume);
    },
  };
}
