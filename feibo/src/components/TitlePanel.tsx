import React, { useState, useEffect, useRef } from 'react';
import { v4 as uuidv4 } from 'uuid';
import { Plus, Bold, Italic } from 'lucide-react';
import { useStore } from '../store';
import { SceneElement, TitleProperties } from '../types';
import { getTypefaceOptions } from '../services/typefaceApi';

interface TitlePanelProps {
  isEditMode?: boolean;
  selectedElement?: SceneElement;
}

const TitlePanel: React.FC<TitlePanelProps> = ({ isEditMode = false, selectedElement }) => {
  const { actions: { addElement, getNextZIndex, updateElement } } = useStore();
  const [text, setText] = useState('输入标题文字');
  const [fontSize, setFontSize] = useState(20);
  const [color, setColor] = useState('#FFFFFF');
  const [fontFamily, setFontFamily] = useState('Source Han Sans SC Medium');
  const [fontOptions, setFontOptions] = useState<{label:string;value:string}[]>(getTypefaceOptions());
  const [opacity, setOpacity] = useState(100);
  const [isBold, setIsBold] = useState(true);
  const [isItalic, setIsItalic] = useState(false);
  const isHydratingRef = useRef(false);

  // 编辑模式下，从选中元素初始化状态
  useEffect(() => {
    if (isEditMode && selectedElement && selectedElement.type === 'title') {
      const props = selectedElement.properties as TitleProperties;
      setText(props.text || '输入标题文字');
      setFontSize(props.fontSize || 20);
      setColor(props.color || '#FFFFFF');
      setFontFamily(props.fontFamily || 'Source Han Sans SC Medium');
      setOpacity((props.opacity || 1) * 100);
      setIsBold(props.fontWeight === 'bold');
      setIsItalic(props.fontStyle === 'italic');
      isHydratingRef.current = true;
    } else if (!isEditMode) {
      // 非编辑模式下重置为默认值
      setText('输入标题文字');
      setFontSize(20);
      setColor('#FFFFFF');
      setFontFamily('Source Han Sans SC Medium');
      setOpacity(100);
      setIsBold(true);
      setIsItalic(false);
    }
  }, [isEditMode, selectedElement?.id]); // 移除selectedElement?.properties依赖，避免实时更新时重新初始化

  useEffect(() => {
    const fonts = getTypefaceOptions();
    setFontOptions(fonts);
    const exists = fonts.some(f => f.value === fontFamily);
    if (!exists && !isEditMode) {
      const preferred = fonts.find(f => f.label.includes('思源黑体') || f.value === 'Source Han Sans SC Medium');
      if (preferred) {
        setFontFamily(preferred.value);
      } else if (fonts[0]) {
        setFontFamily(fonts[0].value);
      }
    }
  }, []);

  const handleAddTitle = () => {
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

    const element: SceneElement = {
      id: uuidv4(),
      type: 'title',
      position: { x: canvasWidth / 2 - 500, y: canvasHeight / 2 - 100 },
      size: { width: 1000, height: 200 },
      rotation: 0,
      zIndex: getNextZIndex('title'),
      properties: {
        text,
        fontSize,
        color,
        fontFamily,
        fontWeight: isBold ? 'bold' : 'normal',
        fontStyle: isItalic ? 'italic' : 'normal',
        textAlign: 'left',
        opacity: opacity / 100
      } as TitleProperties
    };
    
    addElement(element);
  };

  const handleUpdateTitle = () => {
    if (isEditMode && selectedElement) {
      const updatedProperties: TitleProperties = {
        text,
        fontSize,
        color,
        fontFamily,
        fontWeight: isBold ? 'bold' : 'normal',
        fontStyle: isItalic ? 'italic' : 'normal',
        textAlign: 'left',
        opacity: opacity / 100
      };
      
      updateElement(selectedElement.id, { properties: updatedProperties });
    }
  };

  // 实时更新选中元素
  useEffect(() => {
    if (isHydratingRef.current) {
      isHydratingRef.current = false;
      return;
    }
    if (isEditMode && selectedElement) {
      const updatedProperties: TitleProperties = {
        text,
        fontSize,
        color,
        fontFamily,
        fontWeight: isBold ? 'bold' : 'normal',
        fontStyle: isItalic ? 'italic' : 'normal',
        textAlign: 'left',
        opacity: opacity / 100
      };
      
      updateElement(selectedElement.id, { properties: updatedProperties });
    }
  }, [text, fontSize, color, fontFamily, opacity, isBold, isItalic, isEditMode, selectedElement?.id]);

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* 标题 */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="text-gray-900 font-semibold text-sm">
          {isEditMode ? '编辑标题' : '标题设置'}
        </h3>
      </div>

      {/* 内容区域 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* 文字内容 */}
        <div>
          <label className="block text-gray-700 text-sm mb-2">标题内容</label>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="w-full px-3 py-2 bg-white border border-gray-300 rounded text-gray-900 text-sm resize-none"
            rows={3}
            placeholder="输入标题文字"
          />
        </div>

        {/* 字体和字符大小 */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-gray-700 text-sm mb-2">字体</label>
            <select
              value={fontFamily}
              onChange={(e) => setFontFamily(e.target.value)}
              className="w-full px-3 py-2 bg-white border border-gray-300 rounded text-gray-900 text-sm"
            >
              {fontOptions.map(font => (
                <option key={font.value} value={font.value}>{font.label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-gray-700 text-sm mb-2">字符大小</label>
            <input
              type="number"
              value={fontSize}
              onChange={(e) => setFontSize(Number(e.target.value))}
              className="w-full px-3 py-2 bg-white border border-gray-300 rounded text-gray-900 text-sm"
              min="12"
              max="200"
            />
          </div>
        </div>

        {/* 颜色 */}
        <div>
          <label className="block text-gray-700 text-sm mb-2">颜色</label>
          <div className="flex items-center gap-2">
            <input
              type="color"
              value={color}
              onChange={(e) => setColor(e.target.value)}
              className="w-8 h-8 rounded border border-gray-300 cursor-pointer"
            />
            <input
              type="text"
              value={color}
              onChange={(e) => setColor(e.target.value)}
              className="flex-1 px-2 py-1 bg-white border border-gray-300 rounded text-gray-900 text-xs"
            />
          </div>
        </div>


        {/* 样式 */}
        <div>
          <label className="block text-gray-700 text-sm mb-2">样式</label>
          <div className="flex gap-2">
            <button
              onClick={() => setIsBold(!isBold)}
              className={`flex-1 p-2 rounded border transition-colors flex items-center justify-center gap-2 ${
                isBold
                  ? 'bg-teal-600 text-white border-teal-600'
                  : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
            >
              <Bold size={16} />
              粗体
            </button>
            <button
              onClick={() => setIsItalic(!isItalic)}
              className={`flex-1 p-2 rounded border transition-colors flex items-center justify-center gap-2 ${
                isItalic
                  ? 'bg-teal-600 text-white border-teal-600'
                  : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
            >
              <Italic size={16} />
              斜体
            </button>
          </div>
        </div>

        {/* 添加/保存按钮 */}
        {!isEditMode && (
          <button
            onClick={handleAddTitle}
            className="w-full py-3 bg-teal-600 hover:bg-teal-700 text-white rounded-lg font-medium transition-colors flex items-center justify-center gap-2"
          >
            <Plus size={16} />
            添加
          </button>
        )}
      </div>
    </div>
  );
};

export default TitlePanel;
