import React, { useEffect } from 'react';
import { X } from 'lucide-react';

interface AspectRatioModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (aspectRatio: '16:9' | '9:16') => void;
}

const AspectRatioModal: React.FC<AspectRatioModalProps> = ({ isOpen, onClose, onSelect }) => {
  // ESC键关闭弹窗
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleKeyDown);
      // 防止背景滚动
      document.body.style.overflow = 'hidden';
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  const handleSelect = (aspectRatio: '16:9' | '9:16') => {
    onSelect(aspectRatio);
    onClose();
  };

  return (
    <div 
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
      onClick={handleBackdropClick}
    >
      <div className="bg-gray-800 rounded-lg p-6 w-96 max-w-[90vw] border border-gray-700">
        {/* 标题栏 */}
        <div className="flex items-center justify-between mb-6">
          <h3 className="text-white text-lg font-semibold">选择视频比例</h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-white transition-colors p-1"
          >
            <X size={20} />
          </button>
        </div>

        {/* 比例选项 */}
        <div className="space-y-4">
          {/* 16:9 横屏 */}
          <div 
            className="group cursor-pointer p-4 border-2 border-gray-600 hover:border-blue-500 rounded-lg transition-all hover:bg-gray-700/50"
            onClick={() => handleSelect('16:9')}
          >
            <div className="flex items-center space-x-4">
              {/* 比例预览 */}
              <div className="flex-shrink-0">
                <div className="w-16 h-9 bg-gradient-to-r from-blue-500 to-purple-500 rounded border-2 border-gray-500 group-hover:border-blue-400 transition-colors"></div>
              </div>
              
              {/* 信息 */}
              <div className="flex-1">
                <h4 className="text-white font-medium group-hover:text-blue-400 transition-colors">16:9 横屏</h4>
                <p className="text-gray-400 text-sm mt-1">适合电脑、电视等横屏设备观看</p>
                <p className="text-gray-500 text-xs mt-1">推荐用于教学、演示、直播等场景</p>
              </div>
            </div>
          </div>

          {/* 9:16 竖屏 */}
          <div 
            className="group cursor-pointer p-4 border-2 border-gray-600 hover:border-blue-500 rounded-lg transition-all hover:bg-gray-700/50"
            onClick={() => handleSelect('9:16')}
          >
            <div className="flex items-center space-x-4">
              {/* 比例预览 */}
              <div className="flex-shrink-0">
                <div className="w-9 h-16 bg-gradient-to-b from-green-500 to-teal-500 rounded border-2 border-gray-500 group-hover:border-blue-400 transition-colors"></div>
              </div>
              
              {/* 信息 */}
              <div className="flex-1">
                <h4 className="text-white font-medium group-hover:text-blue-400 transition-colors">9:16 竖屏</h4>
                <p className="text-gray-400 text-sm mt-1">适合手机等竖屏设备观看</p>
                <p className="text-gray-500 text-xs mt-1">推荐用于短视频、社交媒体等场景</p>
              </div>
            </div>
          </div>
        </div>

        {/* 底部提示 */}
        <div className="mt-6 pt-4 border-t border-gray-700">
          <p className="text-gray-500 text-xs text-center">
            选择后将创建对应比例的新页面，后续可在页面设置中修改
          </p>
        </div>
      </div>
    </div>
  );
};

export default AspectRatioModal;