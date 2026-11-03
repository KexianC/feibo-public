import type { AppState, DesignDraft, Project, ThemeType, UserWork } from '@/types';
import { STORAGE_KEYS } from '@/types';
import { loadFromStorage, saveTheme, saveToStorage } from '@/store/persistence';
import { autoSaveProject, createDefaultProject, deepClone, loadSavedProject } from '@/store/project';
import type { StoreGet, StoreSet } from '@/store/storeTypes';

type LibraryActionKeys =
  | 'saveDesignDraft'
  | 'loadDesignDraft'
  | 'deleteDesignDraft'
  | 'loadDesignDrafts'
  | 'initializeAppState'
  | 'clearAllData'
  | 'setTheme'
  | 'toggleTheme'
  | 'saveUserWork'
  | 'loadUserWorks'
  | 'updateUserWork'
  | 'deleteUserWork';

export function createLibraryActions(
  set: StoreSet,
  get: StoreGet,
): Pick<AppState['actions'], LibraryActionKeys> {
  return {
    saveDesignDraft: (name) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      const currentPage = state.currentProject.pages.find(
        (page) => page.id === state.currentProject?.currentPageId,
      );
      if (!currentPage) {
        return;
      }

      const newDraft: DesignDraft = {
        id: crypto.randomUUID(),
        name: name || `设计草稿 ${state.designDrafts.length + 1}`,
        elements: deepClone(currentPage.elements),
        aspectRatio: currentPage.aspectRatio,
        script: currentPage.script,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      const nextDrafts = [...state.designDrafts, newDraft];
      set({ designDrafts: nextDrafts });
      saveToStorage(STORAGE_KEYS.DESIGN_DRAFTS, nextDrafts);
    },

    loadDesignDraft: (draftId) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      const draft = state.designDrafts.find((item) => item.id === draftId);
      if (!draft) {
        return;
      }

      get().actions.saveToHistory(state.currentProject);

      const nextProject: Project = {
        ...state.currentProject,
        pages: state.currentProject.pages.map((page) =>
          page.id === state.currentProject?.currentPageId
            ? {
                ...page,
                elements: deepClone(draft.elements),
                script: draft.script || '',
                aspectRatio: draft.aspectRatio,
              }
            : page,
        ),
        updatedAt: new Date(),
      };

      set({
        currentProject: nextProject,
        selectedElementId: null,
      });
      autoSaveProject(nextProject);
    },

    deleteDesignDraft: (draftId) => {
      const state = get();
      const nextDrafts = state.designDrafts.filter((draft) => draft.id !== draftId);
      set({ designDrafts: nextDrafts });
      saveToStorage(STORAGE_KEYS.DESIGN_DRAFTS, nextDrafts);
    },

    loadDesignDrafts: () => {
      const drafts = loadFromStorage<DesignDraft[]>(STORAGE_KEYS.DESIGN_DRAFTS) || [];
      set({ designDrafts: drafts });
    },

    initializeAppState: () => {
      try {
        const savedProject = loadSavedProject();
        if (savedProject) {
          set({
            currentProject: savedProject,
            history: [savedProject],
            historyIndex: 0,
          });
        }

        set({
          designDrafts: loadFromStorage<DesignDraft[]>(STORAGE_KEYS.DESIGN_DRAFTS) || [],
          userWorks: loadFromStorage<UserWork[]>(STORAGE_KEYS.USER_WORKS) || [],
        });

        void get().actions.loadDigitalHumans();
      } catch (error) {
        console.error('初始化应用状态失败:', error);
      }
    },

    clearAllData: () => {
      try {
        localStorage.removeItem(STORAGE_KEYS.CURRENT_PROJECT);
        localStorage.removeItem(STORAGE_KEYS.PROJECTS);
        localStorage.removeItem(STORAGE_KEYS.DESIGN_DRAFTS);
        localStorage.removeItem(STORAGE_KEYS.USER_WORKS);
        localStorage.removeItem(STORAGE_KEYS.USER_AUTH);
        localStorage.removeItem(STORAGE_KEYS.APP_STATE);

        const defaultProject = createDefaultProject();
        set({
          currentProject: defaultProject,
          selectedElementId: null,
          designDrafts: [],
          userWorks: [],
          history: [defaultProject],
          historyIndex: 0,
        });
      } catch (error) {
        console.error('清除数据失败:', error);
      }
    },

    setTheme: (theme: ThemeType) => {
      set({ currentTheme: theme });
      saveTheme(theme);
    },

    toggleTheme: () => {
      const currentTheme = get().currentTheme;
      get().actions.setTheme(currentTheme === 'classic' ? 'dark' : 'classic');
    },

    saveUserWork: (work) => {
      const state = get();
      const nextWorks = [...state.userWorks, work];
      set({ userWorks: nextWorks });
      saveToStorage(STORAGE_KEYS.USER_WORKS, nextWorks);
    },

    loadUserWorks: () => {
      const works = loadFromStorage<UserWork[]>(STORAGE_KEYS.USER_WORKS) || [];
      set({ userWorks: works });
    },

    updateUserWork: (taskId, updates) => {
      const state = get();
      const nextWorks = state.userWorks.map((work) =>
        work.taskId === taskId ? { ...work, ...updates } : work,
      );
      set({ userWorks: nextWorks });
      saveToStorage(STORAGE_KEYS.USER_WORKS, nextWorks);
    },

    deleteUserWork: (taskId) => {
      const state = get();
      const nextWorks = state.userWorks.filter((work) => work.taskId !== taskId);
      set({ userWorks: nextWorks });
      saveToStorage(STORAGE_KEYS.USER_WORKS, nextWorks);
    },
  };
}
