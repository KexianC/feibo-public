import React, { useState, useRef, useEffect } from 'react';
import { Play, Pause, Upload, Volume2, VolumeX, RotateCcw, Scissors, SlidersHorizontal, Video } from 'lucide-react';
import { useStore } from '../store';
import { v4 as uuidv4 } from 'uuid';
import { uploadVideo } from '../services/fileUploadApi';
import { getUserVideos, UserVideo } from '../services/fileUploadApi';

const VideoPanel: React.FC = () => {
  const { actions: { addElement, getNextZIndex } } = useStore();
  const [userVideos, setUserVideos] = useState<UserVideo[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastCursorId, setLastCursorId] = useState<string>('');
  const [hasMore, setHasMore] = useState(true);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [volume, setVolume] = useState(50);
  const [opacity, setOpacity] = useState(100);
  const [filter, setFilter] = useState('none');

  const videoRef = useRef<HTMLVideoElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 获取视频实际时长
  const getVideoDuration = (videoUrl: string): Promise<number> => {
    return new Promise((resolve, reject) => {
      const video = document.createElement('video');
      video.preload = 'metadata';
      video.onloadedmetadata = () => {
        resolve(Math.floor(video.duration));
      };
      video.onerror = () => {
        reject(new Error('无法加载视频'));
      };
      video.src = videoUrl;
    });
  };

  // 格式化时长显示
  const formatDuration = (seconds: number): string => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const remainingSeconds = seconds % 60;

    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`;
    } else {
      return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
    }
  };

  const filters = [
    { id: 'none', name: '无滤镜' },
    { id: 'blur', name: '模糊' },
    { id: 'brightness', name: '亮度' },
    { id: 'contrast', name: '对比度' },
    { id: 'grayscale', name: '灰度' },
    { id: 'sepia', name: '复古' }
  ];

  // 视频播放控制函数（保留以备将来使用）
  const togglePlay = () => {
    if (videoRef.current) {
      if (isPlaying) {
        videoRef.current.pause();
      } else {
        videoRef.current.play();
      }
      setIsPlaying(!isPlaying);
    }
  };

  const toggleMute = () => {
    if (videoRef.current) {
      videoRef.current.muted = !isMuted;
      setIsMuted(!isMuted);
    }
  };

  const handleVolumeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newVolume = parseInt(e.target.value);
    setVolume(newVolume);
    if (videoRef.current) {
      videoRef.current.volume = newVolume / 100;
    }
  };

  // 获取用户视频数据
  const fetchUserVideos = async (reset: boolean = false) => {
    if (isLoading) return;
    
    setIsLoading(true);
    setError(null);
    
    try {
      const cursorId = reset ? '' : lastCursorId;
      const response = await getUserVideos(cursorId, 10);
      
      if (response.code === '0000') {
        const { data: videosData, hasNext, nextCursorId } = response.data;
        
        const videos = await Promise.all((videosData || []).map(async (item: any, index: number) => {
          let duration = 0;
          try {
            duration = await getVideoDuration(item.url);
          } catch (error) {
            console.warn(`获取用户视频时长失败:`, error);
          }
          
          return {
            id: `video_${Date.now()}_${index}`,
            name: item.name || '未命名视频',
            url: item.url,
            thumbnail: 'https://trae-api-sg.mchost.guru/api/ide/v1/text_to_image?prompt=video%20file%20thumbnail%20placeholder&image_size=landscape_4_3',
            duration: duration
          };
        }));
        
        if (reset) {
          setUserVideos(videos);
        } else {
          setUserVideos(prev => [...prev, ...videos]);
        }
        
        setHasMore(hasNext || false);
        setLastCursorId(nextCursorId || '');
      } else {
        setError(response.info || '获取用户视频失败');
      }
    } catch (error) {
      console.error('获取用户视频失败:', error);
      setError('获取用户视频失败，请稍后重试');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchUserVideos(true);
  }, []);

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && file.type.startsWith('video/')) {
      try {
        // 获取视频时长
        const fileUrl = URL.createObjectURL(file);
        const duration = await getVideoDuration(fileUrl);
        
        // 使用统一的上传接口
        const response = await uploadVideo(file);
        if (response.code === '0000' && response.data) {
          // 添加到用户视频库
          const newVideo = {
            id: response.data.fileId,
            name: file.name.replace(/\.[^/.]+$/, ''),
            url: response.data.fileUrl,
            thumbnail: 'https://trae-api-sg.mchost.guru/api/ide/v1/text_to_image?prompt=video%20file%20thumbnail%20placeholder&image_size=landscape_4_3',
            duration: duration
          };
          setUserVideos(prev => [newVideo, ...prev]);
          
          // 直接添加到画布
          addVideoToCanvas(newVideo.url, newVideo.name);
        } else {
          throw new Error('上传失败');
        }
        
        // 清理临时URL
        URL.revokeObjectURL(fileUrl);
      } catch (error) {
        console.error('视频上传失败:', error);
        // 降级处理：使用本地预览
        const url = URL.createObjectURL(file);
        let duration = 0;
        try {
          duration = await getVideoDuration(url);
        } catch (durationError) {
          console.warn('获取视频时长失败:', durationError);
        }
        
        const newVideo = {
          id: uuidv4(),
          name: file.name.replace(/\.[^/.]+$/, ''),
          url,
          thumbnail: 'https://trae-api-sg.mchost.guru/api/ide/v1/text_to_image?prompt=video%20file%20thumbnail%20placeholder&image_size=landscape_4_3',
          duration: duration
        };
        setUserVideos(prev => [newVideo, ...prev]);
        addVideoToCanvas(url, newVideo.name);
      }
    }
  };

  // 添加视频到画布的统一函数
  const addVideoToCanvas = (videoUrl: string, videoName: string) => {
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
      type: 'video' as const,
      position: { x: canvasWidth / 2 - 200, y: canvasHeight / 2 - 150 },
      size: { width: 400, height: 300 },
      rotation: 0,
      zIndex: getNextZIndex('video'),
      properties: {
        src: videoUrl,
        name: videoName,
        opacity: opacity / 100,
        filter,
        volume: volume / 100,
        muted: isMuted,
        autoplay: false,
        loop: false
      }
    };

    addElement(element);
  };

  const getFilterStyle = () => {
    switch (filter) {
      case 'blur': return 'blur(2px)';
      case 'brightness': return 'brightness(1.2)';
      case 'contrast': return 'contrast(1.2)';
      case 'grayscale': return 'grayscale(100%)';
      case 'sepia': return 'sepia(100%)';
      default: return 'none';
    }
  };

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* 标题 */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="text-gray-900 font-semibold text-sm">视频设置</h3>
      </div>

      {/* 内容区域 */}
      <div className="flex-1 overflow-y-auto p-4">
        <div className="space-y-4">
            {/* 文件上传 */}
            <div>
              <label className="block text-gray-700 text-sm mb-2">上传视频文件</label>
              <label className="flex items-center justify-center w-full h-24 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer hover:border-gray-400 transition-colors">
                <div className="text-center">
                  <Upload size={24} className="text-gray-500 mx-auto mb-2" />
                  <span className="text-gray-600 text-sm">点击上传视频</span>
                </div>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="video/*"
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
                  onClick={() => fetchUserVideos(true)}
                  className="mt-2 text-red-600 text-sm underline hover:no-underline"
                >
                  重试
                </button>
              </div>
            )}

            {/* 加载状态 */}
            {isLoading && userVideos.length === 0 && (
              <div className="text-center py-8">
                <div className="w-8 h-8 border-2 border-gray-300 border-t-teal-600 rounded-full animate-spin mx-auto mb-4"></div>
                <p className="text-gray-500 text-sm">正在加载视频文件...</p>
              </div>
            )}

            {/* 用户上传的视频 */}
            {userVideos.length > 0 && (
              <div>
                <label className="block text-gray-700 text-sm mb-2">我的视频</label>
                <div className="grid grid-cols-2 gap-3">
                  {userVideos.map((video) => (
                    <button
                      key={video.id}
                      onClick={() => addVideoToCanvas(video.url, video.name)}
                      className="group cursor-pointer rounded-lg overflow-hidden border-2 border-gray-300 hover:border-teal-500 transition-colors"
                    >
                      <div className="relative">
                        <img
                          src={video.thumbnail}
                          alt={video.name}
                          className="w-full h-20 object-cover"
                          onError={(e) => {
                            const target = e.target as HTMLImageElement;
                            target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjQiIGhlaWdodD0iMjQiIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3Qgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0IiBmaWxsPSIjRjNGNEY2Ii8+CjxwYXRoIGQ9Ik0xMiAxNkM4LjY4NjI5IDE2IDYgMTMuMzEzNyA2IDEwQzYgNi42ODYyOSA4LjY4NjI5IDQgMTIgNEMxNS4zMTM3IDQgMTggNi42ODYyOSAxOCAxMEMxOCAxMy4zMTM3IDE1LjMxMzcgMTYgMTIgMTZaIiBmaWxsPSIjOUI5QkEwIi8+CjxwYXRoIGQ9Ik0xMiAxNEMxMy4xMDQ2IDE0IDE0IDEzLjEwNDYgMTQgMTJDMTQgMTAuODk1NCAxMy4xMDQ2IDEwIDEyIDEwQzEwLjg5NTQgMTAgMTAgMTAuODk1NCAxMCAxMkMxMCAxMy4xMDQ2IDEwLjg5NTQgMTQgMTIgMTRaIiBmaWxsPSIjNjM2MzZBIi8+Cjwvc3ZnPgo=';
                          }}
                        />
                        
                        {/* 播放图标覆盖 */}
                        <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                          <Play size={20} className="text-white" />
                        </div>
                        
                        {/* 时长标签 */}
                        {video.duration > 0 && (
                          <div className="absolute bottom-1 right-1 bg-black/70 text-white text-xs px-1 rounded">
                            {formatDuration(video.duration)}
                          </div>
                        )}
                      </div>
                      
                      <div className="p-2">
                        <h5 className="text-gray-900 text-xs font-medium truncate">{video.name}</h5>
                      </div>
                    </button>
                  ))}
                </div>
                
                {/* 加载更多按钮 */}
                {hasMore && (
                  <div className="mt-4 text-center">
                    <button
                      onClick={() => fetchUserVideos(false)}
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
            {userVideos.length === 0 && !isLoading && !error && (
              <div className="text-center py-8">
                <Video size={48} className="text-gray-300 mx-auto mb-4" />
                <p className="text-gray-500 text-sm">还没有上传视频文件</p>
                <p className="text-gray-400 text-xs mt-1">点击上方按钮上传您的视频文件</p>
              </div>
            )}
        </div>
      </div>
    </div>
  );
};

export default VideoPanel;
