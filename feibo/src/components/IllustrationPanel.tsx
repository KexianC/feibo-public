import React, { useState } from 'react';
import { v4 as uuidv4 } from 'uuid';
import { ImageIcon, Upload, Search, Plus, Image } from 'lucide-react';
import { uploadIllustration } from '../services/fileUploadApi';
import { getUserIllustrations, UserIllustration } from '../services/fileUploadApi';
import { useStore } from '../store';
import { SceneElement, IllustrationProperties } from '../types';

interface IllustrationItem {
  id: string;
  name: string;
  category: string;
  url: string;
  tags: string[];
}

const IllustrationPanel: React.FC = () => {
  const { actions: { addElement, getNextZIndex } } = useStore();
  const [activeTab, setActiveTab] = useState<'library' | 'my-illustrations'>('library');
  const [searchTerm, setSearchTerm] = useState('');
  const [userIllustrations, setUserIllustrations] = useState<UserIllustration[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastCursorId, setLastCursorId] = useState<string>('');
  const [hasMore, setHasMore] = useState(true);



  // 预设插图素材
  const illustrations: IllustrationItem[] = [
    {
      id: 'ill1',
      name: '商务团队',
      category: 'business',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbct_1710409975963.png',
      tags: ['团队', '会议', '商务']
    },
    {
      id: 'ill2',
      name: '数据分析',
      category: 'business',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbct_1710410000815.png',
      tags: ['数据', '图表', '分析']
    },
    {
      id: 'ill4',
      name: '创新思维',
      category: 'business',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbct_1710410085585.png',
      tags: ['创新', '思维', '灵感']
    },
    {
      id: 'ill6',
      name: '客户服务',
      category: 'business',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbct_1710410112260.png',
      tags: ['客户', '服务', '支持']
    },
    {
      id: 'ill7',
      name: '人工智能',
      category: 'technology',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbct_1710410129872.png',
      tags: ['AI', '人工智能', '机器人']
    },
    {
      id: 'ill8',
      name: '云计算',
      category: 'technology',
      url: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024314/mbct_1710410121225.png',
      tags: ['云计算', '服务器', '网络']
    }
  ];

  // 筛选插图
  const filteredIllustrations = illustrations.filter(item => {
    const matchesSearch = searchTerm === '' || 
      item.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.tags.some(tag => tag.toLowerCase().includes(searchTerm.toLowerCase()));
    return matchesSearch;
  });

  /**
   * 将插图添加到画布
   * 始终期望传入远程 OSS 地址，避免 base64/blob 导致合成使用错误的 content
   */
  const handleAddIllustration = (imageUrl: string, name: string) => {
    // 防护：阻止将本地数据添加到画布
    if (imageUrl.startsWith('data:') || imageUrl.startsWith('blob:')) {
      console.warn('插图地址为本地数据，已阻止添加到画布，请使用服务器返回的OSS地址');
      return;
    }
    // 创建图片对象来获取原始尺寸
    const img = document.createElement('img');
    img.onload = () => {
      // 计算保持比例的尺寸（最大边不超过200px）
      const maxSize = 200;
      let width = img.naturalWidth;
      let height = img.naturalHeight;
      
      if (width > height) {
        // 宽度较大，以宽度为准
        if (width > maxSize) {
          height = (height * maxSize) / width;
          width = maxSize;
        }
      } else {
        // 高度较大，以高度为准
        if (height > maxSize) {
          width = (width * maxSize) / height;
          height = maxSize;
        }
      }
      
      const element: SceneElement = {
        id: uuidv4(),
        type: 'illustration',
        position: { x: 300, y: 200 },
        size: { width: Math.round(width), height: Math.round(height) },
        rotation: 0,
        zIndex: getNextZIndex('illustration'),
        properties: {
          imageUrl,
          alt: name
        } as IllustrationProperties
      };
      
      addElement(element);
    };
    
    img.onerror = () => {
      // 如果图片加载失败，使用默认尺寸
      console.warn('Failed to load image for aspect ratio calculation');
      const element: SceneElement = {
        id: uuidv4(),
        type: 'illustration',
        position: { x: 300, y: 200 },
        size: { width: 200, height: 200 },
        rotation: 0,
        zIndex: getNextZIndex('illustration'),
        properties: {
          imageUrl,
          alt: name
        } as IllustrationProperties
      };
      
      addElement(element);
    };
    
    img.src = imageUrl;
  };

  // 获取用户插图数据
  /**
   * 获取用户插图列表并清理 URL（去除反引号与空白）
   */
  const fetchUserIllustrations = async (reset: boolean = false) => {
    if (isLoading) return;
    
    setIsLoading(true);
    setError(null);
    
    try {
      const cursorId = reset ? '' : lastCursorId;
      const response = await getUserIllustrations(cursorId, 10);
      
      if (response.code === '0000') {
        const { data: illustrationsData, hasNext, nextCursorId } = response.data;
        
        const illustrations = (illustrationsData || []).map((item: any, index: number) => ({
          id: `illustration_${Date.now()}_${index}`,
          name: item.name || '未命名插图',
          url: sanitizeOssUrl(item.url || '')
        }));
        
        if (reset) {
          setUserIllustrations(illustrations);
        } else {
          setUserIllustrations(prev => [...prev, ...illustrations]);
        }
        
        setHasMore(hasNext || false);
        setLastCursorId(nextCursorId || '');
      } else {
        setError(response.info || '获取用户插图失败');
      }
    } catch (error) {
      console.error('获取用户插图失败:', error);
      setError('获取用户插图失败，请稍后重试');
    } finally {
      setIsLoading(false);
    }
  };

  // 处理标签切换
  /**
   * 标签切换：进入“我的插图”时加载列表
   */
  const handleTabChange = (tab: 'library' | 'my-illustrations') => {
    setActiveTab(tab);
    if (tab === 'my-illustrations' && userIllustrations.length === 0 && !isLoading) {
      fetchUserIllustrations(true);
    }
  };

  /**
   * 处理插图文件上传
   * 成功后立即使用 OSS 地址添加到画布，并刷新“我的插图”列表；失败时不添加 base64 预览
   */
  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file && file.type.startsWith('image/')) {
      try {
        // 使用统一的上传接口
        const response = await uploadIllustration(file);
        if (response.code === '0000' && response.data) {
          const imageUrl = sanitizeOssUrl(response.data.fileUrl);
          
          // 添加到用户插图库
          const newIllustration = {
            id: response.data.fileId,
            name: file.name.replace(/\.[^/.]+$/, ''),
            url: imageUrl
          };
          setUserIllustrations(prev => [newIllustration, ...prev]);
          
          // 直接添加到画布（确保使用 OSS 地址）
          handleAddIllustration(imageUrl, newIllustration.name);
          // 刷新“我的插图”列表，确保后续选择使用服务端地址
          fetchUserIllustrations(true);
        } else {
          throw new Error(response.info || '上传失败');
        }
      } catch (error) {
        console.error('插图上传失败:', error);
        // 不再将 base64/本地预览添加到画布，提示用户重试上传
        setError('插图上传失败，请稍后重试');
      }
    }
  };

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* 标题 */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="text-gray-900 font-semibold text-sm">插图设置</h3>
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
            <Image size={16} className="mr-2" />
            插图库
          </button>
          <button
            onClick={() => handleTabChange('my-illustrations')}
            className={`flex-1 flex items-center justify-center px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
              activeTab === 'my-illustrations'
                ? 'bg-cyan-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
            disabled={isLoading}
          >
            <Upload size={16} className="mr-2" />
            我的插图
            {isLoading && activeTab === 'my-illustrations' && (
              <div className="ml-2 w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
            )}
          </button>
        </div>
      </div>

      {/* 内容区域 */}
      <div className="flex-1 overflow-y-auto p-4">
        {activeTab === 'library' && (
          <div className="space-y-4">
            {/* 搜索功能 */}
            <div>
              <div className="relative">
                <Search size={16} className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-600" />
                <input
                  type="text"
                  placeholder="搜索插图..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full pl-10 pr-4 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 placeholder-gray-500 focus:outline-none focus:border-teal-500 text-sm"
                />
              </div>
            </div>
            
            {/* 预设插图 */}
            <div>
              <label className="block text-gray-700 text-sm mb-2">选择插图</label>
              {filteredIllustrations.length > 0 ? (
                <div className="grid grid-cols-2 gap-3">
                  {filteredIllustrations.map(item => (
                    <button
                      key={item.id}
                      onClick={() => handleAddIllustration(item.url, item.name)}
                      className="relative group cursor-pointer rounded-lg overflow-hidden border-2 border-gray-300 hover:border-teal-500 transition-colors"
                    >
                      <img
                        src={item.url}
                        alt={item.name}
                        className="w-full h-24 object-cover"
                      />
                      <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center">
                        <Plus size={20} className="text-white mb-1" />
                      </div>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="text-center text-gray-600 mt-8">
                  <ImageIcon size={48} className="mx-auto mb-4 opacity-50" />
                  <p className="text-sm">没有找到匹配的插图</p>
                  <p className="text-xs mt-1">尝试调整搜索条件</p>
                </div>
              )}
            </div>
          </div>
        )}

        {activeTab === 'my-illustrations' && (
          <div className="space-y-4">
            {/* 文件上传 */}
            <div>
              <label className="block text-gray-700 text-sm mb-2">上传插图文件</label>
              <label className="flex items-center justify-center w-full h-24 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer hover:border-gray-400 transition-colors">
                <div className="text-center">
                  <Upload size={24} className="text-gray-500 mx-auto mb-2" />
                  <span className="text-gray-600 text-sm">点击上传插图</span>
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
                  onClick={() => fetchUserIllustrations(true)}
                  className="mt-2 text-red-600 text-sm underline hover:no-underline"
                >
                  重试
                </button>
              </div>
            )}

            {/* 加载状态 */}
            {isLoading && userIllustrations.length === 0 && (
              <div className="text-center py-8">
                <div className="w-8 h-8 border-2 border-gray-300 border-t-teal-600 rounded-full animate-spin mx-auto mb-4"></div>
                <p className="text-gray-500 text-sm">正在加载插图文件...</p>
              </div>
            )}

            {/* 用户上传的插图 */}
            {userIllustrations.length > 0 && (
              <div>
                <label className="block text-gray-700 text-sm mb-2">我的插图</label>
                <div className="grid grid-cols-2 gap-3">
                  {userIllustrations.map((illustration) => (
                    <button
                      key={illustration.id}
                      onClick={() => handleAddIllustration(illustration.url, illustration.name)}
                      className="relative group cursor-pointer rounded-lg overflow-hidden border-2 border-gray-300 hover:border-teal-500 transition-colors"
                    >
                      <img
                        src={illustration.url}
                        alt={illustration.name}
                        className="w-full h-24 object-cover"
                        onError={(e) => {
                          const target = e.target as HTMLImageElement;
                          target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjQiIGhlaWdodD0iMjQiIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3Qgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0IiBmaWxsPSIjRjNGNEY2Ii8+CjxwYXRoIGQ9Ik0xMiAxNkM4LjY4NjI5IDE2IDYgMTMuMzEzNyA2IDEwQzYgNi42ODYyOSA4LjY4NjI5IDQgMTIgNEMxNS4zMTM3IDQgMTggNi42ODYyOSAxOCAxMEMxOCAxMy4zMTM3IDE1LjMxMzcgMTYgMTIgMTZaIiBmaWxsPSIjOUI5QkEwIi8+CjxwYXRoIGQ9Ik0xMiAxNEMxMy4xMDQ2IDE0IDE0IDEzLjEwNDYgMTQgMTJDMTQgMTAuODk1NCAxMy4xMDQ2IDEwIDEyIDEwQzEwLjg5NTQgMTAgMTAgMTAuODk1NCAxMCAxMkMxMCAxMy4xMDQ2IDEwLjg5NTQgMTQgMTIgMTRaIiBmaWxsPSIjNjM2MzZBIi8+Cjwvc3ZnPgo=';
                        }}
                      />
                      
                      <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center">
                        <Plus size={20} className="text-white mb-1" />
                      </div>
                    </button>
                  ))}
                </div>
                
                {/* 加载更多按钮 */}
                {hasMore && (
                  <div className="mt-4 text-center">
                    <button
                      onClick={() => fetchUserIllustrations(false)}
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
            {userIllustrations.length === 0 && !isLoading && !error && (
              <div className="text-center py-8">
                <ImageIcon size={48} className="text-gray-300 mx-auto mb-4" />
                <p className="text-gray-500 text-sm">还没有上传插图文件</p>
                <p className="text-gray-400 text-xs mt-1">点击上方按钮上传您的插图文件</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default IllustrationPanel;
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
