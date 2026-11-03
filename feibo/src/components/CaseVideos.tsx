import React, { useState, useEffect, useRef } from 'react';
import { Play, Eye, Calendar, Film, XCircle, Loader2, Star, Tag } from 'lucide-react';

interface CaseVideo {
  id: string;
  name: string;
  description: string;
  category: string;
  tags: string[];
  videoUrl?: string;
  coverUrl?: string;
  thumbnailUrl?: string;
  duration?: string | number;
  isPopular?: boolean;
  createTime: string;
}

interface CaseVideosProps {
  onClose?: () => void;
}

const CaseVideos: React.FC<CaseVideosProps> = ({ onClose }) => {
  const [videos, setVideos] = useState<CaseVideo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedVideo, setSelectedVideo] = useState<CaseVideo | null>(null);
  const [isVideoModalOpen, setIsVideoModalOpen] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [videoDurations, setVideoDurations] = useState<Record<string, string>>({});

  // 案例视频数据
  const caseVideosData: CaseVideo[] = [
    {
      id: '1',
      name: '健康知识普及宣传视频',
      description: '向观众普及一些有关健康的知识和小贴士，帮助大家更好地了解如何保持身体健康，迎接更加健康的生活',
      category: '医疗科普',
      tags: ['数字人', '健康科普', '疾病预防'],
      videoUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473495131.mp4',
      coverUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710398070868.png',
      thumbnailUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710398070868.png',
      duration: '0:30',
      isPopular: true,
      createTime: new Date().toISOString()
    },
    {
      id: '2',
      name: '血管介入诊疗知情同意书',
      description: '知情同意书是患者在接受血管介入诊疗前需签署的文件，其中包括了治疗过程、风险、可能的并发症等内容。',
      category: '医疗科普',
      tags: ['数字人', '介入治疗','医疗科普'],
      videoUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473502027.mp4',
      coverUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710398132347.png',
      thumbnailUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710398132347.png',
      duration: '0:45',
      isPopular: false,
      createTime: new Date().toISOString()
    },
    {
      id: '3',
      name: '喉炎会转化为喉癌吗',
      description: '许多人担心患上喉炎会增加患喉癌的风险，但事实真的如此吗？我们将从医学角度分析，了解喉炎是否会转化为喉癌。',
      category: '医疗科普',
      tags: ['数字人', '癌症', '早期诊断'],
      videoUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473485563.mp4',
      coverUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710397910840.png',
      thumbnailUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710397910840.png',
      duration: '1:00',
      isPopular: true,
      createTime: new Date().toISOString()
    },
    {
      id: '4',
      name: '皮肤擦伤怎么办',
      description: '这个视频教你如何正确处理皮肤擦伤的情况。包括清洁伤口、消毒处理、贴合适的敷料等步骤，以减少感染和促进伤口愈合。',
      category: '医疗科普',
      tags: ['数字人', '皮肤擦伤', '伤口处理'],
      videoUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473473392.mp4',
      coverUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710397811219.png',
      thumbnailUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710397811219.png',
      duration: '0:35',
      isPopular: false,
      createTime: new Date().toISOString()
    },
    {
      id: '5',
      name: '什么是身高管理',
      description: '身高管理是指一系列的健康措施，旨在帮助个体达到其最高潜力的身高。这包括提供充足的营养、保持健康的生活方式、适当的锻炼等。',
      category: '医疗科普',
      tags: ['数字人', '青少年', '身高管理'],
      videoUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473609418.mp4',
      coverUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710397501278.png',
      thumbnailUrl: 'https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710397501278.png',
      duration: '0:50',
      isPopular: true,
      createTime: new Date().toISOString()
    }
  ];

  // 视频分类
  const categories = [
    { id: 'all', name: '全部' },
    { id: '医疗科普', name: '医疗科普' }
  ];

  // 加载案例视频
  const loadCaseVideos = async () => {
    try {
      setLoading(true);
      setError(null);

      // 模拟加载延迟
      await new Promise(resolve => setTimeout(resolve, 500));
      
      // 直接使用静态数据
      setVideos(caseVideosData);
      
      // 获取每个视频的实际时长
      caseVideosData.forEach(video => {
        if (video.videoUrl) {
          getVideoDuration(video.videoUrl, video.id).catch(error => {
            console.warn(`Failed to get duration for video ${video.id}:`, error);
          });
        }
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载案例视频失败');
    } finally {
      setLoading(false);
    }
  };

  // 预览视频
  const handlePreview = (video: CaseVideo) => {
    setSelectedVideo(video);
    setIsVideoModalOpen(true);
  };

  // 格式化视频时长
  const formatDuration = (seconds: number): string => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);
    
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    } else {
      return `${minutes}:${secs.toString().padStart(2, '0')}`;
    }
  };

  // 获取视频实际时长
  const getVideoDuration = (videoUrl: string, videoId: string): Promise<string> => {
    return new Promise((resolve, reject) => {
      const video = document.createElement('video');
      video.preload = 'metadata';
      
      video.onloadedmetadata = () => {
        const duration = formatDuration(video.duration);
        setVideoDurations(prev => ({ ...prev, [videoId]: duration }));
        resolve(duration);
      };
      
      video.onerror = () => {
        const fallbackDuration = '0:00';
        setVideoDurations(prev => ({ ...prev, [videoId]: fallbackDuration }));
        reject(new Error('Failed to load video metadata'));
      };
      
      video.src = videoUrl;
    });
  };

  // 格式化时间
  const formatTime = (timeStr: string) => {
    try {
      const date = new Date(timeStr);
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
      });
    } catch {
      return timeStr;
    }
  };

  // 过滤视频
  const filteredVideos = videos.filter(video => 
    selectedCategory === 'all' || video.category === selectedCategory
  );

  useEffect(() => {
    loadCaseVideos();
  }, []);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-6xl h-5/6 mx-4 flex flex-col">
        {/* 头部 */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <div className="flex items-center space-x-3">
            <Film className="w-6 h-6 text-teal-600" />
            <h2 className="text-xl font-semibold text-gray-900">案例视频</h2>
            <span className="text-sm text-gray-500">({filteredVideos.length} 个案例)</span>
          </div>
          {onClose && (
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-600 transition-colors"
            >
              <XCircle className="w-6 h-6" />
            </button>
          )}
        </div>

        {/* 分类筛选 */}
        <div className="px-6 py-4 border-b border-gray-100">
          <div className="flex items-center space-x-2 overflow-x-auto">
            {categories.map((category) => (
              <button
                key={category.id}
                onClick={() => setSelectedCategory(category.id)}
                className={`px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-all duration-200 ${
                  selectedCategory === category.id
                    ? 'bg-teal-600 text-white shadow-md'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {category.name}
              </button>
            ))}
          </div>
        </div>

        {/* 内容区域 */}
        <div className="flex-1 overflow-hidden">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <Loader2 className="w-8 h-8 text-teal-600 animate-spin mx-auto mb-2" />
                <p className="text-gray-600">加载中...</p>
              </div>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <XCircle className="w-8 h-8 text-red-500 mx-auto mb-2" />
                <p className="text-red-600 mb-4">{error}</p>
                <button
                  onClick={loadCaseVideos}
                  className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
                >
                  重试
                </button>
              </div>
            </div>
          ) : filteredVideos.length === 0 ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <Film className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <p className="text-gray-600 text-lg mb-2">暂无案例视频</p>
                <p className="text-gray-500">该分类下暂时没有案例视频</p>
              </div>
            </div>
          ) : (
            <div className="p-6 h-full overflow-y-auto">
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                {filteredVideos.map((video) => (
                  <div key={video.id} className="bg-white border border-gray-200 rounded-lg shadow-sm hover:shadow-lg transition-all duration-300 transform hover:-translate-y-1">
                    {/* 视频预览 */}
                    <div className="relative aspect-video bg-gradient-to-br from-gray-100 to-gray-200 rounded-t-lg overflow-hidden group">
                      {video.coverUrl || video.thumbnailUrl ? (
                        <img
                          src={video.coverUrl || video.thumbnailUrl}
                          alt={video.name}
                          className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                        />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center bg-gradient-to-br from-teal-100 to-teal-200">
                          <Film className="w-12 h-12 text-teal-400" />
                        </div>
                      )}
                      
                      {/* 热门标识 */}
                      {video.isPopular && (
                        <div className="absolute top-3 left-3">
                          <div className="flex items-center space-x-1 bg-gradient-to-r from-orange-500 to-red-500 text-white px-3 py-1.5 rounded-full text-xs font-medium shadow-lg">
                            <Star className="w-3 h-3 fill-current" />
                            <span>热门</span>
                          </div>
                        </div>
                      )}

                      {/* 时长显示 */}
                      <div className="absolute top-3 right-3">
                        <div className="bg-black bg-opacity-70 backdrop-blur-sm text-white px-2 py-1 rounded text-xs">
                          {videoDurations[video.id] ? (
                            videoDurations[video.id]
                          ) : video.duration ? (
                            video.duration
                          ) : (
                            <div className="flex items-center space-x-1">
                              <Loader2 className="w-3 h-3 animate-spin" />
                              <span>--:--</span>
                            </div>
                          )}
                        </div>
                      </div>

                      {/* 播放按钮 */}
                      <div className="absolute inset-0 flex items-center justify-center bg-black bg-opacity-0 group-hover:bg-opacity-40 transition-all duration-300">
                        <button
                          onClick={() => handlePreview(video)}
                          className="w-16 h-16 bg-white bg-opacity-90 hover:bg-opacity-100 rounded-full flex items-center justify-center transition-all duration-300 transform scale-0 group-hover:scale-100 shadow-lg"
                        >
                          <Play className="w-8 h-8 text-teal-600 ml-1" />
                        </button>
                      </div>
                    </div>

                    {/* 视频信息 */}
                    <div className="p-5">
                      <h3 className="font-semibold text-gray-900 mb-2 truncate text-lg" title={video.name}>
                        {video.name}
                      </h3>
                      
                      <p className="text-sm text-gray-600 mb-3 line-clamp-2" title={video.description}>
                        {video.description}
                      </p>

                      {/* 标签 */}
                      <div className="flex flex-wrap gap-1 mb-3">
                        {video.tags.slice(0, 3).map((tag, index) => (
                          <span
                            key={index}
                            className="inline-flex items-center px-2 py-1 bg-teal-50 text-teal-700 text-xs rounded-full"
                          >
                            <Tag className="w-3 h-3 mr-1" />
                            {tag}
                          </span>
                        ))}
                      </div>
                      
                      <div className="flex items-center justify-between">
                        <div className="flex items-center text-sm text-gray-500">
                          <Calendar className="w-4 h-4 mr-2 text-teal-500" />
                          <span>{formatTime(video.createTime)}</span>
                        </div>
                        
                        <button
                          onClick={() => handlePreview(video)}
                          className="p-2 text-gray-600 hover:text-teal-600 hover:bg-teal-50 rounded-lg transition-all duration-200 transform hover:scale-105"
                          title="预览"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 视频预览模态框 */}
      {isVideoModalOpen && selectedVideo && (
        <div className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-60">
          <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full mx-4">
            <div className="flex items-center justify-between p-4 border-b border-gray-200">
              <div>
                <h3 className="text-lg font-semibold text-gray-900">{selectedVideo.name}</h3>
                <p className="text-sm text-gray-600 mt-1">{selectedVideo.description}</p>
              </div>
              <button
                onClick={() => setIsVideoModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 transition-colors"
              >
                <XCircle className="w-6 h-6" />
              </button>
            </div>
            <div className="p-4">
              {selectedVideo.videoUrl ? (
                <video
                  src={selectedVideo.videoUrl}
                  controls
                  className="w-full max-h-96 rounded-lg"
                  poster={selectedVideo.coverUrl || selectedVideo.thumbnailUrl}
                >
                  您的浏览器不支持视频播放。
                </video>
              ) : (
                <div className="aspect-video bg-gray-100 rounded-lg flex items-center justify-center">
                  <div className="text-center">
                    <Film className="w-12 h-12 text-gray-400 mx-auto mb-2" />
                    <p className="text-gray-500">视频不可用</p>
                  </div>
                </div>
              )}
              
              {/* 标签展示 */}
              <div className="flex flex-wrap gap-2 mt-4">
                {selectedVideo.tags.map((tag, index) => (
                  <span
                    key={index}
                    className="inline-flex items-center px-3 py-1 bg-teal-50 text-teal-700 text-sm rounded-full"
                  >
                    <Tag className="w-3 h-3 mr-1" />
                    {tag}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CaseVideos;