import React, { useState, useRef, useEffect } from 'react';
import { Music2, Upload, Music } from 'lucide-react';
import { useStore } from '../store';
import { v4 as uuidv4 } from 'uuid';
import { uploadMusic } from '../services/fileUploadApi';
import { getUserMusic, UserMusic } from '../services/fileUploadApi';

const MusicPanel: React.FC = () => {
  const { actions: { addElement } } = useStore();
  const [userMusic, setUserMusic] = useState<UserMusic[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastCursorId, setLastCursorId] = useState<string>('');
  const [hasMore, setHasMore] = useState(true);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 获取用户音乐数据
  const fetchUserMusic = async (reset: boolean = false) => {
    if (isLoading) return;
    
    setIsLoading(true);
    setError(null);
    
    try {
      const cursorId = reset ? '' : lastCursorId;
      const response = await getUserMusic(cursorId, 10);
      
      if (response.code === '0000') {
        const { data: musicData, hasNext, nextCursorId } = response.data;
        
        const music = (musicData || []).map((item: any, index: number) => ({
          id: `music_${Date.now()}_${index}`,
          name: item.name || '未命名音乐',
          url: item.url,
          artist: '自定义',
          duration: 0
        }));
        
        if (reset) {
          setUserMusic(music);
        } else {
          setUserMusic(prev => [...prev, ...music]);
        }
        
        setHasMore(hasNext || false);
        setLastCursorId(nextCursorId || '');
      } else {
        setError(response.info || '获取用户音乐失败');
      }
    } catch (error) {
      console.error('获取用户音乐失败:', error);
      setError('获取用户音乐失败，请稍后重试');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchUserMusic(true);
  }, []);

  // 添加音乐到画布的统一函数
  const addMusicToCanvas = (music: { name: string; artist: string; url: string; duration: number; category?: string; bpm?: number }) => {
    addElement({
      id: uuidv4(),
      type: 'music',
      position: { x: 0, y: 0 },
      size: { width: 100, height: 100 },
      rotation: 0,
      zIndex: 1,
      properties: {
        name: music.name,
        artist: music.artist,
        audioUrl: music.url,
        volume: 1.0,
        duration: music.duration,
        category: music.category || 'custom',
        bpm: music.bpm
      }
    });
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && file.type.startsWith('audio/')) {
      try {
        // 使用统一的上传接口
        const response = await uploadMusic(file);
        if (response.code === '0000' && response.data) {
          // 添加到用户音乐库
          const newMusic = {
            id: response.data.fileId,
            name: file.name.replace(/\.[^/.]+$/, ''),
            url: response.data.fileUrl,
            artist: '自定义',
            duration: 0
          };
          setUserMusic(prev => [newMusic, ...prev]);
          
          // 直接添加到画布
          addMusicToCanvas({
            name: newMusic.name,
            artist: newMusic.artist,
            url: newMusic.url,
            duration: newMusic.duration,
            category: 'custom'
          });
        } else {
          throw new Error('上传失败');
        }
      } catch (error) {
        console.error('音乐上传失败:', error);
        // 降级处理：使用本地预览
        const url = URL.createObjectURL(file);
        const newMusic = {
          id: uuidv4(),
          name: file.name.replace(/\.[^/.]+$/, ''),
          url,
          artist: '自定义',
          duration: 0
        };
        setUserMusic(prev => [newMusic, ...prev]);
        addMusicToCanvas({
          name: newMusic.name,
          artist: newMusic.artist,
          url: newMusic.url,
          duration: newMusic.duration,
          category: 'custom'
        });
      }
    }
  };

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* 标题 */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="text-gray-900 font-semibold text-sm">音乐设置</h3>
      </div>

      {/* 内容区域 */}
      <div className="flex-1 overflow-y-auto p-4">
        <div className="space-y-4">
            {/* 文件上传 */}
            <div>
              <label className="block text-gray-700 text-sm mb-2">上传音乐文件</label>
              <label className="flex items-center justify-center w-full h-24 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer hover:border-gray-400 transition-colors">
                <div className="text-center">
                  <Upload size={24} className="text-gray-500 mx-auto mb-2" />
                  <span className="text-gray-600 text-sm">点击上传音乐</span>
                </div>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="audio/*"
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
                  onClick={() => fetchUserMusic(true)}
                  className="mt-2 text-red-600 text-sm underline hover:no-underline"
                >
                  重试
                </button>
              </div>
            )}

            {/* 加载状态 */}
            {isLoading && userMusic.length === 0 && (
              <div className="text-center py-8">
                <div className="w-8 h-8 border-2 border-gray-300 border-t-teal-600 rounded-full animate-spin mx-auto mb-4"></div>
                <p className="text-gray-500 text-sm">正在加载音乐文件...</p>
              </div>
            )}

            {/* 用户上传的音乐 */}
            {userMusic.length > 0 && (
              <div>
                <label className="block text-gray-700 text-sm mb-2">我的音乐</label>
                <div className="space-y-2">
                  {userMusic.map((music) => (
                    <button
                      key={music.id}
                      onClick={() => addMusicToCanvas({
                        name: music.name,
                        artist: music.artist,
                        url: music.url,
                        duration: music.duration,
                        category: 'custom'
                      })}
                      className="w-full group p-3 rounded-lg border-2 border-gray-300 hover:border-teal-500 transition-colors"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-gradient-to-br from-purple-500 to-pink-600 rounded-lg flex items-center justify-center flex-shrink-0">
                          <Music2 size={20} className="text-white" />
                        </div>
                        
                        <div className="flex-1 min-w-0 text-left">
                          <h5 className="text-gray-900 text-sm font-medium truncate">{music.name}</h5>
                          <p className="text-gray-600 text-xs truncate">{music.artist}</p>
                        </div>
                        
                        <div className="px-3 py-1 text-xs font-medium rounded-lg bg-gray-100 text-gray-700">
                          使用
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
                
                {/* 加载更多按钮 */}
                {hasMore && (
                  <div className="mt-4 text-center">
                    <button
                      onClick={() => fetchUserMusic(false)}
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
            {userMusic.length === 0 && !isLoading && !error && (
              <div className="text-center py-8">
                <Music size={48} className="text-gray-300 mx-auto mb-4" />
                <p className="text-gray-500 text-sm">还没有上传音乐文件</p>
                <p className="text-gray-400 text-xs mt-1">点击上方按钮上传您的音乐文件</p>
              </div>
            )}
        </div>
      </div>
    </div>
  );
};

export default MusicPanel;
