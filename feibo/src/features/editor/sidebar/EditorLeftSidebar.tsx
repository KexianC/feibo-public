import React, { Suspense, lazy, useState } from 'react';
import { Copy, CreditCard, FolderOpen, MessageCircle, Palette, Plus, Sparkles, Trash2, Video, Zap } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import EditorLoadingFallback from '@/features/editor/components/EditorLoadingFallback';
import AspectRatioModal from '@/components/AspectRatioModal';
import ThumbnailGenerator from '@/components/ThumbnailGenerator';
import { ROUTE_PATHS } from '@/router/routePaths';
import { useActions, useCurrentProject } from '@/store';
import type { Page } from '@/types';

const MyWorks = lazy(() => import('@/components/MyWorks'));
const MyDesigns = lazy(() => import('@/components/MyDesigns'));
const ApplicationTemplates = lazy(() => import('@/components/ApplicationTemplates'));
const CaseVideos = lazy(() => import('@/components/CaseVideos'));
const UserFeedbackModal = lazy(() => import('@/components/UserFeedbackModal'));
const RechargeModal = lazy(() => import('@/components/RechargeModal'));

interface PageItemProps {
  page: Page;
  isActive: boolean;
  onSelect: () => void;
  onDelete: () => void;
  onDuplicate: () => void;
  onStartRename: () => void;
  isEditing: boolean;
  editingName: string;
  onNameChange: (name: string) => void;
  onFinishRename: () => void;
  onCancelRename: () => void;
}

const PageItem: React.FC<PageItemProps> = ({
  page,
  isActive,
  onSelect,
  onDelete,
  onDuplicate,
  onStartRename,
  isEditing,
  editingName,
  onNameChange,
  onFinishRename,
  onCancelRename,
}) => {
  return (
    <div
      className={`group relative cursor-pointer rounded-lg border transition-all ${
        isActive ? 'border-teal-500 bg-teal-50 shadow-sm' : 'border-slate-200 bg-white hover:border-teal-300 hover:shadow-sm'
      }`}
      onClick={onSelect}
    >
      <div className="relative m-2 aspect-video overflow-hidden rounded-md bg-slate-100">
        <div className="absolute inset-0 flex items-stretch">
          <div className="w-full">
            <ThumbnailGenerator
              elements={page.elements}
              aspectRatio={page.aspectRatio || '16:9'}
              width={220}
              maxHeight={260}
              fitContainer={true}
            />
          </div>
        </div>
        <div className="absolute bottom-1 left-1 rounded bg-slate-900/70 px-1 py-0.5 text-[8px] text-white">
          {page.elements.length}
        </div>
      </div>

      <div className="px-2 pb-2">
        {isEditing ? (
          <input
            type="text"
            value={editingName}
            onChange={(e) => onNameChange(e.target.value)}
            onBlur={onFinishRename}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                onFinishRename();
              } else if (e.key === 'Escape') {
                onCancelRename();
              }
            }}
            className="app-input w-full rounded px-1 py-0.5 text-sm font-medium"
            autoFocus
            onClick={(e) => e.stopPropagation()}
          />
        ) : (
          <div
            className="cursor-pointer truncate text-sm font-medium text-slate-900 transition-colors hover:text-teal-600"
            onDoubleClick={(e) => {
              e.stopPropagation();
              onStartRename();
            }}
            title="双击重命名"
          >
            {page.name}
          </div>
        )}
      </div>

      <div className="absolute right-1 top-1 flex space-x-1 opacity-0 transition-opacity group-hover:opacity-100">
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDuplicate();
          }}
          className="rounded bg-white/90 p-1 text-slate-600 shadow-sm hover:bg-slate-100 hover:text-slate-900"
          title="复制页面"
        >
          <Copy size={12} />
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDelete();
          }}
          className="rounded bg-white/90 p-1 text-slate-600 shadow-sm hover:bg-red-500 hover:text-white"
          title="删除页面"
        >
          <Trash2 size={12} />
        </button>
      </div>

      {isActive && (
        <div className="absolute -left-1 top-1/2 transform -translate-y-1/2 w-1 h-8 bg-teal-500 rounded-r" />
      )}
    </div>
  );
};

export default function EditorLeftSidebar() {
  const currentProject = useCurrentProject();
  const { addPage, deletePage, switchPage, duplicatePage, renamePage, selectElement } = useActions();
  const navigate = useNavigate();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingPageId, setEditingPageId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState('');
  const [activeModal, setActiveModal] = useState<string | null>(null);
  const [showMyWorks, setShowMyWorks] = useState(false);
  const [showFeedbackModal, setShowFeedbackModal] = useState(false);
  const [showRechargeModal, setShowRechargeModal] = useState(false);

  if (!currentProject) {
    return (
      <div className="w-64 rounded-lg border app-panel p-4">
        <div className="text-center text-slate-600">没有打开的项目</div>
      </div>
    );
  }

  const handleFinishRename = () => {
    if (editingPageId && editingName.trim()) {
      renamePage(editingPageId, editingName.trim());
    }

    setEditingPageId(null);
    setEditingName('');
  };

  return (
    <div className="flex w-64 flex-col overflow-hidden rounded-lg border app-panel">
      <div className="border-b border-slate-200 p-3">
        <div className="grid grid-cols-2 gap-2">
          <button
            onClick={() => {
              selectElement(null);
              setShowMyWorks(true);
            }}
            className="group flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white p-3 transition-colors hover:border-teal-200 hover:bg-teal-50"
          >
            <FolderOpen size={20} className="text-teal-600 mb-1 group-hover:scale-110 transition-transform" />
            <span className="text-gray-900 text-xs font-medium">我的作品</span>
          </button>
          <button
            onClick={() => {
              selectElement(null);
              setActiveModal('designs');
            }}
            className="group flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white p-3 transition-colors hover:border-cyan-200 hover:bg-cyan-50"
          >
            <Palette size={20} className="text-cyan-600 mb-1 group-hover:scale-110 transition-transform" />
            <span className="text-gray-900 text-xs font-medium">我的设计</span>
          </button>
          <button
            onClick={() => {
              selectElement(null);
              navigate(ROUTE_PATHS.publish);
            }}
            className="group flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white p-3 transition-colors hover:border-teal-200 hover:bg-teal-50"
          >
            <Zap size={20} className="text-teal-600 mb-1 group-hover:scale-110 transition-transform" />
            <span className="text-gray-900 text-xs font-medium">视频分发</span>
          </button>
          <button
            onClick={() => {
              selectElement(null);
              setActiveModal('videos');
            }}
            className="group flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white p-3 transition-colors hover:border-cyan-200 hover:bg-cyan-50"
          >
            <Video size={20} className="text-cyan-600 mb-1 group-hover:scale-110 transition-transform" />
            <span className="text-gray-900 text-xs font-medium">案例视频</span>
          </button>
          <button
            onClick={() => {
              selectElement(null);
              navigate(ROUTE_PATHS.videoGeneration);
            }}
            hidden
            style={{ display: 'none' }}
            className="group col-span-2 flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white p-3 transition-colors hover:border-teal-200 hover:bg-teal-50"
          >
            <Sparkles size={20} className="text-teal-600 mb-1 group-hover:scale-110 transition-transform" />
            <span className="text-gray-900 text-xs font-medium">AI视频</span>
          </button>
        </div>
      </div>

      <div className="border-b border-slate-200 px-4 py-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-900">页面管理</h3>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{currentProject.pages.length} 个页面</span>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {currentProject.pages.map((page) => (
          <PageItem
            key={page.id}
            page={page}
            isActive={page.id === currentProject.currentPageId}
            onSelect={() => {
              selectElement(null);
              switchPage(page.id);
            }}
            onDelete={() => {
              if (currentProject.pages.length > 1) {
                deletePage(page.id);
              }
            }}
            onDuplicate={() => duplicatePage(page.id)}
            onStartRename={() => {
              setEditingPageId(page.id);
              setEditingName(page.name);
            }}
            isEditing={editingPageId === page.id}
            editingName={editingName}
            onNameChange={setEditingName}
            onFinishRename={handleFinishRename}
            onCancelRename={() => {
              setEditingPageId(null);
              setEditingName('');
            }}
          />
        ))}
      </div>

      <div className="border-t border-slate-200 p-3">
        <button
          onClick={() => {
            selectElement(null);
            setIsModalOpen(true);
          }}
          className="group flex w-full items-center justify-center space-x-2 rounded-lg border-2 border-dashed border-slate-300 py-3 text-slate-600 transition-colors hover:border-teal-500 hover:bg-teal-50 hover:text-teal-700"
        >
          <Plus size={16} className="group-hover:scale-110 transition-transform" />
          <span className="text-sm font-medium">添加页面</span>
        </button>
      </div>

      <div className="space-y-2 border-t border-slate-200 bg-slate-50 p-3">
        <button
          onClick={() => setShowRechargeModal(true)}
          className="app-button-primary flex w-full items-center justify-center space-x-2 rounded-md px-3 py-2 text-sm font-medium transition-colors"
        >
          <CreditCard size={14} />
          <span>我要充值</span>
        </button>
        <button
          onClick={() => setShowFeedbackModal(true)}
          className="flex w-full items-center justify-center space-x-2 rounded-md bg-slate-700 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
        >
          <MessageCircle size={14} />
          <span>我要反馈</span>
        </button>
      </div>

      <AspectRatioModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSelect={(aspectRatio) => {
          addPage(aspectRatio);
        }}
      />

      <Suspense fallback={<EditorLoadingFallback label="正在加载弹窗..." className="fixed inset-0 z-50" />}>
        {activeModal === 'designs' && <MyDesigns onClose={() => setActiveModal(null)} />}
        <ApplicationTemplates isOpen={activeModal === 'templates'} onClose={() => setActiveModal(null)} />
        {activeModal === 'videos' && <CaseVideos onClose={() => setActiveModal(null)} />}
        {showMyWorks && <MyWorks onClose={() => setShowMyWorks(false)} />}
        <UserFeedbackModal isOpen={showFeedbackModal} onClose={() => setShowFeedbackModal(false)} />
        <RechargeModal isOpen={showRechargeModal} onClose={() => setShowRechargeModal(false)} />
      </Suspense>
    </div>
  );
}
