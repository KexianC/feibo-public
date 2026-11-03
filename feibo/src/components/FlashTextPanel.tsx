import React, { useState, useEffect } from 'react';
import { Plus, Save } from 'lucide-react';
import { useStore } from '../store';
import { v4 as uuidv4 } from 'uuid';
import { SceneElement } from '../types';
import { getTypefaceOptions } from '../services/typefaceApi';

interface FlashTextPanelProps {
  isEditMode?: boolean;
  selectedElement?: SceneElement;
}

const FlashTextPanel: React.FC<FlashTextPanelProps> = ({ isEditMode = false, selectedElement }) => {
  const { actions: { addElement, getNextZIndex, updateElement } } = useStore();
  const [text, setText] = useState('');
  const [fontSize, setFontSize] = useState(60);
  const [fontFamily, setFontFamily] = useState('Source Han Sans SC Medium');
  const [fontOptions, setFontOptions] = useState<{label:string;value:string}[]>([]);
  const [startTime, setStartTime] = useState(0);
  const [endTime, setEndTime] = useState(10);
  const [textColor, setTextColor] = useState('#000000');

  // 编辑模式下，从选中元素初始化状态
  useEffect(() => {
    if (isEditMode && selectedElement && selectedElement.type === 'flash-text') {
      const props = selectedElement.properties;
      setText(props.text || '');
      setFontSize(props.fontSize || 60);
      setFontFamily(props.fontFamily || '宋体');
      setStartTime(props.startTime || 0);
      setEndTime(props.endTime || 10);
      setTextColor(props.textColor || '#000000');
    } else if (!isEditMode) {
      // 非编辑模式下重置为默认值
      setText('');
      setFontSize(60);
      setFontFamily('宋体');
      setStartTime(0);
      setEndTime(10);
      setTextColor('#000000');
    }
  }, [isEditMode, selectedElement?.id]); // 移除selectedElement?.properties依赖，避免实时更新时重新初始化

  useEffect(() => {
    const fonts = getTypefaceOptions();
    setFontOptions(fonts);
    const preferred = fonts.find(f => f.label.includes('思源黑体') || f.value === 'Source Han Sans SC Medium');
    if (preferred) {
      setFontFamily(preferred.value);
    }
  }, []);



  const addFlashTextToCanvas = () => {
    if (!text.trim()) return;

    const currentProject = useStore.getState().currentProject;
    const currentPage = currentProject?.pages.find(p => p.id === currentProject.currentPageId);
    const aspectRatio = currentPage?.aspectRatio || '16:9';
    
    let canvasWidth, canvasHeight;
    if (aspectRatio === '9:16') {
      canvasWidth = 1080;
      canvasHeight = 1920;
    } else {
      canvasWidth = 1920;
      canvasHeight = 1080;
    }

    const element = {
      id: uuidv4(),
      type: 'flash-text' as const,
      position: { x: canvasWidth / 2 - 500, y: canvasHeight / 2 - 100 },
      size: { width: 1000, height: 200 },
      rotation: 0,
      zIndex: getNextZIndex('flash-text'),
      properties: {
        text,
        fontSize,
        fontFamily,
        textColor,
        startTime,
        endTime
      }
    };

    addElement(element);
  };

  const handleUpdateFlashText = () => {
    if (isEditMode && selectedElement) {
      const updatedProperties = {
        text,
        fontSize,
        fontFamily,
        textColor,
        startTime,
        endTime
      };
      
      updateElement(selectedElement.id, { properties: updatedProperties });
    }
  };

  // 实时更新选中元素
  useEffect(() => {
    if (isEditMode && selectedElement) {
      const updatedProperties = {
        text,
        fontSize,
        fontFamily,
        textColor,
        startTime,
        endTime
      };
      
      updateElement(selectedElement.id, { properties: updatedProperties });
    }
  }, [text, fontSize, fontFamily, textColor, startTime, endTime, isEditMode, selectedElement?.id]);



  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* 标题 */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="text-gray-900 font-semibold text-sm">
          {isEditMode ? '编辑闪文' : '闪文设置'}
        </h3>
      </div>

      {/* 内容区域 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* 内容输入 */}
        <div>
          <label className="block text-gray-700 text-sm font-medium mb-2">内容</label>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="内容"
            className="w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 placeholder-gray-500 focus:outline-none focus:border-teal-500 text-sm resize-none"
            rows={3}
          />
        </div>

        {/* 字符大小 */}
        <div>
          <label className="block text-gray-700 text-sm font-medium mb-2">字符大小</label>
          <input
            type="number"
            min="12"
            max="72"
            value={fontSize}
            onChange={(e) => setFontSize(Number(e.target.value))}
            className="w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 focus:outline-none focus:border-teal-500 text-sm"
          />
        </div>

        {/* 字体 */}
        <div>
          <label className="block text-gray-700 text-sm font-medium mb-2">字体</label>
          <select
            value={fontFamily}
            onChange={(e) => setFontFamily(e.target.value)}
            className="w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 focus:outline-none focus:border-teal-500 text-sm"
          >
            {fontOptions.map(font => (
              <option key={font.value} value={font.value}>{font.label}</option>
            ))}
          </select>
        </div>

        {/* 开始时间 */}
        <div>
          <label className="block text-gray-700 text-sm font-medium mb-2">开始时间</label>
          <input
            type="number"
            step="0.1"
            value={startTime}
            onChange={(e) => setStartTime(Number(e.target.value))}
            className="w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 focus:outline-none focus:border-teal-500 text-sm"
            placeholder="0.0"
          />
        </div>

        {/* 结束时间 */}
        <div>
          <label className="block text-gray-700 text-sm font-medium mb-2">结束时间</label>
          <input
            type="number"
            step="0.1"
            value={endTime}
            onChange={(e) => setEndTime(Number(e.target.value))}
            className="w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 focus:outline-none focus:border-teal-500 text-sm"
            placeholder="5.0"
          />
        </div>

        {/* 颜色 */}
        <div>
          <label className="block text-gray-700 text-sm font-medium mb-2">颜色</label>
          <input
            type="color"
            value={textColor}
            onChange={(e) => setTextColor(e.target.value)}
            className="w-full h-10 rounded-lg border border-gray-300 cursor-pointer"
          />
        </div>

        {/* 颜色值 */}
        <div>
          <label className="block text-gray-700 text-sm font-medium mb-2">颜色值</label>
          <input
            type="text"
            value={textColor}
            onChange={(e) => setTextColor(e.target.value)}
            className="w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 focus:outline-none focus:border-teal-500 text-sm"
          />
        </div>

        {/* 添加/保存按钮 */}
        {!isEditMode && (
          <button
            onClick={addFlashTextToCanvas}
            disabled={!text.trim()}
            className="w-full mt-6 px-4 py-3 bg-teal-600 hover:bg-teal-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors flex items-center justify-center gap-2"
          >
            <Plus size={16} />
            添加
          </button>
        )}
      </div>
    </div>
  );
};

export default FlashTextPanel;
