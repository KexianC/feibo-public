import React, { useEffect, useState } from 'react';
import { Palette, Trash2, Download, Eye, Calendar, Clock, Clock4, X, Edit2, Check, XCircle, Loader2, Grid3X3, Image } from 'lucide-react';
import { useStore } from '../store';
import ThumbnailGenerator from './ThumbnailGenerator';
import { getUserTemplates, deleteTemplate, updateTemplateTitle } from '../services/digitalHumanApi';
import { Template } from '../types';

interface MyDesignsProps {
  onClose?: () => void;
}

const MyDesigns: React.FC<MyDesignsProps> = ({ onClose }) => {
  const { actions } = useStore();
  const [templates, setTemplates] = useState<Template[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDraft, setSelectedDraft] = useState<Template | null>(null);
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingTitle, setEditingTitle] = useState('');
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'grid' | 'preview'>(() => {
    return (localStorage.getItem('myDesigns_viewMode') as 'grid' | 'preview') || 'grid';
  });

  // 加载设计草稿列表
  const loadTemplates = async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await getUserTemplates();
      
      // 确保返回的数据结构正确
      if (result && Array.isArray(result.templates)) {
        setTemplates(result.templates);
        console.log('成功加载设计草稿列表:', result.templates.length, '个草稿');
      } else {
        console.warn('getUserTemplates返回的数据格式不正确');
        setTemplates([]);
        setError('获取到的数据格式不正确');
      }
    } catch (err) {
      console.error('加载设计草稿失败:', err);
      setError(err instanceof Error ? err.message : '加载设计草稿失败');
      // 确保在错误情况下templates也是数组
      setTemplates([]);
    } finally {
      setLoading(false);
    }
  };

  // 组件加载时获取设计草稿列表
  useEffect(() => {
    loadTemplates();
  }, []);

  /**
   * 加载设计草稿到画布
   * 解析草稿数据并使用store的loadProject方法加载到当前项目
   */
  const handleLoadDraft = async (template: Template) => {
    try {
      setLoading(true);
      
      // 解析body中的项目数据
      const projectData = JSON.parse(template.body);
      if (projectData.project) {
        // 使用store的loadProject方法加载项目数据
        actions.loadProject(projectData.project);
        
        console.log('成功加载设计草稿，标题长度:', template.title?.length ?? 0);
        
        // 关闭模态窗口
        if (onClose) {
          onClose();
        }
      } else {
        throw new Error('草稿数据中缺少项目信息');
      }
    } catch (err) {
      console.error('加载设计草稿失败:', err);
      setError(err instanceof Error ? err.message : '加载设计草稿失败');
    } finally {
      setLoading(false);
    }
  };

  // 开始编辑标题
  const handleStartEdit = (template: Template, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingId(template.digitalTemplateId);
    setEditingTitle(template.title);
  };

  // 保存标题修改
  const handleSaveEdit = async (templateId: string) => {
    if (!editingTitle.trim()) {
      alert('标题不能为空');
      return;
    }

    try {
      await updateTemplateTitle(templateId, editingTitle.trim());
      
      // 更新本地数据
      setTemplates(prev => prev.map(t => 
        t.digitalTemplateId === templateId 
          ? { ...t, title: editingTitle.trim() }
          : t
      ));
      
      setEditingId(null);
      setEditingTitle('');
      alert('标题修改成功');
    } catch (err) {
      console.error('修改标题失败:', err);
      alert('修改失败: ' + (err instanceof Error ? err.message : '未知错误'));
    }
  };

  // 取消编辑
  const handleCancelEdit = () => {
    setEditingId(null);
    setEditingTitle('');
  };

  // 删除设计草稿
  const handleDeleteDraft = async (templateId: string, templateTitle: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm(`确定要删除设计草稿 "${templateTitle}" 吗？此操作无法撤销。`)) {
      return;
    }

    try {
      setDeletingId(templateId);
      await deleteTemplate(templateId);
      
      // 从本地数据中移除
      setTemplates(prev => prev.filter(t => t.digitalTemplateId !== templateId));
      
      alert('删除成功');
    } catch (err) {
      console.error('删除设计草稿失败:', err);
      alert('删除失败: ' + (err instanceof Error ? err.message : '未知错误'));
    } finally {
      setDeletingId(null);
    }
  };

  // 预览设计草稿
  const handlePreviewDraft = (template: Template, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedDraft(template);
    setIsPreviewOpen(true);
  };

  // 格式化时间
  const formatTime = (timeStr: string) => {
    try {
      const date = new Date(timeStr);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      
      if (diffDays === 0) {
        return '今天';
      } else if (diffDays === 1) {
        return '昨天';
      } else if (diffDays < 7) {
        return `${diffDays}天前`;
      } else {
        return date.toLocaleDateString('zh-CN');
      }
    } catch {
      return timeStr;
    }
  };

  // 获取设计草稿的元素统计
  const getElementStats = (template: Template) => {
    try {
      const projectData = JSON.parse(template.body);
      const project = projectData.project;
      
      if (!project || !project.pages || !project.pages[0] || !project.pages[0].elements) {
        return { digitalHuman: 0, text: 0, image: 0, background: 0, other: 0 };
      }
      
      const stats = {
        digitalHuman: 0,
        text: 0,
        image: 0,
        background: 0,
        other: 0
      };
      
      project.pages[0].elements.forEach((element: any) => {
        if (element.type === 'digital-human') {
          stats.digitalHuman++;
        } else if (['text', 'title', 'flash-text', 'subtitle'].includes(element.type)) {
          stats.text++;
        } else if (['image', 'illustration'].includes(element.type)) {
          stats.image++;
        } else if (element.type === 'background') {
          stats.background++;
        } else {
          stats.other++;
        }
      });
      
      return stats;
    } catch {
      return { digitalHuman: 0, text: 0, image: 0, background: 0, other: 0 };
    }
  };

  // 获取设计草稿的宽高比
  const getAspectRatio = (template: Template) => {
    try {
      const projectData = JSON.parse(template.body);
      const project = projectData.project;
      return project?.pages?.[0]?.aspectRatio || '16:9';
    } catch {
      return '16:9';
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full mx-4 max-h-[80vh] flex flex-col">
        {/* 标题栏 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <div className="flex items-center space-x-2">
            <Palette className="text-teal-500" size={24} />
            <h2 className="text-xl font-semibold text-gray-900">我的设计</h2>
            <span className="text-sm text-gray-500">({Array.isArray(templates) ? templates.length : 0} 个草稿)</span>
          </div>
          <div className="flex items-center space-x-3">
            {/* 视图切换按钮 */}
            <div className="flex items-center bg-gray-100 rounded-lg p-1">
              <button
                onClick={() => {
                  setViewMode('grid');
                  localStorage.setItem('myDesigns_viewMode', 'grid');
                }}
                className={`p-2 rounded-md transition-colors ${
                  viewMode === 'grid'
                    ? 'bg-white text-teal-600 shadow-sm'
                    : 'text-gray-600 hover:text-gray-800'
                }`}
                title="网格视图"
              >
                <Grid3X3 size={16} />
              </button>
              <button
                onClick={() => {
                  setViewMode('preview');
                  localStorage.setItem('myDesigns_viewMode', 'preview');
                }}
                className={`p-2 rounded-md transition-colors ${
                  viewMode === 'preview'
                    ? 'bg-white text-teal-600 shadow-sm'
                    : 'text-gray-600 hover:text-gray-800'
                }`}
                title="预览视图"
              >
                <Image size={16} />
              </button>
            </div>
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-600 transition-colors"
            >
              <X size={24} />
            </button>
          </div>
        </div>

        {/* 内容区域 */}
        <div className="flex-1 overflow-y-auto p-6">
          {/* 视图模式提示 */}
          {!loading && !error && Array.isArray(templates) && templates.length > 0 && (
            <div className="mb-4 text-sm text-gray-600">
              {viewMode === 'grid' ? (
                <span>网格视图 - 快速浏览所有设计草稿</span>
              ) : (
                <span>预览视图 - 查看设计草稿的详细预览</span>
              )}
            </div>
          )}
          
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="text-center">
                <Loader2 className="w-8 h-8 text-teal-600 animate-spin mx-auto mb-2" />
                <p className="text-gray-600">加载中...</p>
              </div>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-12">
              <div className="text-center">
                <XCircle className="w-8 h-8 text-red-500 mx-auto mb-2" />
                <p className="text-red-600 mb-4">{error}</p>
                <button
                  onClick={loadTemplates}
                  className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
                >
                  重试
                </button>
              </div>
            </div>
          ) : !Array.isArray(templates) || templates.length === 0 ? (
            <div className="text-center py-12">
              <Palette className="mx-auto text-gray-300 mb-4" size={64} />
              <h3 className="text-lg font-medium text-gray-900 mb-2">还没有设计草稿</h3>
              <p className="text-gray-500 mb-4">在编辑器中创建设计后，点击保存按钮来保存您的设计草稿</p>
              <div className="text-sm text-gray-400">
                <span>💡 提示：您可以使用上方的视图切换按钮来选择不同的浏览方式</span>
              </div>
            </div>
          ) : (
            <div className={`transition-all duration-500 ease-in-out ${
              viewMode === 'grid' 
                ? 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4'
                : 'grid grid-cols-1 lg:grid-cols-2 gap-6'
            }`}>
              {Array.isArray(templates) && templates.map((template) => {
                const stats = getElementStats(template);
                const aspectRatio = getAspectRatio(template);
                const isEditing = editingId === template.digitalTemplateId;
                const isDeleting = deletingId === template.digitalTemplateId;
                
                return (
                  <div
                    key={template.digitalTemplateId}
                    className={`bg-white border border-gray-200 rounded-lg hover:shadow-md transition-all duration-300 cursor-pointer group ${
                      viewMode === 'grid' ? 'p-4' : 'p-6'
                    }`}
                    onClick={() => !isEditing && !isDeleting && handleLoadDraft(template)}
                  >
                    {/* 草稿预览区域 */}
                    <div className={`bg-gray-100 rounded-lg mb-3 flex items-center justify-center relative overflow-hidden ${
                      viewMode === 'grid' 
                        ? 'aspect-video' 
                        : aspectRatio === '16:9' 
                          ? 'aspect-video' 
                          : 'aspect-[9/16] max-h-96'
                    }`}>
                      {(() => {
                        try {
                          const projectData = JSON.parse(template.body);
                          const project = projectData.project;
                          const elements = project?.pages?.[0]?.elements || [];
                          
                          if (elements.length > 0) {
                            const previewWidth = viewMode === 'grid' ? 280 : 600;
                            const previewHeight = viewMode === 'grid' 
                              ? (aspectRatio === '16:9' ? 157 : 280)
                              : (aspectRatio === '16:9' ? 337 : 600);
                            
                            return (
                              <ThumbnailGenerator
                                elements={elements}
                                aspectRatio={aspectRatio}
                                width={previewWidth}
                                height={previewHeight}
                              />
                            );
                          }
                        } catch (error) {
                          console.warn('解析草稿预览数据失败:', error);
                        }
                        
                        // 默认显示图标
                        return (
                          <div className="text-gray-400 text-center">
                            <Palette size={viewMode === 'grid' ? 32 : 48} className="mx-auto mb-2" />
                            <div className={viewMode === 'grid' ? 'text-xs' : 'text-sm'}>
                              {aspectRatio === '16:9' ? '横屏' : '竖屏'}
                            </div>
                          </div>
                        );
                      })()}
                      
                      {/* 悬停时显示的操作按钮 */}
                      {!isEditing && !isDeleting && (
                        <div className="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                          <div className="flex space-x-2">
                            <button
                              onClick={(e) => handlePreviewDraft(template, e)}
                              className="p-2 bg-white rounded-full text-gray-700 hover:bg-gray-100 transition-colors"
                              title="预览"
                            >
                              <Eye size={16} />
                            </button>
                            <button
                              onClick={(e) => handleStartEdit(template, e)}
                              className="p-2 bg-white rounded-full text-blue-600 hover:bg-blue-50 transition-colors"
                              title="编辑标题"
                            >
                              <Edit2 size={16} />
                            </button>
                            <button
                              onClick={(e) => handleDeleteDraft(template.digitalTemplateId, template.title, e)}
                              className="p-2 bg-white rounded-full text-red-600 hover:bg-red-50 transition-colors"
                              title="删除"
                            >
                              <Trash2 size={16} />
                            </button>
                          </div>
                        </div>
                      )}
                      
                      {/* 删除中的加载状态 */}
                      {isDeleting && (
                        <div className="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center">
                          <Loader2 className="w-6 h-6 text-white animate-spin" />
                        </div>
                      )}
                    </div>

                    {/* 草稿信息 */}
                    <div className={`space-y-${viewMode === 'grid' ? '2' : '3'}`}>
                      {/* 标题编辑 */}
                      {isEditing ? (
                        <div className="flex items-center space-x-2">
                          <input
                            type="text"
                            value={editingTitle}
                            onChange={(e) => setEditingTitle(e.target.value)}
                            className={`flex-1 px-2 py-1 border border-gray-300 rounded focus:outline-none focus:border-teal-500 ${
                              viewMode === 'grid' ? 'text-sm' : 'text-base'
                            }`}
                            onKeyPress={(e) => {
                              if (e.key === 'Enter') {
                                handleSaveEdit(template.digitalTemplateId);
                              } else if (e.key === 'Escape') {
                                handleCancelEdit();
                              }
                            }}
                            autoFocus
                          />
                          <button
                            onClick={() => handleSaveEdit(template.digitalTemplateId)}
                            className="p-1 text-green-600 hover:bg-green-50 rounded transition-colors"
                            title="保存"
                          >
                            <Check size={viewMode === 'grid' ? 14 : 16} />
                          </button>
                          <button
                            onClick={handleCancelEdit}
                            className="p-1 text-gray-600 hover:bg-gray-50 rounded transition-colors"
                            title="取消"
                          >
                            <X size={viewMode === 'grid' ? 14 : 16} />
                          </button>
                        </div>
                      ) : (
                        <h3 className={`font-medium text-gray-900 truncate ${
                          viewMode === 'grid' ? 'text-base' : 'text-lg'
                        }`} title={template.title}>
                          {template.title}
                        </h3>
                      )}
                      
                      {/* 元素统计 */}
                      <div className={`flex items-center space-x-3 text-gray-500 ${
                        viewMode === 'grid' ? 'text-xs' : 'text-sm'
                      }`}>
                        {stats.digitalHuman > 0 && (
                          <span className="flex items-center space-x-1">
                            <span className="w-2 h-2 bg-blue-500 rounded-full"></span>
                            <span>{stats.digitalHuman} 数字人</span>
                          </span>
                        )}
                        {stats.text > 0 && (
                          <span className="flex items-center space-x-1">
                            <span className="w-2 h-2 bg-green-500 rounded-full"></span>
                            <span>{stats.text} 文本</span>
                          </span>
                        )}
                        {stats.image > 0 && (
                          <span className="flex items-center space-x-1">
                            <span className="w-2 h-2 bg-purple-500 rounded-full"></span>
                            <span>{stats.image} 图片</span>
                          </span>
                        )}
                        {stats.background > 0 && (
                          <span className="flex items-center space-x-1">
                            <span className="w-2 h-2 bg-orange-500 rounded-full"></span>
                            <span>{stats.background} 背景</span>
                          </span>
                        )}
                      </div>
                      
                      {/* 时间信息 */}
                      <div className={`flex items-center justify-between text-gray-400 ${
                        viewMode === 'grid' ? 'text-xs' : 'text-sm'
                      }`}>
                        <div className="flex items-center space-x-1">
                          <Calendar size={viewMode === 'grid' ? 12 : 14} />
                          <span>{formatTime(template.createTime)}</span>
                        </div>
                        {template.updateTime && (
                          <div className="flex items-center space-x-1">
                            <Clock4 size={viewMode === 'grid' ? 12 : 14} />
                            <span>{new Date(template.updateTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</span>
                          </div>
                        )}
                      </div>
                      
                      {/* 预览模式下的额外信息 */}
                      {viewMode === 'preview' && (
                        <div className="pt-2 border-t border-gray-100">
                          <div className="text-sm text-gray-600">
                            <span className="font-medium">画面比例：</span>
                            <span className="ml-1">{aspectRatio === '16:9' ? '横屏 (16:9)' : '竖屏 (9:16)'}</span>
                          </div>
                          <div className="text-sm text-gray-600 mt-1">
                            <span className="font-medium">元素总数：</span>
                            <span className="ml-1">{stats.digitalHuman + stats.text + stats.image + stats.background} 个</span>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* 底部操作栏 */}
        <div className="border-t border-gray-200 p-4">
          <div className="flex items-center justify-between">
            <div className="text-sm text-gray-500">
              点击草稿卡片可加载到画布进行编辑
            </div>
            <button
              onClick={onClose}
              className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors"
            >
              关闭
            </button>
          </div>
        </div>
      </div>

      {/* 预览模态窗口 */}
      {isPreviewOpen && selectedDraft && (() => {
        try {
          const projectData = JSON.parse(selectedDraft.body);
          const project = projectData.project;
          const script = projectData.script || project?.pages?.[0]?.script;
          const elements = project?.pages?.[0]?.elements || [];
          const aspectRatio = project?.pages?.[0]?.aspectRatio || '16:9';
          
          return (
            <div className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-60">
              <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[80vh] flex flex-col">
                <div className="flex items-center justify-between p-4 border-b border-gray-200">
                  <h3 className="text-lg font-semibold text-gray-900">预览: {selectedDraft.title}</h3>
                  <button
                    onClick={() => setIsPreviewOpen(false)}
                    className="text-gray-400 hover:text-gray-600 transition-colors"
                  >
                    <X size={20} />
                  </button>
                </div>
                <div className="flex-1 p-4 overflow-y-auto">
                  <div className="space-y-4">
                    <div>
                      <h4 className="font-medium text-gray-900 mb-2">基本信息</h4>
                      <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                          <span className="text-gray-500">比例：</span>
                          <span className="text-gray-900">{aspectRatio === '16:9' ? '横屏 (16:9)' : '竖屏 (9:16)'}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">元素数量：</span>
                          <span className="text-gray-900">{elements.length} 个</span>
                        </div>
                        <div>
                          <span className="text-gray-500">创建时间：</span>
                          <span className="text-gray-900">{formatTime(selectedDraft.createTime)}</span>
                        </div>
                        {selectedDraft.updateTime && (
                          <div>
                            <span className="text-gray-500">更新时间：</span>
                            <span className="text-gray-900">{formatTime(selectedDraft.updateTime)}</span>
                          </div>
                        )}
                      </div>
                    </div>
                    
                    {script && (
                      <div>
                        <h4 className="font-medium text-gray-900 mb-2">文案内容</h4>
                        <div className="bg-gray-50 rounded-lg p-3 text-sm text-gray-700">
                          {script}
                        </div>
                      </div>
                    )}
                    
                    <div>
                      <h4 className="font-medium text-gray-900 mb-2">元素列表</h4>
                      <div className="space-y-2">
                        {elements.map((element: any, index: number) => (
                          <div key={element.id} className="flex items-center justify-between bg-gray-50 rounded-lg p-3">
                            <div className="flex items-center space-x-3">
                              <span className="text-xs bg-gray-200 text-gray-600 px-2 py-1 rounded">
                                {index + 1}
                              </span>
                              <span className="text-sm font-medium text-gray-900">
                                {element.type === 'digital-human' ? '数字人' :
                                 element.type === 'background' ? '背景' :
                                 element.type === 'text' ? '文本' :
                                 element.type === 'title' ? '标题' :
                                 element.type === 'flash-text' ? '闪文' :
                                 element.type === 'subtitle' ? '字幕' :
                                 element.type === 'image' ? '图片' :
                                 element.type === 'illustration' ? '插图' :
                                 element.type}
                              </span>
                            </div>
                            <div className="text-xs text-gray-500">
                              {element.size?.width || 0} × {element.size?.height || 0}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
                <div className="border-t border-gray-200 p-4 flex justify-end space-x-3">
                  <button
                    onClick={() => setIsPreviewOpen(false)}
                    className="px-4 py-2 text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors"
                  >
                    关闭
                  </button>
                  <button
                    onClick={() => {
                      handleLoadDraft(selectedDraft);
                      setIsPreviewOpen(false);
                    }}
                    className="px-4 py-2 bg-teal-600 hover:bg-teal-700 text-white rounded-lg transition-colors"
                  >
                    加载到画布
                  </button>
                </div>
              </div>
            </div>
          );
        } catch (error) {
          console.error('解析预览数据失败:', error);
          return (
            <div className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-60">
              <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-6">
                <div className="text-center">
                  <XCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
                  <h3 className="text-lg font-semibold text-gray-900 mb-2">预览失败</h3>
                  <p className="text-gray-600 mb-4">设计草稿数据格式错误</p>
                  <button
                    onClick={() => setIsPreviewOpen(false)}
                    className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors"
                  >
                    关闭
                  </button>
                </div>
              </div>
            </div>
          );
        }
      })()}
    </div>
  );
};

export default MyDesigns;
