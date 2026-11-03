import React, { useState } from 'react';
import { v4 as uuidv4 } from 'uuid';
import { ImageIcon, Upload, Check } from 'lucide-react';
import { useStore } from '../store';
import { SceneElement, BackgroundProperties } from '../types';
import { uploadBackgroundImage, getUserBackgrounds, UserBackground } from '../services/fileUploadApi';

const BackgroundPanel: React.FC = () => {
  const { currentProject, actions: { addElement, deleteElementsByType, updateElement } } = useStore();
  const [activeTab, setActiveTab] = useState<'library' | 'my-backgrounds'>('library');
  const [userBackgrounds, setUserBackgrounds] = useState<UserBackground[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastCursorId, setLastCursorId] = useState<string>('');
  const [hasMore, setHasMore] = useState(true);


  // 获取当前页面的画布尺寸
  const getCanvasSize = () => {
    const currentPage = currentProject?.pages.find(p => p.id === currentProject.currentPageId);
    const aspectRatio = currentPage?.aspectRatio || '16:9';
    if (aspectRatio === '9:16') {
      return { width: 1080, height: 1920 }; // 竖屏
    } else {
      return { width: 1920, height: 1080 }; // 横屏
    }
  };

  // 调整其他元素的层级，确保它们不在1层
  const adjustElementsZIndex = () => {
    if (!currentProject) return;
    
    const currentPage = currentProject.pages.find(p => p.id === currentProject.currentPageId);
    if (!currentPage) return;
    
    // 找到所有zIndex为1的非背景元素
    const elementsAtLevel1 = currentPage.elements.filter(el => el.zIndex === 1 && el.type !== 'background');
    
    // 将这些元素的层级调整到2或更高
    elementsAtLevel1.forEach(element => {
      updateElement(element.id, { zIndex: 2 });
    });
  };



  // 预设背景图片
  const presetImages = [
    {
      id: 'bg0',
      name: '绿幕',
      url: 'https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/background/20260105/12315d1f433942b7a9e381c790571e2b.jpg'
    },
    {
      id: 'bg1',
      name: '背景图片1',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbbj_1710409794449.jpg'
    },
    {
      id: 'bg2', 
      name: '背景图片2',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbbj_1710409782954.jpg'
    },
    {
      id: 'bg3',
      name: '背景图片3',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/202467/mbbj_1717725815215.jpg'
    },
    {
      id: 'bg4',
      name: '背景图片4',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbbj_1710409768237.jpg'
    },
    {
      id: 'bg5',
      name: '背景图片5',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbbj_1710409745043.jpg'
    },
    {
      id: 'bg6',
      name: '背景图片6',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbbj_1710409727442.jpg'
    },
    {
      id: 'bg7',
      name: '背景图片7',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbbj_1710409694924.jpg'
    }
  ];



  const handleAddImageBackground = (imageUrl: string) => {
    // 删除现有背景
    deleteElementsByType('background');
    
    // 检查并调整其他元素的层级，确保它们不在1层
    adjustElementsZIndex();
    
    const canvasSize = getCanvasSize();
    const element: SceneElement = {
      id: uuidv4(),
      type: 'background',
      position: { x: 0, y: 0 },
      size: canvasSize, // 动态画布尺寸
      rotation: 0,
      zIndex: 1,
      properties: {
        backgroundType: 'image',
        imageUrl,
        opacity: 1
      } as BackgroundProperties
    };
    
    addElement(element);
  };

  // 获取用户背景数据
  const fetchUserBackgrounds = async (reset: boolean = false) => {
    if (isLoading) return;
    
    setIsLoading(true);
    setError(null);
    
    try {
      const cursorId = reset ? '' : lastCursorId;
      const response = await getUserBackgrounds(cursorId, 10);
      
      if (response.code === '0000') {
        // 根据新的API返回格式解析数据
        const { data: backgroundsData, hasNext, nextCursorId } = response.data;
        
        // 将API返回的数据转换为组件需要的格式
        const backgrounds = (backgroundsData || []).map((item: any, index: number) => ({
          id: `bg_${Date.now()}_${index}`,
          name: item.name || '未命名背景',
          url: sanitizeOssUrl(item.url || '')
        }));
        
        if (reset) {
          setUserBackgrounds(backgrounds);
        } else {
          setUserBackgrounds(prev => [...prev, ...backgrounds]);
        }
        
        setHasMore(hasNext || false);
        setLastCursorId(nextCursorId || '');
      } else {
        setError(response.info || '获取用户背景失败');
      }
    } catch (error) {
      console.error('获取用户背景失败:', error);
      setError('获取用户背景失败，请稍后重试');
    } finally {
      setIsLoading(false);
    }
  };

  // 处理标签切换
  const handleTabChange = (tab: 'library' | 'my-backgrounds') => {
    setActiveTab(tab);
    if (tab === 'my-backgrounds' && (userBackgrounds || []).length === 0 && !isLoading) {
      fetchUserBackgrounds(true);
    }
  };

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file && file.type.startsWith('image/')) {
      try {
        // 使用统一的上传接口
        const response = await uploadBackgroundImage(file);
        
        // 添加到用户背景库
        const newBackground = {
          id: response.data.fileId,
          name: file.name.replace(/\.[^/.]+$/, ''), // 移除文件扩展名
          url: sanitizeOssUrl(response.data.fileUrl)
        };
        setUserBackgrounds(prev => [...prev, newBackground]);
        // 直接应用背景
        handleAddImageBackground(sanitizeOssUrl(response.data.fileUrl));
      } catch (error) {
        console.error('背景图片上传失败:', error);
        // 降级处理：使用本地预览
        const reader = new FileReader();
        reader.onload = (e) => {
          const result = e.target?.result as string;
          const newBackground = {
            id: uuidv4(),
            name: file.name.replace(/\.[^/.]+$/, ''),
            url: result
          };
          setUserBackgrounds(prev => [...prev, newBackground]);
          handleAddImageBackground(result);
        };
        reader.readAsDataURL(file);
      }
    }
  };

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* 标题 */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="text-gray-900 font-semibold text-sm">背景设置</h3>
      </div>

      {/* 标签切换 */}
      <div className="p-4 border-b border-gray-200">
        <div className="flex space-x-2">
          <button
            onClick={() => handleTabChange('library')}
            className={`flex-1 flex items-center justify-center px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
              activeTab === 'library'
                ? 'bg-teal-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            <ImageIcon size={16} className="mr-2" />
            背景库
          </button>
          <button
            onClick={() => handleTabChange('my-backgrounds')}
            className={`flex-1 flex items-center justify-center px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
              activeTab === 'my-backgrounds'
                ? 'bg-cyan-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
            disabled={isLoading}
          >
            <Upload size={16} className="mr-2" />
            我的背景
            {isLoading && activeTab === 'my-backgrounds' && (
              <div className="ml-2 w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
            )}
          </button>
        </div>
      </div>



      {/* 内容区域 */}
      <div className="flex-1 overflow-y-auto p-4">
        {activeTab === 'library' && (
          <div className="space-y-4">
            {/* 预设背景 */}
            <div>
              <label className="block text-gray-700 text-sm mb-2">选择背景</label>
              <div className="space-y-2">
                {presetImages.map((bg) => (
                  <button
                    key={bg.id}
                    onClick={() => handleAddImageBackground(bg.url)}
                    className="w-full p-3 rounded-lg border border-gray-300 hover:border-teal-500 transition-colors"
                  >
                    <img
                      src={bg.url}
                      alt={bg.name}
                      className="w-full h-16 object-cover rounded mb-2"
                    />
                    <div className="text-center text-gray-900 text-sm">{bg.name}</div>
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'my-backgrounds' && (
          <div className="space-y-4">
            {/* 文件上传 */}
            <div>
              <label className="block text-gray-700 text-sm mb-2">上传背景图片</label>
              <label className="flex items-center justify-center w-full h-24 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer hover:border-gray-400 transition-colors">
                <div className="text-center">
                  <Upload size={24} className="text-gray-500 mx-auto mb-2" />
                  <span className="text-gray-600 text-sm">点击上传图片</span>
                </div>
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleFileUpload}
                  className="hidden"
                />
              </label>
            </div>

            {/* 错误提示 */}
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg">
                <p className="text-red-600 text-sm">{error}</p>
                <button
                  onClick={() => fetchUserBackgrounds(true)}
                  className="mt-2 text-red-600 text-sm underline hover:no-underline"
                >
                  重试
                </button>
              </div>
            )}

            {/* 加载状态 */}
            {isLoading && (userBackgrounds || []).length === 0 && (
              <div className="text-center py-8">
                <div className="w-8 h-8 border-2 border-gray-300 border-t-teal-600 rounded-full animate-spin mx-auto mb-4"></div>
                <p className="text-gray-500 text-sm">正在加载背景图片...</p>
              </div>
            )}

            {/* 用户上传的背景 */}
            {(userBackgrounds || []).length > 0 && (
              <div>
                <label className="block text-gray-700 text-sm mb-2">我的背景</label>
                <div className="space-y-2">
                  {(userBackgrounds || []).map((bg) => (
                    <button
                      key={bg.id}
                      onClick={() => handleAddImageBackground(bg.url)}
                      className="w-full p-3 rounded-lg border border-gray-300 hover:border-teal-500 transition-colors"
                    >
                      <img
                        src={bg.url}
                        alt={bg.name}
                        className="w-full h-16 object-cover rounded mb-2"
                        onError={(e) => {
                          const target = e.target as HTMLImageElement;
                          target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjQiIGhlaWdodD0iMjQiIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3Qgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0IiBmaWxsPSIjRjNGNEY2Ii8+CjxwYXRoIGQ9Ik0xMiAxNkM4LjY4NjI5IDE2IDYgMTMuMzEzNyA2IDEwQzYgNi42ODYyOSA4LjY4NjI5IDQgMTIgNEMxNS4zMTM3IDQgMTggNi42ODYyOSAxOCAxMEMxOCAxMy4zMTM3IDE1LjMxMzcgMTYgMTIgMTZaIiBmaWxsPSIjOUI5QkEwIi8+CjxwYXRoIGQ9Ik0xMiAxNEMxMy4xMDQ2IDE0IDE0IDEzLjEwNDYgMTQgMTJDMTQgMTAuODk1NCAxMy4xMDQ2IDEwIDEyIDEwQzEwLjg5NTQgMTAgMTAgMTAuODk1NCAxMCAxMkMxMCAxMy4xMDQ2IDEwLjg5NTQgMTQgMTIgMTRaIiBmaWxsPSIjNjM2MzZBIi8+Cjwvc3ZnPgo=';
                        }}
                      />
                      <div className="text-center text-gray-900 text-sm">{bg.name}</div>
                    </button>
                  ))}
                </div>
                
                {/* 加载更多按钮 */}
                {hasMore && (
                  <div className="mt-4 text-center">
                    <button
                      onClick={() => fetchUserBackgrounds(false)}
                      disabled={isLoading}
                      className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {isLoading ? (
                        <div className="flex items-center">
                          <div className="w-4 h-4 border-2 border-gray-400 border-t-transparent rounded-full animate-spin mr-2"></div>
                          加载中...
                        </div>
                      ) : (
                        '加载更多'
                      )}
                    </button>
                  </div>
                )}
              </div>
            )}

            {/* 空状态提示 */}
            {(userBackgrounds || []).length === 0 && !isLoading && !error && (
              <div className="text-center py-8">
                <ImageIcon size={48} className="text-gray-300 mx-auto mb-4" />
                <p className="text-gray-500 text-sm">还没有上传背景图片</p>
                <p className="text-gray-400 text-xs mt-1">点击上方按钮上传您的背景图片</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default BackgroundPanel;
  const sanitizeOssUrl = (raw: string): string => {
    const s = (raw || '').trim().replace(/[`"']/g, '');
    if (s.startsWith('http://')) {
      try {
        const u = new URL(s);
        return `https://minio.hzmwmt.com${u.pathname}${u.search}`;
      } catch {
        return s.replace(/^http:\/\/[\w\.:\-]+/, 'https://minio.hzmwmt.com');
      }
    }
    return s;
  };
