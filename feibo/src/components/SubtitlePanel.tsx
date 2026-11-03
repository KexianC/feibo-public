import React, { useState, useEffect } from 'react';
import { Type, ToggleLeft, ToggleRight } from 'lucide-react';
import { useStore } from '../store';
import { v4 as uuidv4 } from 'uuid';
import { PageSubtitleConfig } from '../types';
import { getTypefaceOptions } from '../services/typefaceApi';

/**
 * 字幕面板
 * 说明：
 * - 距离底部（bottomSpace）滑条范围按画布分辨率动态设置：横屏 0-1080，竖屏 0-1920
 * - bottomSpace/wordSpace 均以像素为单位，并实时写入页面配置与字幕元素属性
 */
const SubtitlePanel: React.FC = () => {
  const { currentProject, actions: { updateElement, addElement, getNextZIndex, updatePageSubtitleConfig } } = useStore();
  
  // 获取当前页面
  const currentPage = currentProject?.pages.find(p => p.id === currentProject.currentPageId);
  
  // 默认字幕配置
  const defaultSubtitleConfig: PageSubtitleConfig = {
    isEnabled: false,
    contentType: 'subtitles',
    primaryColour: '#000000',
    fontname: 'STSONG',
    bold: '0',
    subtitleUsepx: 'Y',
    fontsize: '35',
    underline: 0,
    italic: 0,
    bottomSpace: 10,
    wordSpace: 0
  };
  
  // 使用当前页面的字幕配置或默认配置
  const subtitleConfig = currentPage?.subtitleConfig || defaultSubtitleConfig;
  // 依据当前页面的纵横比计算画布尺寸与 bottomSpace 最大值
  const aspect = currentPage?.aspectRatio || '16:9';
  const canvasWidth = aspect === '9:16' ? 1080 : 1920;
  const canvasHeight = aspect === '9:16' ? 1920 : 1080;
  const maxBottomSpace = canvasHeight;
  
  const [isEnabled, setIsEnabled] = useState(subtitleConfig.isEnabled);
  const [primaryColour, setPrimaryColour] = useState('#000000');
  const [fontname, setFontname] = useState('Source Han Sans SC Medium');
  const [fontOptions, setFontOptions] = useState<{label:string;value:string}[]>([]);
  const [bold, setBold] = useState('0');
  const [fontsize, setFontsize] = useState('35');
  const [underline, setUnderline] = useState(0);
  const [italic, setItalic] = useState(0);
  const [bottomSpace, setBottomSpace] = useState(10);
  const [wordSpace, setWordSpace] = useState(0);

  // 页面切换时恢复字幕配置
  useEffect(() => {
    const config = currentPage?.subtitleConfig || defaultSubtitleConfig;
    if (config) {
      setIsEnabled(config.isEnabled);
      setPrimaryColour(config.primaryColour);
      setFontname(config.fontname);
      setBold(config.bold);
      setFontsize(config.fontsize);
      setUnderline(config.underline);
      setItalic(config.italic);
      setBottomSpace(config.bottomSpace);
      setWordSpace(config.wordSpace);
    }
  }, [currentPage?.id]);

  // 更新页面字幕配置
  const updateSubtitleConfig = () => {
    const newConfig: PageSubtitleConfig = {
      isEnabled,
      contentType: 'subtitles',
      primaryColour,
      fontname,
      bold,
      subtitleUsepx: 'Y',
      fontsize,
      underline,
      italic,
      bottomSpace,
      wordSpace
    };
    updatePageSubtitleConfig(newConfig);
  };

  // 监听状态变化并更新页面配置
  useEffect(() => {
    updateSubtitleConfig();
  }, [isEnabled, primaryColour, fontname, bold, fontsize, underline, italic, bottomSpace, wordSpace]);

  // 映射 bottomSpace 为像素定位，实时更新画布上的字幕元素位置
  useEffect(() => {
    const project = useStore.getState().currentProject;
    const page = project?.pages.find(p => p.id === project.currentPageId);
    if (!page) return;
    const aspect = page.aspectRatio || '16:9';
    const canvasWidth = aspect === '9:16' ? 1080 : 1920;
    const canvasHeight = aspect === '9:16' ? 1920 : 1080;
    // 找到当前页面的字幕元素
    const subtitleElement = page.elements?.find((el) => el.type === 'subtitle');
    if (!subtitleElement) return;
    const newY = canvasHeight - Number(bottomSpace) - (subtitleElement.size?.height || 100);
    updateElement(subtitleElement.id, {
      position: {
        x: subtitleElement.position?.x ?? 50,
        y: Math.max(0, newY)
      },
      size: {
        width: (subtitleElement.size?.width ?? (canvasWidth - 100)),
        height: (subtitleElement.size?.height ?? 100)
      },
      properties: {
        ...subtitleElement.properties,
        bottomSpace: Number(bottomSpace),
        wordSpace: Number(wordSpace)
      }
    });
  }, [bottomSpace, wordSpace, updateElement]);

  useEffect(() => {
    const fonts = getTypefaceOptions();
    setFontOptions(fonts);
    const exists = fonts.some(f => f.value === fontname);
    if (!exists) {
      const preferred = fonts.find(f => f.label.includes('思源黑体') || f.value === 'Source Han Sans SC Medium');
      if (preferred) {
        setFontname(preferred.value);
      } else if (fonts[0]) {
        setFontname(fonts[0].value);
      }
    }
  }, []);

  const addSubtitleToCanvas = () => {
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
      type: 'subtitle' as const,
      position: { x: 50, y: canvasHeight - Number(bottomSpace) - 100 },
      size: { width: canvasWidth - 100, height: 100 },
      rotation: 0,
      zIndex: getNextZIndex('subtitle'),
      properties: {
        text: '请输入字幕内容',
        // 用于视频合成的参数
        contentType: 'subtitles',
        primaryColour,
        fontname,
        bold,
        subtitleUsepx: 'Y',
        fontsize,
        underline,
          italic,
          bottomSpace,
          wordSpace
      }
    };

    addElement(element);
  };

  return (
    <div className="p-4 bg-white rounded-lg shadow-sm">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-2">
          <Type size={20} className="text-gray-600" />
          <span className="text-sm font-medium text-gray-900">显示字幕</span>
        </div>
        <button
          onClick={() => setIsEnabled(!isEnabled)}
          className="flex items-center space-x-1"
        >
          {isEnabled ? (
            <ToggleRight size={20} className="text-teal-500" />
          ) : (
            <ToggleLeft size={20} className="text-gray-400" />
          )}
        </button>
      </div>

      {isEnabled && (
        <div className="space-y-4">
          {/* 字号 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              字号
            </label>
            <input
              type="number"
              value={fontsize}
              onChange={(e) => setFontsize(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent"
              min="12"
              max="72"
            />
          </div>

          {/* 样式 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              样式
            </label>
            <div className="flex space-x-2">
              <button
                onClick={() => setBold(bold === '1' ? '0' : '1')}
                className={`w-10 h-10 rounded-md font-bold flex items-center justify-center ${
                  bold === '1'
                    ? 'bg-teal-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                B
              </button>
              <button
                onClick={() => setItalic(italic === 1 ? 0 : 1)}
                className={`w-10 h-10 rounded-md italic flex items-center justify-center ${
                  italic === 1
                    ? 'bg-cyan-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                I
              </button>
              <button
                onClick={() => setUnderline(underline === 1 ? 0 : 1)}
                className={`w-10 h-10 rounded-md underline flex items-center justify-center ${
                  underline === 1
                    ? 'bg-teal-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                U
              </button>
            </div>
          </div>

          {/* 字体 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              字体
            </label>
            <select
              value={fontname}
              onChange={(e) => setFontname(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent bg-white"
            >
              {fontOptions.map((font) => (
                <option key={font.value} value={font.value}>
                  {font.label}
                </option>
              ))}
            </select>
          </div>

          {/* 距离底部 */}
          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-2">距离底部</label>
          <input
            type="range"
            min="0"
            max={maxBottomSpace}
            value={bottomSpace}
            onChange={(e) => setBottomSpace(Number(e.target.value))}
            className="w-full h-3 bg-gray-400 rounded-lg appearance-none cursor-pointer slider"
            style={{
              background: `linear-gradient(to right, #059669 0%, #059669 ${(bottomSpace / maxBottomSpace) * 100}%, #9ca3af ${(bottomSpace / maxBottomSpace) * 100}%, #9ca3af 100%)`
            }}
          />
        </div>

          {/* 字间距 */}
          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-2">字间距</label>
            <input
              type="number"
              min="0"
              max="50"
              value={wordSpace}
              onChange={(e) => setWordSpace(Number(e.target.value))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent"
            />
          </div>

          {/* 颜色 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              颜色
            </label>
            <div className="flex items-center space-x-2">
              <div 
                className="w-full h-10 rounded-md border border-gray-300 flex items-center justify-center text-white font-medium"
                style={{ backgroundColor: primaryColour }}
              >
                {primaryColour}
              </div>
              <input
                type="color"
                value={primaryColour}
                onChange={(e) => setPrimaryColour(e.target.value)}
                className="w-12 h-10 border border-gray-300 rounded cursor-pointer"
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SubtitlePanel;
