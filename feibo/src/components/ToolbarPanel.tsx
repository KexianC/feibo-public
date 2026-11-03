import React, { Suspense, lazy, useEffect } from 'react';
import {
  ChevronDown,
  ChevronUp,
  Redo2,
  Undo2,
} from 'lucide-react';
import { useActions, useCanRedo, useCanUndo, useCurrentProject, useSelectedElement } from '../store';

const LayersPanel = lazy(() => import('./LayersPanel'));

interface ToolbarPanelProps {
  actionPortalId?: string;
  className?: string;
}

const ToolbarPanel: React.FC<ToolbarPanelProps> = ({ actionPortalId, className = '' }) => {
  const currentProject = useCurrentProject();
  const selectedElementId = useSelectedElement();
  const canUndo = useCanUndo();
  const canRedo = useCanRedo();
  const { undo, redo, updateElement } = useActions();

  useEffect(() => {
    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      const isCtrl = e.ctrlKey || (e.metaKey ?? false);
      const isShift = e.shiftKey;

      if (isCtrl && (e.key.toLowerCase() === 'z' || e.code === 'KeyZ')) {
        if (!isShift) {
          if (canUndo) {
            e.preventDefault();
            undo();
          }
          return;
        }

        if (canRedo) {
          e.preventDefault();
          redo();
        }
        return;
      }

      if (isCtrl && (e.key.toLowerCase() === 'y' || e.code === 'KeyY')) {
        if (canRedo) {
          e.preventDefault();
          redo();
        }
      }
    };

    window.addEventListener('keydown', handleGlobalKeyDown);
    return () => window.removeEventListener('keydown', handleGlobalKeyDown);
  }, [canRedo, canUndo, redo, undo]);

  const currentPage = currentProject?.pages.find((page) => page.id === currentProject.currentPageId);
  const selectedElement = selectedElementId
    ? currentPage?.elements.find((element) => element.id === selectedElementId)
    : null;
  const canAdjustLayer = !!selectedElement && selectedElement.type !== 'background';

  const handleMoveUp = () => {
    if (!selectedElementId || !currentPage) return;

    const element = currentPage.elements.find((item) => item.id === selectedElementId);
    if (!element || element.zIndex >= 100 || element.type === 'background') return;

    updateElement(selectedElementId, { zIndex: element.zIndex + 1 });
  };

  const handleMoveDown = () => {
    if (!selectedElementId || !currentPage) return;

    const element = currentPage.elements.find((item) => item.id === selectedElementId);
    if (!element || element.type === 'background') return;

    updateElement(selectedElementId, { zIndex: Math.max(1, element.zIndex - 1) });
  };

  const iconButtonClass = (enabled: boolean) =>
    `flex h-8 items-center justify-center rounded-lg transition-colors ${
      enabled
        ? 'text-slate-600 hover:bg-teal-500 hover:text-white'
        : 'cursor-not-allowed text-slate-300'
    }`;

  return (
    <div className={`flex w-[10.5rem] flex-col rounded-lg border app-panel p-2 ${className}`}>
      <div className="grid grid-cols-4 gap-1 border-b border-slate-200 pb-2">
        <button
          onClick={undo}
          disabled={!canUndo}
          className={iconButtonClass(canUndo)}
          title="撤销"
        >
          <Undo2 size={16} />
        </button>
        <button
          onClick={redo}
          disabled={!canRedo}
          className={iconButtonClass(canRedo)}
          title="重做"
        >
          <Redo2 size={16} />
        </button>
        <button
          onClick={handleMoveUp}
          disabled={!canAdjustLayer}
          className={iconButtonClass(canAdjustLayer)}
          title={selectedElement?.type === 'background' ? '背景层级已锁定' : '上移一层'}
        >
          <ChevronUp size={16} />
        </button>
        <button
          onClick={handleMoveDown}
          disabled={!canAdjustLayer}
          className={iconButtonClass(canAdjustLayer)}
          title={selectedElement?.type === 'background' ? '背景层级已锁定' : '下移一层'}
        >
          <ChevronDown size={16} />
        </button>
      </div>

      <div className="my-2 min-h-0 flex-1 overflow-hidden border-b border-slate-200 pb-2">
        <Suspense fallback={<div className="py-3 text-center text-xs text-slate-400">加载中</div>}>
          <LayersPanel compact />
        </Suspense>
      </div>

      {actionPortalId && (
        <div
          id={actionPortalId}
          className="w-full"
        />
      )}
    </div>
  );
};

export default ToolbarPanel;
