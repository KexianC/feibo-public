import React, { useState, useEffect } from 'react';
import { Download, CheckCircle, XCircle, Clock4, Loader2, Image as ImageIcon, Play, X } from 'lucide-react';

export interface VideoComposeProgressProps {
  isVisible: boolean;
  progress: number;
  status: 'pending' | 'processing' | 'completed' | 'suc' | 'failed' | 'fail' | 'ing';
  taskId?: string;
  videoUrl?: string;
  coverUrl?: string;
  errorMessage?: string;
  onDownload?: (videoUrl: string) => void;
  onClose?: () => void;
}

const VideoComposeProgress: React.FC<VideoComposeProgressProps> = ({
  isVisible,
  progress,
  status,
  taskId,
  videoUrl,
  coverUrl,
  errorMessage,
  onDownload,
  onClose
}) => {
  const [imageLoading, setImageLoading] = useState(true);
  const [imageError, setImageError] = useState(false);
  const [autoProgress, setAutoProgress] = useState(0);
  const [isVideoModalOpen, setIsVideoModalOpen] = useState(false);

  // 切换到新任务时彻底清理弹窗内部状态，避免上一条任务的视频预览、
  // 封面加载状态或模拟进度残留到下一条任务。
  useEffect(() => {
    setAutoProgress(0);
    setImageLoading(true);
    setImageError(false);
    setIsVideoModalOpen(false);
  }, [taskId]);

  /**
   * 处理播放视频功能
   * 打开视频播放弹窗
   */
  const handlePlayVideo = () => {
    setIsVideoModalOpen(true);
  };

  // 当coverUrl变化时重置图片状态
  useEffect(() => {
    if (coverUrl) {
      setImageLoading(true);
      setImageError(false);
    }
  }, [coverUrl]);

  // 自动进度条逻辑
  useEffect(() => {
    let interval: NodeJS.Timeout;
    
    if (status === 'ing' || status === 'processing') {
      // 如果API没有返回progress或progress为0，使用自动增长的进度
      if (progress === 0 || !progress) {
        setAutoProgress(0);
        interval = setInterval(() => {
          setAutoProgress(prev => {
            // 进度条自动增长到99%，增长速度逐渐减慢
            if (prev < 50) {
              return prev + 2; // 前50%较快
            } else if (prev < 80) {
              return prev + 1; // 50-80%中等速度
            } else if (prev < 99) {
              return prev + 0.5; // 80-99%较慢
            }
            return 99; // 最大99%
          });
        }, 1000); // 每秒更新一次
      } else {
        // 如果API返回了有效的progress，重置自动进度
        setAutoProgress(0);
      }
    } else {
      // 非处理状态时重置自动进度
      setAutoProgress(0);
    }

    return () => {
      if (interval) {
        clearInterval(interval);
      }
    };
  }, [status, progress]);

  if (!isVisible) return null;

  const handleImageLoad = () => {
    setImageLoading(false);
    setImageError(false);
  };

  const handleImageError = () => {
    setImageLoading(false);
    setImageError(true);
  };

  const getStatusIcon = () => {
    switch (status) {
      case 'pending':
        return <Clock4 className="w-5 h-5 text-yellow-500" />;
      case 'processing':
      case 'ing':
        return <Loader2 className="w-5 h-5 text-blue-500 animate-spin" />;
      case 'completed':
      case 'suc':
        return <CheckCircle className="w-5 h-5 text-green-500" />;
      case 'failed':
      case 'fail':
        return <XCircle className="w-5 h-5 text-red-500" />;
      default:
        return <Clock4 className="w-5 h-5 text-gray-500" />;
    }
  };

  const getStatusText = () => {
    const currentProgress = progress > 0 ? progress : autoProgress;
    switch (status) {
      case 'pending':
        return '等待处理中...';
      case 'processing':
      case 'ing':
        return `合成中... ${Math.round(currentProgress)}%`;
      case 'completed':
      case 'suc':
        return '合成完成！';
      case 'failed':
      case 'fail':
        return '合成失败';
      default:
        return '未知状态';
    }
  };

  const getProgressBarColor = () => {
    switch (status) {
      case 'processing':
      case 'ing':
        return 'bg-blue-500';
      case 'completed':
      case 'suc':
        return 'bg-green-500';
      case 'failed':
      case 'fail':
        return 'bg-red-500';
      default:
        return 'bg-gray-500';
    }
  };

  // 获取当前显示的进度值
  const getCurrentProgress = () => {
    if (status === 'completed' || status === 'suc') {
      return 100;
    }
    return progress > 0 ? progress : autoProgress;
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl p-6 w-96 max-w-md mx-4">
        {/* 标题栏 */}
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-gray-900">视频合成进度</h3>
          {onClose && (
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-600 transition-colors"
              disabled={status === 'processing'}
            >
              <XCircle className="w-5 h-5" />
            </button>
          )}
        </div>

        {/* 状态图标和文本 */}
        <div className="flex items-center mb-4">
          {getStatusIcon()}
          <span className="ml-2 text-gray-700">{getStatusText()}</span>
        </div>

        {/* 进度条 */}
        <div className="mb-4">
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className={`h-2 rounded-full transition-all duration-300 ${getProgressBarColor()}`}
              style={{ width: `${Math.max(0, Math.min(100, getCurrentProgress()))}%` }}
            />
          </div>
          <div className="flex justify-between text-sm text-gray-500 mt-1">
            <span>0%</span>
            <span>{Math.round(getCurrentProgress())}%</span>
            <span>100%</span>
          </div>
        </div>

        {/* 任务ID */}
        {taskId && (
          <div className="mb-4 p-2 bg-gray-50 rounded text-sm">
            <span className="text-gray-600">任务ID: </span>
            <span className="font-mono text-gray-800">{taskId}</span>
          </div>
        )}

        {/* 错误信息 */}
        {(status === 'failed' || status === 'fail') && errorMessage && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded">
            <p className="text-red-700 text-sm">{errorMessage}</p>
          </div>
        )}

        {/* 封面图片 - 仅在成功状态下显示 */}
        {(status === 'completed' || status === 'suc') && coverUrl && (
          <div className="mb-4">
            <h4 className="text-sm font-medium text-gray-700 mb-2">视频封面</h4>
            <div className="relative w-full h-32 bg-gray-100 rounded-lg overflow-hidden">
              {imageLoading && (
                <div className="absolute inset-0 flex items-center justify-center">
                  <Loader2 className="w-6 h-6 text-gray-400 animate-spin" />
                </div>
              )}
              {imageError ? (
                <div className="absolute inset-0 flex flex-col items-center justify-center text-gray-400">
                  <ImageIcon className="w-8 h-8 mb-1" />
                  <span className="text-xs">封面加载失败</span>
                </div>
              ) : (
                <img
                  src={coverUrl}
                  alt="视频封面"
                  className="w-full h-full object-cover"
                  onLoad={handleImageLoad}
                  onError={handleImageError}
                  style={{ display: imageLoading ? 'none' : 'block' }}
                />
              )}
            </div>
          </div>
        )}

        {/* 操作按钮 */}
        <div className="flex justify-between space-x-3">
          {/* 左侧播放按钮 */}
          {(status === 'completed' || status === 'suc') && videoUrl && (
            <button
              onClick={handlePlayVideo}
              className="flex items-center px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
            >
              <Play className="w-4 h-4 mr-2" />
              播放视频
            </button>
          )}
          
          {/* 右侧按钮组 */}
          <div className="flex space-x-3">
            {(status === 'completed' || status === 'suc') && videoUrl && onDownload && (
              <button
                onClick={() => onDownload(videoUrl)}
                className="flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Download className="w-4 h-4 mr-2" />
                下载视频
              </button>
            )}
            
            {status !== 'processing' && onClose && (
              <button
                onClick={onClose}
                className="px-4 py-2 bg-gray-300 text-gray-700 rounded-lg hover:bg-gray-400 transition-colors"
              >
                关闭
              </button>
            )}
          </div>
        </div>

        {/* 提示信息 */}
        {(status === 'processing' || status === 'ing') && (
          <div className="mt-4 p-2 bg-blue-50 border border-blue-200 rounded">
            <p className="text-blue-700 text-sm text-center">
              视频正在合成中，请耐心等待...
            </p>
          </div>
        )}
      </div>
      
      {/* 视频播放弹窗 */}
      {isVideoModalOpen && videoUrl && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
          <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl overflow-hidden flex flex-col max-h-[90vh]">
            {/* 弹窗头部 */}
            <div className="flex justify-between items-center p-4 border-b border-gray-200">
              <h3 className="text-lg font-bold text-gray-800">视频预览</h3>
              <button
                onClick={() => setIsVideoModalOpen(false)}
                className="text-gray-500 hover:text-gray-700 hover:bg-gray-100 p-2 rounded-full transition-colors"
              >
                <X size={20} />
              </button>
            </div>
            
            {/* 视频播放区域 */}
            <div className="p-4 bg-gray-50 flex-1 overflow-auto flex items-center justify-center">
              <video 
                src={videoUrl} 
                controls 
                autoPlay
                className="w-full max-h-[70vh] rounded-lg shadow-sm"
                poster={coverUrl}
              >
                您的浏览器不支持视频播放。
              </video>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default VideoComposeProgress;
