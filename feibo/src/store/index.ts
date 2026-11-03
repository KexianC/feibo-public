export { THEME_CONFIG } from '@/store/constants';
export { useStore } from '@/store/appStore';

import { THEME_CONFIG } from '@/store/constants';
import { useStore } from '@/store/appStore';

export const useCurrentProject = () => useStore((state) => state.currentProject);
export const useSelectedElement = () => useStore((state) => state.selectedElementId);
export const useDigitalHumans = () => useStore((state) => state.digitalHumans);
export const useSelectedVoice = () => useStore((state) => state.selectedVoice);
export const useVoiceSpeed = () => useStore((state) => state.voiceSpeed);
export const useVoiceVolume = () => useStore((state) => state.voiceVolume);
export const useActions = () => useStore((state) => state.actions);
export const useCurrentTheme = () => useStore((state) => state.currentTheme);
export const useThemeConfig = () => {
  const theme = useStore((state) => state.currentTheme);
  return THEME_CONFIG[theme];
};
export const useCanUndo = () => useStore((state) => state.historyIndex > 0);
export const useCanRedo = () => useStore((state) => state.historyIndex < state.history.length - 1);
export const useHistoryIndex = () => useStore((state) => state.historyIndex);
export const useHistoryLength = () => useStore((state) => state.history.length);
