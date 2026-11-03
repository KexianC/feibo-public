import { v4 as uuidv4 } from 'uuid';
import type { AppState, Page, PageSubtitleConfig, Project, SceneElement } from '@/types';
import { autoSaveProject, createDefaultSubtitleConfig, deepClone } from '@/store/project';
import type { StoreGet, StoreSet } from '@/store/storeTypes';

type EditorActionKeys =
  | 'saveToHistory'
  | 'loadProject'
  | 'addPage'
  | 'deletePage'
  | 'switchPage'
  | 'duplicatePage'
  | 'renamePage'
  | 'updatePageScript'
  | 'updatePageSubtitleConfig'
  | 'getNextZIndex'
  | 'addElement'
  | 'updateElement'
  | 'deleteElement'
  | 'deleteElementsByType'
  | 'selectElement'
  | 'undo'
  | 'redo';

function updateProjectState(
  currentProject: Project,
  set: StoreSet,
  nextProject: Project,
  extraState?: Partial<AppState>,
) {
  set({
    currentProject: nextProject,
    ...extraState,
  });
  autoSaveProject(nextProject);
}

export function createEditorActions(
  set: StoreSet,
  get: StoreGet,
): Pick<AppState['actions'], EditorActionKeys> {
  return {
    saveToHistory: (project) => {
      const state = get();
      const clonedProject = deepClone(project);
      const nextHistory = state.history.slice(0, state.historyIndex + 1);
      nextHistory.push(clonedProject);

      const maxHistoryLength = 50;
      if (nextHistory.length > maxHistoryLength) {
        nextHistory.shift();
      } else {
        set({ historyIndex: state.historyIndex + 1 });
      }

      set({ history: nextHistory });
    },

    loadProject: (project) => {
      set({ currentProject: project });
      autoSaveProject(project);
    },

    addPage: (aspectRatio = '16:9') => {
      const state = get();
      if (!state.currentProject) {
        return null;
      }

      get().actions.saveToHistory(state.currentProject);

      const newPage: Page = {
        id: uuidv4(),
        name: `页面 ${state.currentProject.pages.length + 1}`,
        elements: [],
        duration: 5000,
        script: '',
        aspectRatio,
        subtitleConfig: createDefaultSubtitleConfig(),
        voiceId: undefined,
        voiceSpeed: 1,
        voiceVolume: 100,
      };

      const nextProject: Project = {
        ...state.currentProject,
        pages: [...state.currentProject.pages, newPage],
        currentPageId: newPage.id,
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject);
      return newPage.id;
    },

    deletePage: (pageId) => {
      const state = get();
      if (!state.currentProject || state.currentProject.pages.length <= 1) {
        return;
      }

      get().actions.saveToHistory(state.currentProject);

      const updatedPages = state.currentProject.pages.filter((page) => page.id !== pageId);
      const nextProject: Project = {
        ...state.currentProject,
        pages: updatedPages,
        currentPageId:
          state.currentProject.currentPageId === pageId
            ? updatedPages[0].id
            : state.currentProject.currentPageId,
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject);
    },

    switchPage: (pageId) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      const targetPage = state.currentProject.pages.find((page) => page.id === pageId);
      if (!targetPage) {
        return;
      }

      const nextProject: Project = {
        ...state.currentProject,
        currentPageId: pageId,
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject, {
        selectedElementId: null,
      });
    },

    duplicatePage: (pageId) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      const sourcePage = state.currentProject.pages.find((page) => page.id === pageId);
      if (!sourcePage) {
        return;
      }

      get().actions.saveToHistory(state.currentProject);

      const newPage: Page = {
        ...sourcePage,
        id: uuidv4(),
        name: `${sourcePage.name} 副本`,
        elements: sourcePage.elements.map((element) => ({
          ...element,
          id: uuidv4(),
        })),
      };

      const nextProject: Project = {
        ...state.currentProject,
        pages: [...state.currentProject.pages, newPage],
        currentPageId: newPage.id,
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject, {
        selectedElementId: null,
      });
    },

    renamePage: (pageId, newName) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      get().actions.saveToHistory(state.currentProject);

      const nextProject: Project = {
        ...state.currentProject,
        pages: state.currentProject.pages.map((page) =>
          page.id === pageId ? { ...page, name: newName } : page,
        ),
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject);
    },

    updatePageScript: (script) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      get().actions.saveToHistory(state.currentProject);

      const nextProject: Project = {
        ...state.currentProject,
        pages: state.currentProject.pages.map((page) =>
          page.id === state.currentProject?.currentPageId ? { ...page, script } : page,
        ),
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject);
    },

    updatePageSubtitleConfig: (subtitleConfig: PageSubtitleConfig) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      get().actions.saveToHistory(state.currentProject);

      const nextProject: Project = {
        ...state.currentProject,
        pages: state.currentProject.pages.map((page) =>
          page.id === state.currentProject?.currentPageId ? { ...page, subtitleConfig } : page,
        ),
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject);
    },

    getNextZIndex: (elementType) => {
      const state = get();
      if (!state.currentProject) {
        return elementType === 'background' ? 1 : 2;
      }

      const currentPage = state.currentProject.pages.find(
        (page) => page.id === state.currentProject?.currentPageId,
      );

      if (!currentPage || currentPage.elements.length === 0) {
        return elementType === 'background' ? 1 : 2;
      }

      if (elementType === 'background') {
        return 1;
      }

      const existingZIndexes = currentPage.elements.map((element) => element.zIndex);
      const maxZIndex = Math.max(...existingZIndexes.filter((zIndex) => zIndex >= 2));

      if (maxZIndex < 2 || Number.isNaN(maxZIndex)) {
        return 2;
      }

      if (maxZIndex < 100) {
        return maxZIndex + 1;
      }

      for (let i = 2; i <= 100; i += 1) {
        if (!existingZIndexes.includes(i)) {
          return i;
        }
      }

      return 2;
    },

    addElement: (element: SceneElement) => {
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

      get().actions.saveToHistory(state.currentProject);

      const nextProject: Project = {
        ...state.currentProject,
        pages: state.currentProject.pages.map((page) =>
          page.id === state.currentProject?.currentPageId
            ? { ...page, elements: [...page.elements, element] }
            : page,
        ),
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject, {
        selectedElementId: element.id,
      });
    },

    updateElement: (elementId, updates) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      get().actions.saveToHistory(state.currentProject);

      const nextProject: Project = {
        ...state.currentProject,
        pages: state.currentProject.pages.map((page) => ({
          ...page,
          elements: page.elements.map((element) =>
            element.id === elementId ? { ...element, ...updates } : element,
          ),
        })),
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject);
    },

    deleteElement: (elementId) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      get().actions.saveToHistory(state.currentProject);

      const nextProject: Project = {
        ...state.currentProject,
        pages: state.currentProject.pages.map((page) => ({
          ...page,
          elements: page.elements.filter((element) => element.id !== elementId),
        })),
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject, {
        selectedElementId: state.selectedElementId === elementId ? null : state.selectedElementId,
      });
    },

    deleteElementsByType: (elementType) => {
      const state = get();
      if (!state.currentProject) {
        return;
      }

      get().actions.saveToHistory(state.currentProject);

      const nextProject: Project = {
        ...state.currentProject,
        pages: state.currentProject.pages.map((page) =>
          page.id === state.currentProject?.currentPageId
            ? {
                ...page,
                elements: page.elements.filter((element) => element.type !== elementType),
              }
            : page,
        ),
        updatedAt: new Date(),
      };

      updateProjectState(state.currentProject, set, nextProject, {
        selectedElementId: null,
      });
    },

    selectElement: (elementId) => {
      set({ selectedElementId: elementId });
    },

    undo: () => {
      const state = get();
      if (state.historyIndex <= 0) {
        return;
      }

      const previousProject = deepClone(state.history[state.historyIndex - 1]);
      set({
        currentProject: previousProject,
        historyIndex: state.historyIndex - 1,
        selectedElementId: null,
      });
      autoSaveProject(previousProject);
    },

    redo: () => {
      const state = get();
      if (state.historyIndex >= state.history.length - 1) {
        return;
      }

      const nextProject = deepClone(state.history[state.historyIndex + 1]);
      set({
        currentProject: nextProject,
        historyIndex: state.historyIndex + 1,
        selectedElementId: null,
      });
      autoSaveProject(nextProject);
    },
  };
}
