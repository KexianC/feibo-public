import React, { useState } from 'react';
import { X, Search, Play, Download, Star, Tag } from 'lucide-react';
import ModalPortal from './ModalPortal';
import { ApplicationTemplate, TemplateCategory } from '../types';
import { useStore } from '../store';
import { predefinedTemplates } from '../data/templates';

interface ApplicationTemplatesProps {
  isOpen: boolean;
  onClose: () => void;
}

const ApplicationTemplates: React.FC<ApplicationTemplatesProps> = ({ isOpen, onClose }) => {
  const { currentProject, actions: { addElement, updateElement, deleteElement, getNextZIndex, saveToHistory, addPage, switchPage, renamePage, updatePageScript } } = useStore();
  const [selectedCategory, setSelectedCategory] = useState<TemplateCategory | 'all'>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedTemplate, setSelectedTemplate] = useState<ApplicationTemplate | null>(null);
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);

  // 模板分类
  const categories: { key: TemplateCategory | 'all'; label: string; icon: string }[] = [
    { key: 'all', label: '全部', icon: '🎯' },
    { key: 'business', label: '商务演示', icon: '💼' },
    { key: 'education', label: '教育培训', icon: '📚' },
    { key: 'product', label: '产品介绍', icon: '📱' },
    { key: 'news', label: '新闻播报', icon: '📺' },
    { key: 'entertainment', label: '娱乐内容', icon: '🎬' }
  ];

  // 使用预设模板数据
  const templates = predefinedTemplates;

  // 筛选模板
  const filteredTemplates = templates.filter(template => {
    const matchesCategory = selectedCategory === 'all' || template.category === selectedCategory;
    const matchesSearch = searchTerm === '' || 
      template.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      template.description.toLowerCase().includes(searchTerm.toLowerCase()) ||
      template.tags.some(tag => tag.toLowerCase().includes(searchTerm.toLowerCase()));
    return matchesCategory && matchesSearch;
  });

  // 应用模板
  /**
   * 应用模板到新页面
   * 根据模板的aspectRatio自动判断横竖屏，在新页面中应用模板元素
   * @param template 要应用的模板
   */
  const applyTemplate = (template: ApplicationTemplate) => {
    if (!currentProject) {
      console.error('没有当前项目');
      return;
    }

    // 保存当前状态到历史记录
    saveToHistory(currentProject);

    try {
      // 根据模板的aspectRatio创建新页面
      const newPageId = addPage(template.aspectRatio);
      
      if (newPageId === null) {
        console.error('创建新页面失败');
        return;
      }

      // 切换到新创建的页面
      switchPage(newPageId);

      // 等待页面切换完成后再添加元素
      setTimeout(() => {
        // 添加模板中的所有元素
        template.elements.forEach((templateElement, index) => {
          // 为每个元素生成新的ID
          const newElement = {
            ...templateElement,
            id: `template-${template.id}-${index}-${Date.now()}`,
            zIndex: templateElement.type === 'background' ? 1 : getNextZIndex(templateElement.type)
          };
          
          addElement(newElement);
        });

        // 如果模板有预设文案，更新页面文案
        if (template.script) {
          updatePageScript(template.script);
          console.log('应用模板文案，长度:', template.script.length);
        }

        // 重命名新页面为有意义的名称
        renamePage(newPageId, `模板页面 - ${template.name}`);

        console.log(`模板"${template.name}"已成功应用到新页面 (${template.aspectRatio})`);
      }, 100);
      
    } catch (error) {
      console.error('应用模板失败:', error);
    }

    onClose();
  };

  // 预览模板
  const previewTemplate = (template: ApplicationTemplate) => {
    setSelectedTemplate(template);
    setIsPreviewOpen(true);
  };

  if (!isOpen) return null;

  return (
    <ModalPortal>
      <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-6">
      <div className="my-auto flex h-[85vh] max-h-[calc(100vh-2rem)] w-full max-w-6xl flex-col overflow-hidden rounded-lg bg-white shadow-xl">
        {/* 头部 */}
        <div className="flex flex-shrink-0 items-center justify-between border-b border-gray-200 p-6">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">应用模板</h2>
            <p className="text-gray-600 mt-1">选择一个模板快速开始您的项目</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <X size={24} className="text-gray-500" />
          </button>
        </div>

        {/* 搜索和筛选 */}
        <div className="flex-shrink-0 border-b border-gray-200 p-6">
          <div className="flex items-center space-x-4 mb-4">
            <div className="relative flex-1">
              <Search size={20} className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                placeholder="搜索模板..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-transparent"
              />
            </div>
          </div>
          
          {/* 分类标签 */}
          <div className="flex flex-wrap gap-2">
            {categories.map(category => (
              <button
                key={category.key}
                onClick={() => setSelectedCategory(category.key)}
                className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${
                  selectedCategory === category.key
                    ? 'bg-teal-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                <span className="mr-2">{category.icon}</span>
                {category.label}
              </button>
            ))}
          </div>
        </div>

        {/* 模板网格 */}
        <div className="min-h-0 flex-1 overflow-y-auto p-6">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {filteredTemplates.map(template => (
              <div key={template.id} className="bg-white border border-gray-200 rounded-lg overflow-hidden hover:shadow-lg transition-shadow group">
                {/* 模板预览图 */}
                <div className="relative aspect-video bg-gray-100">
                  <img
                    src={template.thumbnail}
                    alt={template.name}
                    className="w-full h-full object-cover"
                  />
                  {template.isPopular && (
                    <div className="absolute top-2 left-2 bg-orange-500 text-white px-2 py-1 rounded-full text-xs font-medium flex items-center">
                      <Star size={12} className="mr-1" />
                      热门
                    </div>
                  )}
                  <div className="absolute top-2 right-2 bg-black bg-opacity-50 text-white px-2 py-1 rounded text-xs">
                    {template.aspectRatio}
                  </div>
                  
                  {/* 悬停操作按钮 */}
                  <div className="absolute inset-0 bg-black bg-opacity-0 group-hover:bg-opacity-30 transition-all duration-200 flex items-center justify-center opacity-0 group-hover:opacity-100">
                    <div className="flex space-x-2">
                      <button
                        onClick={() => previewTemplate(template)}
                        className="bg-cyan-600 text-white px-3 py-2 rounded-lg hover:bg-cyan-700 transition-colors flex items-center space-x-1"
                      >
                        <Play size={16} />
                        <span>预览</span>
                      </button>
                      <button
                        onClick={() => applyTemplate(template)}
                        className="bg-teal-600 text-white px-3 py-2 rounded-lg hover:bg-teal-700 transition-colors flex items-center space-x-1"
                      >
                        <Download size={16} />
                        <span>应用</span>
                      </button>
                    </div>
                  </div>
                </div>
                
                {/* 模板信息 */}
                <div className="p-4">
                  <h3 className="font-semibold text-gray-900 mb-2">{template.name}</h3>
                  <p className="text-gray-600 text-sm mb-3 line-clamp-2">{template.description}</p>
                  
                  {/* 标签 */}
                  <div className="flex flex-wrap gap-1">
                    {template.tags.slice(0, 3).map(tag => (
                      <span key={tag} className="inline-flex items-center px-2 py-1 bg-gray-100 text-gray-700 text-xs rounded">
                        <Tag size={10} className="mr-1" />
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
          
          {filteredTemplates.length === 0 && (
            <div className="text-center py-12">
              <div className="text-gray-400 text-6xl mb-4">🔍</div>
              <h3 className="text-lg font-medium text-gray-900 mb-2">未找到匹配的模板</h3>
              <p className="text-gray-600">尝试调整搜索条件或选择其他分类</p>
            </div>
          )}
        </div>
      </div>
      
      {/* 模板预览模态窗口 */}
      {isPreviewOpen && selectedTemplate && (
        <div className="fixed inset-0 z-[60] flex items-start justify-center overflow-y-auto bg-black/75 p-4 sm:p-6">
          <div className="my-auto flex w-full max-w-4xl max-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-lg bg-white shadow-xl">
            <div className="flex flex-shrink-0 items-center justify-between border-b border-gray-200 p-4">
              <h3 className="text-lg font-semibold">{selectedTemplate.name}</h3>
              <button
                onClick={() => setIsPreviewOpen(false)}
                className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
              >
                <X size={20} className="text-gray-500" />
              </button>
            </div>
            <div className="min-h-0 overflow-y-auto p-6">
              <div className="aspect-video bg-gray-100 rounded-lg mb-4">
                <img
                  src={selectedTemplate.thumbnail}
                  alt={selectedTemplate.name}
                  className="w-full h-full object-cover rounded-lg"
                />
              </div>
              <div className="space-y-4">
                <div>
                  <h4 className="font-medium text-gray-900 mb-2">描述</h4>
                  <p className="text-gray-600">{selectedTemplate.description}</p>
                </div>
                {selectedTemplate.script && (
                  <div>
                    <h4 className="font-medium text-gray-900 mb-2">预设文案</h4>
                    <p className="text-gray-600 bg-gray-50 p-3 rounded-lg">{selectedTemplate.script}</p>
                  </div>
                )}
                <div className="flex justify-end space-x-3">
                  <button
                    onClick={() => setIsPreviewOpen(false)}
                    className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
                  >
                    取消
                  </button>
                  <button
                    onClick={() => {
                      applyTemplate(selectedTemplate);
                      setIsPreviewOpen(false);
                    }}
                    className="px-4 py-2 bg-cyan-600 text-white rounded-lg hover:bg-cyan-700 transition-colors"
                  >
                    应用模板
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
      </div>
    </ModalPortal>
  );
};

export default ApplicationTemplates;
