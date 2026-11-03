import { Suspense, lazy } from 'react';
import EditorLoadingFallback from '@/features/editor/components/EditorLoadingFallback';
import Canvas from '@/components/Canvas';
import ToolbarPanel from '@/components/ToolbarPanel';
import { useCurrentProject } from '@/store';

const EditorLeftSidebar = lazy(() => import('@/features/editor/sidebar/EditorLeftSidebar'));
const EditorRightSidebar = lazy(() => import('@/features/editor/sidebar/EditorRightSidebar'));
const BottomPanel = lazy(() => import('@/components/BottomPanel'));

export default function WorkbenchShell() {
  const currentProject = useCurrentProject();
  const currentPage = currentProject?.pages.find((page) => page.id === currentProject.currentPageId);
  const aspectRatio = currentPage?.aspectRatio ?? '16:9';
  const isPortrait = aspectRatio === '9:16';
  const actionPortalId = 'synthesis-toolbar-actions';

  const sidebarFallback = <EditorLoadingFallback label="正在加载侧栏..." className="w-64 h-full" />;
  const rightSidebarFallback = <EditorLoadingFallback label="正在加载资源..." className="w-80 h-full" />;
  const panelFallback = <EditorLoadingFallback label="正在加载底部面板..." className="h-full" />;

  if (isPortrait) {
    return (
      <div className="flex h-full min-h-0 gap-3 p-3">
        <Suspense fallback={sidebarFallback}>
          <EditorLeftSidebar />
        </Suspense>
        <div className="min-w-0 flex-1 overflow-hidden rounded-lg">
          <Canvas />
        </div>
        <div className="w-96 flex-shrink-0 overflow-hidden rounded-lg app-panel">
          <Suspense fallback={panelFallback}>
            <BottomPanel actionPortalId={actionPortalId} />
          </Suspense>
        </div>
        <ToolbarPanel actionPortalId={actionPortalId} className="min-h-0 flex-shrink-0" />
        <Suspense fallback={rightSidebarFallback}>
          <EditorRightSidebar />
        </Suspense>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 gap-3 p-3">
      <Suspense fallback={sidebarFallback}>
        <EditorLeftSidebar />
      </Suspense>
      <div className="flex min-h-0 flex-1 gap-3">
        <div className="flex min-h-0 flex-1 flex-col gap-3">
          <div className="min-h-0 flex-[3] overflow-hidden rounded-lg">
            <Canvas />
          </div>
          <div className="min-h-0 flex-[1] overflow-hidden rounded-lg app-panel">
            <Suspense fallback={panelFallback}>
              <BottomPanel actionPortalId={actionPortalId} />
            </Suspense>
          </div>
        </div>
        <ToolbarPanel actionPortalId={actionPortalId} className="min-h-0 flex-shrink-0" />
      </div>
      <Suspense fallback={rightSidebarFallback}>
        <EditorRightSidebar />
      </Suspense>
    </div>
  );
}
