import React, { useState } from 'react';
import { 
  Eye, 
  EyeOff, 
  Trash2, 
  Edit3, 
  ChevronUp, 
  ChevronDown, 
  User, 
  Type, 
  Image, 
  Video, 
  Music, 
  Zap, 
  Layers,
  Square,
  Mic,
  Subtitles,
  Lock,
  GripVertical
} from 'lucide-react';
import { useStore } from '../store';
import { SceneElement, ElementType } from '../types';

// 元素类型图标映射
const getElementIcon = (type: ElementType) => {
  const iconMap = {
    'digital-human': User,
    'text': Type,
    'title': Type,
    'image': Image,
    'illustration': Image,
    'shape': Square,
    'background': Square,
    'voice': Mic,
    'video': Video,
    'music': Music,
    'flash-text': Zap,
    'subtitle': Subtitles
  };
  return iconMap[type] || Square;
};

// 元素类型名称映射
const getElementTypeName = (type: ElementType) => {
  const nameMap = {
    'digital-human': '数字人',
    'text': '文本',
    'title': '标题',
    'image': '图片',
    'illustration': '插图',
    'shape': '形状',
    'background': '背景',
    'voice': '配音',
    'video': '视频',
    'music': '音乐',
    'flash-text': '闪文',
    'subtitle': '字幕'
  };
  return nameMap[type] || '未知';
};

// 获取元素显示名称
const getElementDisplayName = (element: SceneElement) => {
  const typeName = getElementTypeName(element.type);
  
  // 尝试从properties中获取名称
  if (element.properties.name) {
    return element.properties.name;
  }
  if (element.properties.text) {
    return `${typeName}: ${element.properties.text.substring(0, 20)}${element.properties.text.length > 20 ? '...' : ''}`;
  }
  if (element.properties.title) {
    return `${typeName}: ${element.properties.title}`;
  }
  
  return typeName;
};

interface LayerItemProps {
  element: SceneElement;
  isSelected: boolean;
  onSelect: (elementId: string) => void;
  onToggleVisibility: (elementId: string) => void;
  onMoveUp: (elementId: string) => void;
  onMoveDown: (elementId: string) => void;
  onDelete: (elementId: string) => void;
  onRename: (elementId: string, newName: string) => void;
  // 拖拽排序相关
  draggable?: boolean;
  isDragging?: boolean;
  isDragOver?: boolean;
  onDragStart?: (elementId: string, e: React.DragEvent) => void;
  onDragOver?: (elementId: string, e: React.DragEvent) => void;
  onDrop?: (elementId: string, e: React.DragEvent) => void;
  onDragEnd?: (e: React.DragEvent) => void;
}

const LayerItem: React.FC<LayerItemProps> = ({
  element,
  isSelected,
  onSelect,
  onToggleVisibility,
  onMoveUp,
  onMoveDown,
  onDelete,
  onRename,
  // DnD props
  draggable = false,
  isDragging = false,
  isDragOver = false,
  onDragStart,
  onDragOver,
  onDrop,
  onDragEnd
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState(getElementDisplayName(element));
  const [isVisible, setIsVisible] = useState(element.properties.visible !== false);
  
  const Icon = getElementIcon(element.type);
  
  const handleToggleVisibility = () => {
    const newVisibility = !isVisible;
    setIsVisible(newVisibility);
    onToggleVisibility(element.id);
  };
  
  const handleStartEdit = () => {
    setIsEditing(true);
    setEditName(getElementDisplayName(element));
  };
  
  const handleFinishEdit = () => {
    setIsEditing(false);
    if (editName.trim() && editName !== getElementDisplayName(element)) {
      onRename(element.id, editName.trim());
    }
  };
  
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleFinishEdit();
    } else if (e.key === 'Escape') {
      setIsEditing(false);
      setEditName(getElementDisplayName(element));
    }
  };
  
  return (
    <div
      onClick={() => onSelect(element.id)}
      draggable={draggable}
      onDragStart={(e) => {
        if (!draggable) return;
        e.stopPropagation();
        onDragStart && onDragStart(element.id, e);
      }}
      onDragOver={(e) => {
        if (!draggable) return;
        e.preventDefault();
        e.stopPropagation();
        onDragOver && onDragOver(element.id, e);
      }}
      onDrop={(e) => {
        if (!draggable) return;
        e.preventDefault();
        e.stopPropagation();
        onDrop && onDrop(element.id, e);
      }}
      onDragEnd={(e) => {
        if (!draggable) return;
        e.stopPropagation();
        onDragEnd && onDragEnd(e);
      }}
      className={`flex items-center gap-2 p-2 rounded-lg cursor-pointer transition-colors w-full ${
        isSelected
          ? 'bg-teal-50 border border-teal-500'
          : 'bg-white hover:bg-gray-50 border border-gray-200'
      } ${isDragging ? 'opacity-50' : ''} ${isDragOver ? 'ring-1 ring-teal-500' : ''}`}
    >
      {/* 元素图标 */}
      <div className="flex-shrink-0 flex items-center gap-1">
        <Icon size={16} className={`${
          isSelected ? 'text-teal-600' : 'text-gray-600'
        }`} />
        {/* 背景元素锁定标识 */}
        {element.type === 'background' && (
          <Lock size={12} className="text-orange-500" />
        )}
        {/* 拖拽句柄（仅非背景可拖拽） */}
        {element.type !== 'background' && (
          <span
            className="ml-1 text-gray-400 hover:text-gray-600 cursor-grab active:cursor-grabbing"
            title="拖拽排序"
            onMouseDown={(e) => e.stopPropagation()}
          >
            <GripVertical size={12} />
          </span>
        )}
      </div>
      
      {/* 元素名称 */}
      <div className="w-[45px] flex-shrink-0">
        {isEditing ? (
          <input
            type="text"
            value={editName}
            onChange={(e) => setEditName(e.target.value)}
            onBlur={handleFinishEdit}
            onKeyDown={handleKeyPress}
            className="w-full px-1 py-0.5 bg-white border border-gray-300 rounded text-gray-900 text-sm focus:outline-none focus:border-teal-500"
            autoFocus
            onClick={(e) => e.stopPropagation()}
          />
        ) : (
          <span 
            className={`text-sm truncate block ${
              isSelected ? 'text-gray-900' : 'text-gray-700'
            }`}
            title={getElementDisplayName(element)}
          >
            {getElementDisplayName(element)}
          </span>
        )}
      </div>
      
      {/* 层级显示 */}
      <span className="text-xs text-gray-600 w-[20px] flex-shrink-0 text-center">
        {element.zIndex}
      </span>
      
      {/* 操作按钮 */}
      <div className="flex items-center gap-1 w-[100px] flex-shrink-0 justify-end">
        {/* 可见性切换 */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            handleToggleVisibility();
          }}
          className="p-1 text-gray-600 hover:text-gray-900 transition-colors"
          title={isVisible ? '隐藏' : '显示'}
        >
          {isVisible ? <Eye size={12} /> : <EyeOff size={12} />}
        </button>
        
        {/* 上移 */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            if (element.type !== 'background') {
              onMoveUp(element.id);
            }
          }}
          disabled={element.type === 'background'}
          className={`p-1 transition-colors ${
            element.type === 'background'
              ? 'text-gray-400 cursor-not-allowed'
              : 'text-gray-600 hover:text-gray-900'
          }`}
          title={element.type === 'background' ? '背景层级已锁定' : '上移一层'}
        >
          <ChevronUp size={12} />
        </button>
        
        {/* 下移 */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            if (element.type !== 'background') {
              onMoveDown(element.id);
            }
          }}
          disabled={element.type === 'background'}
          className={`p-1 transition-colors ${
            element.type === 'background'
              ? 'text-gray-400 cursor-not-allowed'
              : 'text-gray-600 hover:text-gray-900'
          }`}
          title={element.type === 'background' ? '背景层级已锁定' : '下移一层'}
        >
          <ChevronDown size={12} />
        </button>
        
        {/* 重命名 */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            handleStartEdit();
          }}
          className="p-1 text-gray-600 hover:text-gray-900 transition-colors"
          title="重命名"
        >
          <Edit3 size={12} />
        </button>
        
        {/* 删除 */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDelete(element.id);
          }}
          className="p-1 text-gray-600 hover:text-red-600 transition-colors"
          title="删除"
        >
          <Trash2 size={12} />
        </button>
      </div>
    </div>
  );
};

interface CompactLayerItemProps {
  element: SceneElement;
  isSelected: boolean;
  isDragging: boolean;
  isDragOver: boolean;
  onSelect: (elementId: string) => void;
  onToggleVisibility: (elementId: string) => void;
  onRename: (elementId: string, newName: string) => void;
  onDragStart: (elementId: string, e: React.DragEvent) => void;
  onDragOver: (elementId: string, e: React.DragEvent) => void;
  onDrop: (elementId: string, e: React.DragEvent) => void;
  onDragEnd: (e: React.DragEvent) => void;
}

const CompactLayerItem: React.FC<CompactLayerItemProps> = ({
  element,
  isSelected,
  isDragging,
  isDragOver,
  onSelect,
  onToggleVisibility,
  onRename,
  onDragStart,
  onDragOver,
  onDrop,
  onDragEnd,
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState(getElementDisplayName(element));
  const Icon = getElementIcon(element.type);
  const isVisible = element.properties.visible !== false;
  const draggable = element.type !== 'background';

  const finishEdit = () => {
    setIsEditing(false);
    const trimmedName = editName.trim();
    if (trimmedName && trimmedName !== getElementDisplayName(element)) {
      onRename(element.id, trimmedName);
    }
  };

  return (
    <div
      draggable={draggable}
      onClick={() => onSelect(element.id)}
      onDragStart={(e) => {
        if (!draggable) return;
        e.stopPropagation();
        onDragStart(element.id, e);
      }}
      onDragOver={(e) => {
        if (!draggable) return;
        e.preventDefault();
        e.stopPropagation();
        onDragOver(element.id, e);
      }}
      onDrop={(e) => {
        if (!draggable) return;
        e.preventDefault();
        e.stopPropagation();
        onDrop(element.id, e);
      }}
      onDragEnd={(e) => {
        if (!draggable) return;
        e.stopPropagation();
        onDragEnd(e);
      }}
      className={`flex w-full items-center gap-1 rounded-md border px-1.5 py-1 transition-colors ${
        isSelected
          ? 'border-teal-500 bg-teal-50 text-teal-700'
          : 'border-slate-200 bg-white text-slate-600 hover:border-teal-200 hover:bg-teal-50/60'
      } ${isDragging ? 'opacity-50' : ''} ${isDragOver ? 'ring-1 ring-teal-500' : ''}`}
      title={getElementDisplayName(element)}
    >
      <GripVertical
        size={12}
        className={`flex-shrink-0 ${draggable ? 'cursor-grab text-slate-400 active:cursor-grabbing' : 'text-slate-200'}`}
      />
      <Icon size={12} className="flex-shrink-0" />

      {isEditing ? (
        <input
          value={editName}
          onChange={(e) => setEditName(e.target.value)}
          onBlur={finishEdit}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              finishEdit();
            } else if (e.key === 'Escape') {
              setIsEditing(false);
              setEditName(getElementDisplayName(element));
            }
          }}
          onClick={(e) => e.stopPropagation()}
          className="min-w-0 flex-1 rounded border border-teal-300 bg-white px-1 py-0.5 text-[11px] leading-4 text-slate-900 outline-none"
          autoFocus
        />
      ) : (
        <span className="min-w-0 flex-1 truncate text-[11px] leading-4">
          {getElementDisplayName(element)}
        </span>
      )}

      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          onToggleVisibility(element.id);
        }}
        className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded text-slate-500 hover:bg-slate-100 hover:text-slate-900"
        title={isVisible ? '隐藏' : '显示'}
      >
        {isVisible ? <Eye size={12} /> : <EyeOff size={12} />}
      </button>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setIsEditing(true);
          setEditName(getElementDisplayName(element));
        }}
        className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded text-slate-500 hover:bg-slate-100 hover:text-slate-900"
        title="重命名"
      >
        <Edit3 size={12} />
      </button>
    </div>
  );
};

interface LayersPanelProps {
  compact?: boolean;
}

const LayersPanel: React.FC<LayersPanelProps> = ({ compact = false }) => {
  const { 
    currentProject, 
    selectedElementId,
    actions: { selectElement, updateElement, deleteElement }
  } = useStore();
  // DnD状态
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [dragOverId, setDragOverId] = useState<string | null>(null);
  
  // 获取当前页面
  const currentPage = currentProject?.pages.find(p => p.id === currentProject.currentPageId);
  
  // 按zIndex从高到低排序元素
  const sortedElements = currentPage?.elements
    ? [...currentPage.elements].sort((a, b) => b.zIndex - a.zIndex)
    : [];
  
  const handleSelectElement = (elementId: string) => {
    selectElement(elementId);
  };
  
  const handleToggleVisibility = (elementId: string) => {
    const element = currentPage?.elements.find(e => e.id === elementId);
    if (element) {
      const newVisibility = element.properties.visible !== false ? false : true;
      updateElement(elementId, {
        properties: {
          ...element.properties,
          visible: newVisibility
        }
      });
    }
  };
  
  /**
   * 上移一层（函数级注释）
   * - 基于当前面板排序（zIndex 从高到低），将元素与其上方元素进行邻接交换
   * - 遇到上方为背景元素时跳过继续向上寻找最近的非背景元素进行交换
   * - 完成交换后，重新为所有元素分配唯一且连续的 zIndex，避免重复
   * - 背景元素不参与上移操作（自身不可上移；他人上移时不会与背景交换）
   */
  const handleMoveUp = (elementId: string) => {
    if (!currentPage) return;
    const list = [...sortedElements];
    const idx = list.findIndex(e => e.id === elementId);
    if (idx <= 0) return; // 已在最顶层或未找到
    const el = list[idx];
    if (el.type === 'background') return; // 背景不允许上移

    // 寻找最近的非背景上方元素
    let targetIndex = idx - 1;
    while (targetIndex >= 0 && list[targetIndex].type === 'background') {
      targetIndex--;
    }
    if (targetIndex < 0) return; // 上方只有背景或没有元素

    // 邻接交换：与目标上方元素互换位置（更靠前即更高层）
    const target = list[targetIndex];
    list[targetIndex] = el;
    list[idx] = target;

    // 重新分配 zIndex：保证唯一并按从高到低递减
    const maxExisting = Math.max(...currentPage.elements.map(e => e.zIndex), list.length);
    for (let i = 0; i < list.length; i++) {
      const targetZ = maxExisting - i;
      if (list[i].zIndex !== targetZ) {
        updateElement(list[i].id, { zIndex: targetZ });
      }
    }
  };
  
  /**
   * 下移一层（函数级注释）
   * - 基于当前面板排序（zIndex 从高到低），将元素与其下方元素进行邻接交换
   * - 遇到下方为背景元素时跳过继续向下寻找最近的非背景元素进行交换
   * - 完成交换后，重新为所有元素分配唯一且连续的 zIndex，避免重复
   * - 背景元素不参与下移操作（自身不可下移；他人下移时不会与背景交换）
   */
  const handleMoveDown = (elementId: string) => {
    if (!currentPage) return;
    const list = [...sortedElements];
    const idx = list.findIndex(e => e.id === elementId);
    if (idx === -1 || idx >= list.length - 1) return; // 已在最底层或未找到
    const el = list[idx];
    if (el.type === 'background') return; // 背景不允许下移

    // 寻找最近的非背景下方元素
    let targetIndex = idx + 1;
    while (targetIndex <= list.length - 1 && list[targetIndex].type === 'background') {
      targetIndex++;
    }
    if (targetIndex > list.length - 1) return; // 下方只有背景或没有元素

    // 邻接交换：与目标下方元素互换位置（更靠后即更低层）
    const target = list[targetIndex];
    list[targetIndex] = el;
    list[idx] = target;

    // 重新分配 zIndex：保证唯一并按从高到低递减
    const maxExisting = Math.max(...currentPage.elements.map(e => e.zIndex), list.length);
    for (let i = 0; i < list.length; i++) {
      const targetZ = maxExisting - i;
      if (list[i].zIndex !== targetZ) {
        updateElement(list[i].id, { zIndex: targetZ });
      }
    }
  };
  
  const handleDelete = (elementId: string) => {
    /**
     * 删除指定元素
     * - 允许删除背景元素：删除后页面可能出现“空背景”状态
     * - 空背景由 Canvas 默认渲染处理，不影响其他图层的显示与操作
     */
    deleteElement(elementId);
  };
  
  const handleRename = (elementId: string, newName: string) => {
    const element = currentPage?.elements.find(e => e.id === elementId);
    if (element) {
      updateElement(elementId, {
        properties: {
          ...element.properties,
          name: newName
        }
      });
    }
  };

  /**
   * 根据拖拽结果重排所有元素的 zIndex
   * - 输入使用当前面板的排序（zIndex 从高到低）
   * - 将 draggingId 的元素插入到 dropTargetId 位置（靠前即更高层级）
   * - 重新分配 zIndex，保持从高到低的顺序，背景层保持其当前位置
   */
  const reorderByDragAndDrop = (draggingId: string, dropTargetId: string) => {
    if (!currentPage) return;
    const list = [...sortedElements];
    const fromIndex = list.findIndex(e => e.id === draggingId);
    const toIndex = list.findIndex(e => e.id === dropTargetId);
    if (fromIndex === -1 || toIndex === -1 || fromIndex === toIndex) return;

    // 背景不可被拖动，保护：若拖拽的是背景则直接返回
    const draggingEl = list[fromIndex];
    if (draggingEl.type === 'background') return;

    // 构建新顺序：将拖拽元素移除并插入到目标索引之前
    list.splice(fromIndex, 1);
    const insertIndex = toIndex; // 插入到目标元素之前 → 更靠前（更高层级）
    list.splice(insertIndex, 0, draggingEl);

    // 计算可用的最大 zIndex（保持不超过当前最大值）
    const maxExisting = Math.max(...currentPage.elements.map(e => e.zIndex), list.length);

    // 重新赋值 zIndex：列表第0个为最高层，依次递减
    for (let i = 0; i < list.length; i++) {
      const targetZ = maxExisting - i;
      const el = list[i];
      if (el.zIndex !== targetZ) {
        updateElement(el.id, { zIndex: targetZ });
      }
    }
  };

  /**
   * 开始拖拽事件处理（函数级注释）
   * - 记录当前拖拽的元素 ID
   * - 使用 HTML5 Drag API 的 dataTransfer 标注移动行为
   */
  const handleDragStart = (elementId: string, e: React.DragEvent) => {
    setDraggingId(elementId);
    setDragOverId(null);
    try {
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', elementId);
    } catch {}
  };

  /**
   * 拖拽经过目标项的处理（函数级注释）
   * - 阻止默认行为以允许放置
   * - 高亮当前潜在放置目标
   */
  const handleDragOverItem = (elementId: string, e: React.DragEvent) => {
    e.preventDefault();
    setDragOverId(elementId);
    try {
      e.dataTransfer.dropEffect = 'move';
    } catch {}
  };

  /**
   * 在目标项上完成放置（函数级注释）
   * - 读取拖拽源 ID，计算并应用新的 zIndex 顺序
   * - 清理拖拽态
   */
  const handleDropOnItem = (elementId: string, e: React.DragEvent) => {
    e.preventDefault();
    const dragged = draggingId || (() => {
      try { return e.dataTransfer.getData('text/plain'); } catch { return null; }
    })();
    if (dragged) {
      reorderByDragAndDrop(dragged, elementId);
    }
    setDraggingId(null);
    setDragOverId(null);
  };

  /**
   * 拖拽结束时的清理（函数级注释）
   */
  const handleDragEnd = (e: React.DragEvent) => {
    setDraggingId(null);
    setDragOverId(null);
  };
  
  if (compact) {
    return (
      <div className="flex min-h-0 w-full flex-1 flex-col">
        <div className="mb-2 flex items-center justify-center gap-1 border-b border-slate-200 pb-2 text-xs font-medium text-slate-700">
          <Layers size={13} />
          <span className="whitespace-nowrap">图层</span>
          <span className="text-slate-400">{sortedElements.length}</span>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {sortedElements.length === 0 ? (
            <div className="px-1 py-3 text-center text-xs text-slate-400">暂无</div>
          ) : (
            <div className="space-y-1">
              {sortedElements.map((element) => (
                <CompactLayerItem
                  key={element.id}
                  element={element}
                  isSelected={selectedElementId === element.id}
                  isDragging={draggingId === element.id}
                  isDragOver={dragOverId === element.id && draggingId !== element.id}
                  onSelect={handleSelectElement}
                  onToggleVisibility={handleToggleVisibility}
                  onRename={handleRename}
                  onDragStart={handleDragStart}
                  onDragOver={handleDragOverItem}
                  onDrop={handleDropOnItem}
                  onDragEnd={handleDragEnd}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* 标题 */}
      <div className="p-4 border-b border-gray-200">
        <div className="flex items-center gap-2 mb-3">
          <Layers size={16} className="text-gray-700" />
          <h3 className="text-gray-900 font-semibold text-sm">图层管理</h3>
        </div>
        
        <div className="text-xs text-gray-600">
          共 {sortedElements.length} 个图层
        </div>
      </div>
      
      {/* 图层列表 */}
      <div className="flex-1 overflow-y-auto p-4 min-h-0">
        {sortedElements.length === 0 ? (
          <div className="text-center text-gray-600 mt-8">
            <div className="text-4xl mb-2">📋</div>
            <p className="text-sm">暂无图层</p>
            <p className="text-xs mt-1">添加元素后将在此显示</p>
          </div>
        ) : (
          <div className="space-y-2">
            {sortedElements.map(element => (
              <LayerItem
                key={element.id}
                element={element}
                isSelected={selectedElementId === element.id}
                onSelect={handleSelectElement}
                onToggleVisibility={handleToggleVisibility}
                onMoveUp={handleMoveUp}
                onMoveDown={handleMoveDown}
                onDelete={handleDelete}
                onRename={handleRename}
                // DnD props
                draggable={element.type !== 'background'}
                isDragging={draggingId === element.id}
                isDragOver={dragOverId === element.id && draggingId !== element.id}
                onDragStart={handleDragStart}
                onDragOver={handleDragOverItem}
                onDrop={handleDropOnItem}
                onDragEnd={handleDragEnd}
              />
            ))}
          </div>
        )}
      </div>
      
      {/* 底部操作提示 */}
      {sortedElements.length > 0 && (
        <div className="p-4 border-t border-gray-200">
          <div className="text-xs text-gray-600 space-y-1">
            <div>• 点击图层选中元素</div>
            <div>• 双击或点击编辑按钮重命名</div>
            <div>• 使用眼睛图标控制可见性</div>
          </div>
        </div>
      )}
      

    </div>
  );
};

export default LayersPanel;
