import React, { useState, useEffect } from 'react';
import { Play, Download, Trash2, Clock, Clock4, CheckCircle, XCircle, Loader2, Eye, Calendar, Film, Edit2, Save, X } from 'lucide-react';
import {
  downloadVideo,
  getVideoInfo,
  getUserWorks,
  updateWorkTitle,
  deleteUserWork,
  type UserWork,
  type VideoInfo,
} from '../services/digitalHumanApi';
import { useStore } from '../store';

interface MyWorksProps {
  onClose?: () => void;
}

const MyWorks: React.FC<MyWorksProps> = ({ onClose }) => {
  const [works, setWorks] = useState<UserWork[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [selectedWork, setSelectedWork] = useState<UserWork | null>(null);
  const [isVideoModalOpen, setIsVideoModalOpen] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingTitle, setEditingTitle] = useState<string>('');

  // 从store获取用户作品数据
  const { userWorks, actions } = useStore();
  
  /**
   * 加载作品列表
   * 使用新的后端接口获取作品数据，并通过getVideoInfo获取视频详细信息
   * 同时合并本地store中保存的作品，确保备用接口合成的作品不被覆盖
   */
  const loadWorks = async (lastCursorId?: string) => {
    try {
      setLoading(true);
      setError(null);
      
      // 获取本地store中保存的作品
      const localWorks = useStore.getState().userWorks || [];
      console.log('本地保存的作品数量:', localWorks.length);
      
      // 使用新的API接口获取服务器作品列表
      const result = await getUserWorks(lastCursorId, 20);
      
      // 为每个服务器作品使用externalId获取视频详细信息
      const worksWithVideoInfo = await Promise.allSettled(
        result.records.map(async (work) => {
          try {
            // 使用externalId而不是digitalWorkId来查询视频信息
            const videoInfo = await getVideoInfo(work.externalId);
            return {
              ...work,
              videoUrl: videoInfo.videoUrl || work.videoUrl,
              coverUrl: videoInfo.coverUrl || work.coverUrl,
              thumbnailUrl: videoInfo.thumbnailUrl || work.thumbnailUrl,
              duration: videoInfo.duration || work.duration,
              width: videoInfo.width || work.width,
              height: videoInfo.height || work.height,
              status: videoInfo.status || work.status,
              progress: videoInfo.progress || work.progress,
              errorMessage: videoInfo.errorMessage || work.errorMessage,
              // 确保正确映射创建时间和更新时间
              createTime: videoInfo.createTime || work.createTime,
              updateTime: videoInfo.updateTime || work.updateTime
            };
          } catch (error) {
            console.warn(`获取作品${work.digitalWorkId}的视频信息失败（使用externalId: ${work.externalId}）:`, error);
            // 如果获取视频信息失败，返回原始作品数据
            return work;
          }
        })
      );
      
      // 提取成功的服务器作品结果
      const serverWorks = worksWithVideoInfo
        .filter(result => result.status === 'fulfilled')
        .map(result => (result as PromiseFulfilledResult<UserWork>).value);
      
      /**
       * 合并本地作品和服务器作品
       * 1. 创建一个Map用于去重，以taskId或digitalWorkId为键
       * 2. 优先保留本地作品（备用接口合成的作品）
       * 3. 按创建时间倒序排列，新作品在前
       */
      const worksMap = new Map<string, UserWork>();
      
      // 先添加本地作品（优先级更高，避免被服务器作品覆盖）
      localWorks.forEach(work => {
        const key = work.taskId || work.digitalWorkId || work.id;
        if (key) {
          worksMap.set(key, work);
        }
      });
      
      // 再添加服务器作品（只有当本地没有相同作品时才添加）
      serverWorks.forEach(work => {
        const key = work.taskId || work.digitalWorkId || work.id;
        if (key && !worksMap.has(key)) {
          worksMap.set(key, work);
        }
      });
      
      // 转换为数组并按创建时间倒序排列
      const mergedWorks = Array.from(worksMap.values()).sort((a, b) => {
        const timeA = new Date(a.createTime || a.updateTime || 0).getTime();
        const timeB = new Date(b.createTime || b.updateTime || 0).getTime();
        return timeB - timeA; // 倒序排列，新作品在前
      });
      
      setWorks(mergedWorks);
      setTotal(mergedWorks.length);
      
      console.log('成功加载并合并作品列表:', {
        本地作品: localWorks.length,
        服务器作品: serverWorks.length,
        合并后总数: mergedWorks.length
      });
    } catch (err) {
      console.error('加载作品失败:', err);
      setError(err instanceof Error ? err.message : '加载作品失败');
    } finally {
      setLoading(false);
    }
  };

  /**
   * 开始编辑标题
   */
  const handleStartEdit = (work: UserWork) => {
    setEditingId(work.id);
    setEditingTitle(work.name);
  };

  /**
   * 取消编辑标题
   */
  const handleCancelEdit = () => {
    setEditingId(null);
    setEditingTitle('');
  };

  /**
   * 保存标题修改
   */
  const handleSaveTitle = async (work: UserWork) => {
    if (!editingTitle.trim()) {
      alert('标题不能为空');
      return;
    }

    try {
      // 使用digitalWorkId字段
      await updateWorkTitle(work.digitalWorkId, editingTitle.trim());
      
      // 更新本地数据
      setWorks(prevWorks => 
        prevWorks.map(w => 
          w.id === work.id ? { ...w, name: editingTitle.trim(), title: editingTitle.trim() } : w
        )
      );
      
      setEditingId(null);
      setEditingTitle('');
      alert('标题修改成功');
    } catch (err) {
      console.error('修改标题失败:', err);
      alert(err instanceof Error ? err.message : '修改标题失败');
    }
  };

  /**
   * 删除作品
   * 使用新的后端接口删除作品
   */
  const handleDelete = async (work: UserWork) => {
    if (!confirm(`确定要删除作品 "${work.name}" 吗？此操作不可撤销。`)) {
      return;
    }

    try {
      setDeletingId(work.id);
      
      // 调用新的删除接口，使用digitalWorkId
      await deleteUserWork(work.digitalWorkId);
      
      // 从本地数据中移除
      setWorks(prevWorks => prevWorks.filter(w => w.id !== work.id));
      setTotal(prev => prev - 1);
      
      alert('删除成功');
    } catch (err) {
      console.error('删除作品失败:', err);
      alert(err instanceof Error ? err.message : '删除失败');
    } finally {
      setDeletingId(null);
    }
  };

  // 下载视频
  const handleDownload = (work: UserWork) => {
    if (work.videoUrl) {
      downloadVideo(work.videoUrl, `${work.name}.mp4`);
    }
  };

  // 预览视频
  const handlePreview = (work: UserWork) => {
    setSelectedWork(work);
    setIsVideoModalOpen(true);
  };

  /**
   * 格式化时间显示
   * 支持多种日期格式，避免显示invalid
   */
  const formatTime = (timeStr: string) => {
    if (!timeStr) {
      return '未知时间';
    }

    try {
      // 尝试解析日期
      const date = new Date(timeStr);
      
      // 检查日期是否有效
      if (isNaN(date.getTime())) {
        console.warn('无效的日期格式:', timeStr);
        return timeStr; // 返回原始字符串
      }

      // 计算相对时间
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMinutes = Math.floor(diffMs / (1000 * 60));
      const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

      // 显示相对时间
      if (diffMinutes < 1) {
        return '刚刚';
      } else if (diffMinutes < 60) {
        return `${diffMinutes}分钟前`;
      } else if (diffHours < 24) {
        return `${diffHours}小时前`;
      } else if (diffDays < 7) {
        return `${diffDays}天前`;
      } else {
        // 超过7天显示具体日期
        return date.toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit'
        });
      }
    } catch (error) {
      console.warn('格式化时间失败:', timeStr, error);
      return timeStr; // 返回原始字符串作为回退
    }
  };

  // 获取状态图标
  const getStatusIcon = (status: UserWork['status']) => {
    switch (status) {
      case 'pending':
        return <Clock4 className="w-4 h-4 text-yellow-500" />;
      case 'processing':
        return <Loader2 className="w-4 h-4 text-blue-500 animate-spin" />;
      case 'completed':
      case 'suc':
        return <CheckCircle className="w-4 h-4 text-green-500" />;
      case 'failed':
      case 'fail':
        return <XCircle className="w-4 h-4 text-red-500" />;
      default:
        return <Clock4 className="w-4 h-4 text-gray-500" />;
    }
  };

  // 获取状态文本
  const getStatusText = (status: UserWork['status']) => {
    switch (status) {
      case 'pending':
        return '等待中';
      case 'processing':
        return '合成中';
      case 'completed':
      case 'suc':
        return '已完成';
      case 'failed':
      case 'fail':
        return '失败';
      default:
        return '未知';
    }
  };

  /**
   * 分页处理
   */
  const handlePageChange = (page: number) => {
    if (page >= 1 && page <= totalPages && page !== currentPage) {
      // 这里可以根据需要实现基于lastCursorId的分页
      loadWorks();
    }
  };

  useEffect(() => {
    loadWorks();
  }, []);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-6xl h-5/6 mx-4 flex flex-col">
        {/* 头部 */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <div className="flex items-center space-x-3">
            <Film className="w-6 h-6 text-teal-600" />
            <h2 className="text-xl font-semibold text-gray-900">我的作品</h2>
            <span className="text-sm text-gray-500">({total} 个作品)</span>
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
                  onClick={() => loadWorks()}
                  className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
                >
                  重试
                </button>
              </div>
            </div>
          ) : works.length === 0 ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <Film className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <p className="text-gray-600 text-lg mb-2">暂无作品</p>
                <p className="text-gray-500">开始创建您的第一个视频作品吧！</p>
              </div>
            </div>
          ) : (
            <div className="p-6 h-full overflow-y-auto">
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                {works.map((work) => (
                  <div key={work.id} className="bg-white border border-gray-200 rounded-lg shadow-sm hover:shadow-lg transition-all duration-300 transform hover:-translate-y-1">
                    {/* 视频预览 */}
                    <div className="relative aspect-video bg-gradient-to-br from-gray-100 to-gray-200 rounded-t-lg overflow-hidden group">
                      {work.coverUrl || work.thumbnailUrl ? (
                        <img
                          src={work.coverUrl || work.thumbnailUrl}
                          alt={work.name}
                          className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                        />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center bg-gradient-to-br from-teal-100 to-teal-200">
                          <Film className="w-12 h-12 text-teal-400" />
                        </div>
                      )}
                      
                      {/* 状态覆盖层 */}
                      <div className="absolute top-3 left-3">
                        <div className="flex items-center space-x-1 bg-black bg-opacity-70 backdrop-blur-sm text-white px-3 py-1.5 rounded-full text-xs font-medium">
                          {getStatusIcon(work.status)}
                          <span>{getStatusText(work.status)}</span>
                        </div>
                      </div>

                      {/* 时长显示 */}
                      {work.duration && (
                        <div className="absolute top-3 right-3">
                          <div className="bg-black bg-opacity-70 backdrop-blur-sm text-white px-2 py-1 rounded text-xs">
                            {typeof work.duration === 'string' ? work.duration : `${Math.floor(Number(work.duration) / 60)}:${String(Number(work.duration) % 60).padStart(2, '0')}`}
                          </div>
                        </div>
                      )}

                      {/* 播放按钮 */}
                      {(work.status === 'completed' || work.status === 'suc') && work.videoUrl && (
                        <div className="absolute inset-0 flex items-center justify-center bg-black bg-opacity-0 group-hover:bg-opacity-40 transition-all duration-300">
                          <button
                            onClick={() => handlePreview(work)}
                            className="w-16 h-16 bg-white bg-opacity-90 hover:bg-opacity-100 rounded-full flex items-center justify-center transition-all duration-300 transform scale-0 group-hover:scale-100 shadow-lg"
                          >
                            <Play className="w-8 h-8 text-teal-600 ml-1" />
                          </button>
                        </div>
                      )}
                    </div>

                    {/* 作品信息 */}
                    <div className="p-5">
                      {/* 标题编辑区域 */}
                      {editingId === work.id ? (
                        <div className="mb-3">
                          <input
                            type="text"
                            value={editingTitle}
                            onChange={(e) => setEditingTitle(e.target.value)}
                            className="w-full px-2 py-1 text-lg font-semibold border border-teal-300 rounded focus:outline-none focus:border-teal-500"
                            autoFocus
                            onKeyPress={(e) => {
                              if (e.key === 'Enter') {
                                handleSaveTitle(work);
                              } else if (e.key === 'Escape') {
                                handleCancelEdit();
                              }
                            }}
                          />
                          <div className="flex items-center space-x-2 mt-2">
                            <button
                              onClick={() => handleSaveTitle(work)}
                              className="p-1 text-green-600 hover:bg-green-50 rounded transition-colors"
                              title="保存"
                            >
                              <Save className="w-4 h-4" />
                            </button>
                            <button
                              onClick={handleCancelEdit}
                              className="p-1 text-gray-600 hover:bg-gray-50 rounded transition-colors"
                              title="取消"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex items-center justify-between mb-3">
                          <h3 className="font-semibold text-gray-900 truncate text-lg flex-1" title={work.name}>
                            {work.name}
                          </h3>
                          <button
                            onClick={() => handleStartEdit(work)}
                            className="p-1 text-gray-400 hover:text-teal-600 hover:bg-teal-50 rounded transition-colors ml-2"
                            title="编辑标题"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                      
                      <div className="flex items-center text-sm text-gray-500 mb-4">
                        <Calendar className="w-4 h-4 mr-2 text-teal-500" />
                        <span>{formatTime(work.createTime)}</span>
                      </div>

                      {/* 进度条 */}
                      {work.status === 'processing' && work.progress !== undefined && (
                        <div className="mb-4">
                          <div className="flex items-center justify-between text-xs text-gray-600 mb-1">
                            <span>合成进度</span>
                            <span>{work.progress}%</span>
                          </div>
                          <div className="w-full bg-gray-200 rounded-full h-2">
                            <div 
                              className="bg-gradient-to-r from-teal-500 to-teal-600 h-2 rounded-full transition-all duration-300"
                              style={{ width: `${work.progress}%` }}
                            ></div>
                          </div>
                        </div>
                      )}

                      {(work.status === 'failed' || work.status === 'fail') && work.errorMessage && (
                        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
                          <span className="font-medium">失败原因：</span>{work.errorMessage}
                        </div>
                      )}

                      {/* 操作按钮 */}
                      <div className="flex items-center justify-between">
                        <div className="flex items-center space-x-1">
                          {(work.status === 'completed' || work.status === 'suc') && work.videoUrl && (
                            <>
                              <button
                                onClick={() => handlePreview(work)}
                                className="p-2 text-gray-600 hover:text-teal-600 hover:bg-teal-50 rounded-lg transition-all duration-200 transform hover:scale-105"
                                title="预览"
                              >
                                <Eye className="w-4 h-4" />
                              </button>
                              <button
                                onClick={() => handleDownload(work)}
                                className="p-2 text-gray-600 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-all duration-200 transform hover:scale-105"
                                title="下载"
                              >
                                <Download className="w-4 h-4" />
                              </button>
                            </>
                          )}
                        </div>
                        
                        <button
                          onClick={() => handleDelete(work)}
                          disabled={deletingId === work.id}
                          className="p-2 text-gray-600 hover:text-red-600 hover:bg-red-50 rounded-lg transition-all duration-200 transform hover:scale-105 disabled:opacity-50 disabled:transform-none"
                          title="删除"
                        >
                          {deletingId === work.id ? (
                            <Loader2 className="w-4 h-4 animate-spin" />
                          ) : (
                            <Trash2 className="w-4 h-4" />
                          )}
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* 分页 */}
        {!loading && !error && totalPages > 1 && (
          <div className="border-t border-gray-200 px-6 py-4">
            <div className="flex items-center justify-between">
              <div className="text-sm text-gray-600">
                第 {currentPage} 页，共 {totalPages} 页
              </div>
              <div className="flex items-center space-x-2">
                <button
                  onClick={() => handlePageChange(currentPage - 1)}
                  disabled={currentPage <= 1}
                  className="px-3 py-1 text-sm border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  上一页
                </button>
                
                {/* 页码 */}
                {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                  const page = Math.max(1, Math.min(totalPages - 4, currentPage - 2)) + i;
                  if (page > totalPages) return null;
                  
                  return (
                    <button
                      key={page}
                      onClick={() => handlePageChange(page)}
                      className={`px-3 py-1 text-sm border rounded ${
                        page === currentPage
                          ? 'bg-teal-600 text-white border-teal-600'
                          : 'border-gray-300 hover:bg-gray-50'
                      }`}
                    >
                      {page}
                    </button>
                  );
                })}
                
                <button
                  onClick={() => handlePageChange(currentPage + 1)}
                  disabled={currentPage >= totalPages}
                  className="px-3 py-1 text-sm border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  下一页
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 视频预览模态框 */}
      {isVideoModalOpen && selectedWork && (
        <div className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-60">
          <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full mx-4">
            <div className="flex items-center justify-between p-4 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900">{selectedWork.name}</h3>
              <button
                onClick={() => setIsVideoModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 transition-colors"
              >
                <XCircle className="w-6 h-6" />
              </button>
            </div>
            <div className="p-4">
              {selectedWork.videoUrl ? (
                <video
                  src={selectedWork.videoUrl}
                  controls
                  className="w-full max-h-96 rounded-lg"
                  poster={selectedWork.coverUrl || selectedWork.thumbnailUrl}
                >
                  您的浏览器不支持视频播放。
                </video>
              ) : (
                <div className="aspect-video bg-gray-100 rounded-lg flex items-center justify-center">
                  <p className="text-gray-500">视频不可用</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default MyWorks;
