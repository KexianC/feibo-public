import React, { Suspense, lazy, useEffect, useRef, useState } from 'react';
import {
  AlertCircle,
  FileImage,
  Grid2x2,
  ImageIcon,
  Mic2,
  Music2,
  RefreshCw,
  Search,
  Subtitles,
  Type,
  UserCircle2,
  Video,
  Zap,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { v4 as uuidv4 } from 'uuid';
import EditorLoadingFallback from '@/features/editor/components/EditorLoadingFallback';
import { useCurrentProject, useSelectedElement, useStore } from '@/store';
import type { DigitalHuman, SceneElement } from '@/types';

const BackgroundPanel = lazy(() => import('@/components/BackgroundPanel'));
const TitlePanel = lazy(() => import('@/components/TitlePanel'));
const IllustrationPanel = lazy(() => import('@/components/IllustrationPanel'));
const VoicePanel = lazy(() => import('@/components/VoicePanel'));
const VideoPanel = lazy(() => import('@/components/VideoPanel'));
const MusicPanel = lazy(() => import('@/components/MusicPanel'));
const FlashTextPanel = lazy(() => import('@/components/FlashTextPanel'));
const SubtitlePanel = lazy(() => import('@/components/SubtitlePanel'));

type TabType =
  | 'model'
  | 'voice'
  | 'background'
  | 'illustration'
  | 'title'
  | 'video'
  | 'music'
  | 'flash'
  | 'subtitle';

const tabs = [
  { id: 'model' as TabType, name: '模型', icon: UserCircle2 },
  { id: 'voice' as TabType, name: '配音', icon: Mic2 },
  { id: 'background' as TabType, name: '背景', icon: ImageIcon },
  { id: 'illustration' as TabType, name: '插图', icon: FileImage },
  { id: 'title' as TabType, name: '标题', icon: Type },
  { id: 'video' as TabType, name: '视频', icon: Video },
  { id: 'music' as TabType, name: '音乐', icon: Music2 },
  { id: 'flash' as TabType, name: '闪文', icon: Zap },
  { id: 'subtitle' as TabType, name: '字幕', icon: Subtitles },
];

interface DigitalHumanCardProps {
  digitalHuman: DigitalHuman;
  onSelect: (digitalHuman: DigitalHuman) => void;
  compact?: boolean;
}

const DigitalHumanCard: React.FC<DigitalHumanCardProps> = ({ digitalHuman, onSelect, compact }) => {
  return (
    <div
      className="group cursor-pointer overflow-hidden rounded-lg border border-slate-200 bg-white transition-all hover:border-teal-300 hover:shadow-sm"
      onClick={() => onSelect(digitalHuman)}
    >
      <div className="relative aspect-square overflow-hidden bg-slate-100">
        <img
          src={digitalHuman.avatar}
          alt={digitalHuman.name}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          loading="lazy"
          decoding="async"
          onError={(e) => {
            const target = e.target as HTMLImageElement;
            target.src =
              'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjMzc0MTUxIi8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iNDAiIHI9IjE1IiBmaWxsPSIjNkI3Mjg0Ii8+CjxwYXRoIGQ9Ik0yNSA3NUMyNSA2NS4zMzUgMzMuMzM1IDU3IDQzIDU3SDE1N0M2Ni42NjUgNTcgNzUgNjUuMzM1IDc1IDc1VjgwSDI1Vjc1WiIgZmlsbD0iIzZCNzI4NCIvPgo8L3N2Zz4K';
          }}
        />
        <div className="absolute inset-0 flex items-center justify-center bg-slate-900/20 opacity-0 transition-opacity group-hover:opacity-100">
          <div className="rounded-full bg-white/95 px-3 py-1 text-sm font-medium text-slate-800 shadow-sm">
            点击添加
          </div>
        </div>
      </div>

      <div className="p-3">
        <h4 className="truncate text-sm font-medium text-slate-900" title={digitalHuman.name}>
          {compact ? (digitalHuman.name || '').slice(0, 2) : digitalHuman.name}
        </h4>
      </div>
    </div>
  );
};

const PlaceholderContent: React.FC<{ tabName: string; icon: LucideIcon }> = ({
  tabName,
  icon: Icon,
}) => {
  return (
    <div className="flex flex-1 items-center justify-center">
      <div className="text-center text-slate-600">
        <Icon size={48} className="mx-auto mb-4 opacity-50" />
        <h3 className="mb-2 text-lg font-medium text-slate-900">{tabName}</h3>
        <p className="text-sm text-slate-600">该功能正在开发中</p>
        <p className="mt-1 text-xs text-slate-500">敬请期待</p>
      </div>
    </div>
  );
};

export default function EditorRightSidebar() {
  const {
    digitalHumans,
    isLoadingDigitalHumans,
    digitalHumansError,
    myModels,
    isLoadingMyModels,
    myModelsError,
    actions: {
      addElement,
      loadDigitalHumans,
      refreshDigitalHumans,
      loadMyModels,
      refreshMyModels,
      deleteElementsByType,
      selectElement,
      getNextZIndex,
    },
  } = useStore();
  const selectedElementId = useSelectedElement();
  const currentProject = useCurrentProject();
  const [selectedCategory, setSelectedCategory] = useState<string>('private');
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState<TabType>('model');
  const [gridCols, setGridCols] = useState<number>(2);
  const triedMyModelsRef = useRef(false);

  useEffect(() => {
    const handleOpenTab = (event: Event) => {
      const requestedTab = (event as CustomEvent<{ tab?: TabType }>).detail?.tab;
      if (requestedTab && tabs.some((tab) => tab.id === requestedTab)) {
        setActiveTab(requestedTab);
      }
    };

    window.addEventListener('feibo:open-editor-tab', handleOpenTab);
    return () => window.removeEventListener('feibo:open-editor-tab', handleOpenTab);
  }, []);

  const selectedElement =
    selectedElementId && currentProject
      ? currentProject.pages.find((page) => page.id === currentProject.currentPageId)?.elements.find((el) => el.id === selectedElementId) || null
      : null;

  useEffect(() => {
    loadDigitalHumans();
  }, [loadDigitalHumans]);

  useEffect(() => {
    if (activeTab !== 'model' || triedMyModelsRef.current || myModelsError) {
      return;
    }

    if (myModels.length === 0 && !isLoadingMyModels) {
      triedMyModelsRef.current = true;
      void loadMyModels();
    }
  }, [activeTab, isLoadingMyModels, loadMyModels, myModels.length, myModelsError]);

  useEffect(() => {
    if (selectedCategory !== 'private' || triedMyModelsRef.current || myModelsError) {
      return;
    }

    if (myModels.length === 0 && !isLoadingMyModels) {
      triedMyModelsRef.current = true;
      void loadMyModels();
    }
  }, [isLoadingMyModels, loadMyModels, myModels.length, myModelsError, selectedCategory]);

  const filteredDigitalHumans = (selectedCategory === 'public' ? digitalHumans : myModels).filter((digitalHuman) =>
    digitalHuman.name.toLowerCase().includes(searchTerm.toLowerCase()),
  );

  const currentIsLoading = selectedCategory === 'public' ? isLoadingDigitalHumans : isLoadingMyModels;
  const currentError = selectedCategory === 'public' ? digitalHumansError : myModelsError;

  const refreshCurrentCategory = () => {
    if (selectedCategory === 'public') {
      void refreshDigitalHumans();
      return;
    }

    void refreshMyModels();
  };

  const handleSelectDigitalHuman = (digitalHuman: DigitalHuman) => {
    deleteElementsByType('digital-human');

    window.setTimeout(() => {
      const storeProject = useStore.getState().currentProject;
      const currentPage = storeProject?.pages.find((page) => page.id === storeProject.currentPageId);
      const aspectRatio = currentPage?.aspectRatio || '16:9';
      const canvasWidth = aspectRatio === '9:16' ? 1080 : 1920;
      const canvasHeight = aspectRatio === '9:16' ? 1920 : 1080;
      const centerX = canvasWidth / 2;
      const centerY = canvasHeight / 2;

      const defaultModelHeight = aspectRatio === '9:16'
        ? canvasHeight * 0.55
        : canvasHeight * 0.72;
      const defaultModelMaxWidth = aspectRatio === '9:16'
        ? canvasWidth * 0.82
        : canvasWidth * 0.55;

      let elementHeight = defaultModelHeight;
      let elementWidth = defaultModelHeight * (2 / 3);

      if (digitalHuman.speakerWidth && digitalHuman.speakerHeight) {
        const digitalHumanAspectRatio = digitalHuman.speakerWidth / digitalHuman.speakerHeight;
        const baseHeight = defaultModelHeight;
        const calculatedWidth = baseHeight * digitalHumanAspectRatio;
        const maxWidth = defaultModelMaxWidth;

        if (calculatedWidth > maxWidth) {
          elementWidth = maxWidth;
          elementHeight = maxWidth / digitalHumanAspectRatio;
        } else {
          elementWidth = calculatedWidth;
          elementHeight = baseHeight;
        }

        elementWidth = Math.max(elementWidth, canvasWidth * 0.18);
        elementHeight = Math.max(elementHeight, canvasHeight * 0.34);
      }

      const element: SceneElement = {
        id: uuidv4(),
        type: 'digital-human',
        position: { x: centerX - elementWidth / 2, y: centerY - elementHeight / 2 },
        size: { width: elementWidth, height: elementHeight },
        rotation: 0,
        zIndex: getNextZIndex('digital-human'),
        properties: {
          digitalHumanId: digitalHuman.id,
          name: digitalHuman.name,
          avatar: digitalHuman.speakerUrl || digitalHuman.avatar,
          modelUrl: digitalHuman.modelUrl,
          speakerId: digitalHuman.speakerId,
          gender: digitalHuman.gender,
          magicAudioId: digitalHuman.magicAudioId,
          originalWidth: digitalHuman.speakerWidth,
          originalHeight: digitalHuman.speakerHeight,
        },
      };

      addElement(element);

      window.setTimeout(() => {
        selectElement(element.id);
      }, 100);
    }, 150);
  };

  const categories = [
    { id: 'private', name: '我的模型', count: myModels.length },
    { id: 'public', name: '公用模型', count: digitalHumans.length },
  ];

  const renderPanel = (node: React.ReactNode) => (
    <Suspense fallback={<EditorLoadingFallback label="正在加载面板..." />}>
      <div className="flex-1 flex flex-col min-h-0 overflow-hidden">{node}</div>
    </Suspense>
  );

  const renderContent = () => {
    if (selectedElement) {
      switch (selectedElement.type) {
        case 'title':
          return renderPanel(<TitlePanel isEditMode={true} selectedElement={selectedElement} />);
        case 'flash-text':
          return renderPanel(<FlashTextPanel isEditMode={true} selectedElement={selectedElement} />);
        default:
          break;
      }
    }

    switch (activeTab) {
      case 'model':
        return (
          <>
            <div className="border-b border-slate-200 p-4">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-slate-900">数字人库</h3>
                <button
                  onClick={refreshCurrentCategory}
                  disabled={currentIsLoading}
                  className="app-icon-button h-8 w-8 disabled:opacity-50"
                  title={selectedCategory === 'public' ? '刷新数字人库' : '刷新我的模型'}
                >
                  <RefreshCw size={16} className={currentIsLoading ? 'animate-spin' : ''} />
                </button>
              </div>

              <div className="flex items-center gap-2">
                <div className="relative flex-1">
                  <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
                  <input
                    type="text"
                    placeholder="搜索数字人..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="app-input w-full rounded-lg py-2 pl-10 pr-4 text-sm placeholder-slate-400"
                  />
                </div>
                <button
                  onClick={() => setGridCols(gridCols === 2 ? 4 : 2)}
                  className="rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700 hover:bg-slate-200"
                  title="切换每行显示数量"
                  aria-label={gridCols === 2 ? '切换到4列' : '切换到2列'}
                >
                  {gridCols === 2 ? <TwoSquaresIcon size={16} /> : <Grid2x2 size={16} />}
                </button>
              </div>
            </div>

            <div className="border-b border-slate-200 p-4">
              <div className="flex gap-2">
                {categories.map((category, index) => (
                  <button
                    key={category.id}
                    onClick={() => setSelectedCategory(category.id)}
                    className={`px-3 py-1.5 rounded-full text-sm font-medium transition-colors ${
                      selectedCategory === category.id
                        ? index % 2 === 0
                          ? 'bg-teal-600 text-white'
                          : 'bg-cyan-600 text-white'
                        : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                    }`}
                  >
                    {category.name} ({category.count})
                  </button>
                ))}
              </div>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-4">
              {currentError && (
                <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4">
                  <div className="flex items-center gap-2 text-red-600 mb-2">
                    <AlertCircle size={16} />
                    <span className="text-sm font-medium">加载失败</span>
                  </div>
                  <p className="text-red-500 text-xs">{currentError}</p>
                  <button
                    onClick={refreshCurrentCategory}
                    className="mt-2 rounded bg-red-600 px-3 py-1 text-xs text-white transition-colors hover:bg-red-700"
                  >
                    重试
                  </button>
                </div>
              )}

              {currentIsLoading ? (
                <div className="mt-8 text-center text-slate-600">
                  <div className="animate-spin w-8 h-8 border-2 border-teal-500 border-t-transparent rounded-full mx-auto mb-4" />
                  <p className="text-sm text-slate-800">
                    {selectedCategory === 'public' ? '正在加载数字人库...' : '正在加载我的模型...'}
                  </p>
                </div>
              ) : filteredDigitalHumans.length > 0 ? (
                <div className={`grid ${gridCols === 2 ? 'grid-cols-2' : 'grid-cols-4'} gap-3`}>
                  {filteredDigitalHumans.map((digitalHuman) => (
                    <DigitalHumanCard
                      key={digitalHuman.id}
                      digitalHuman={digitalHuman}
                      onSelect={handleSelectDigitalHuman}
                      compact={gridCols === 4}
                    />
                  ))}
                </div>
              ) : (
                <div className="mt-8 text-center text-slate-600">
                  <UserCircle2 size={40} className="mx-auto mb-3 text-slate-300" />
                  <p className="text-sm text-slate-800">
                    {selectedCategory === 'public' ? '没有找到匹配的数字人' : '没有找到匹配的模型'}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">尝试调整搜索条件或刷新数据</p>
                </div>
              )}
            </div>
          </>
        );
      case 'background':
        return renderPanel(<BackgroundPanel />);
      case 'title':
        return renderPanel(<TitlePanel />);
      case 'illustration':
        return renderPanel(<IllustrationPanel />);
      case 'voice':
        return renderPanel(<VoicePanel />);
      case 'video':
        return renderPanel(<VideoPanel />);
      case 'music':
        return renderPanel(<MusicPanel />);
      case 'flash':
        return renderPanel(<FlashTextPanel />);
      case 'subtitle':
        return renderPanel(<SubtitlePanel />);
      default: {
        const currentTab = tabs.find((tab) => tab.id === activeTab);
        return currentTab ? <PlaceholderContent tabName={currentTab.name} icon={currentTab.icon} /> : null;
      }
    }
  };

  return (
    <div className="flex h-full min-h-0 w-80 flex-shrink-0 overflow-hidden rounded-lg border app-panel">
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">{renderContent()}</div>

      <div className="flex w-[2.8rem] flex-shrink-0 flex-col border-l border-slate-200 bg-slate-50">
        {tabs.map((tab, index) => {
          const Icon = tab.icon;

          return (
            <button
              key={tab.id}
              onClick={() => {
                setActiveTab(tab.id);
                selectElement(null);
              }}
              className={`flex flex-col items-center justify-center border-b border-slate-200 px-1 py-3 transition-colors ${
                activeTab === tab.id
                  ? index % 2 === 0
                    ? 'bg-teal-600 text-white'
                    : 'bg-cyan-600 text-white'
                  : 'text-slate-600 hover:bg-white hover:text-slate-900'
              }`}
              title={tab.name}
            >
              <Icon size={18} className="mb-1" />
              <span className="text-[11px] font-medium leading-tight">{tab.name}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

const TwoSquaresIcon: React.FC<{ size?: number; className?: string }> = ({ size = 16, className }) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
    aria-hidden="true"
  >
    <rect x="3" y="6" width="8" height="8" rx="1" />
    <rect x="13" y="6" width="8" height="8" rx="1" />
  </svg>
);
