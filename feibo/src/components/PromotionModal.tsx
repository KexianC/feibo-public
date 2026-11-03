import React, { useState, useEffect } from 'react';
import { X, Gift, Clock, CheckCircle } from 'lucide-react';
import { ActivityStatusResponse, formatActivityRemainingTime } from '../services/userApi';

interface PromotionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onClaim: () => void;
  activityStatus?: ActivityStatusResponse | null;
  isLoading?: boolean;
}

const PromotionModal: React.FC<PromotionModalProps> = ({ 
  isOpen, 
  onClose, 
  onClaim, 
  activityStatus, 
  isLoading = false 
}) => {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setIsVisible(true);
    } else {
      const timer = setTimeout(() => setIsVisible(false), 300);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

  if (!isVisible) return null;

  const handleClaim = () => {
    onClaim();
  };

  // 根据活动状态决定显示内容
  const getModalContent = () => {
    if (!activityStatus) {
      // 没有活动状态数据时，显示默认内容
      return {
        showClaimButton: true,
        buttonText: '立即领取 7天免费会员',
        statusText: '限时优惠，所有用户可享',
        icon: <Gift className="w-8 h-8 text-white" />,
        iconBg: 'bg-gradient-to-r from-teal-500 to-blue-500'
      };
    }

    if (!activityStatus.isReceived) {
      // 未领取状态
      return {
        showClaimButton: true,
        buttonText: '立即领取 7天免费会员',
        statusText: '限时优惠，所有用户可享',
        icon: <Gift className="w-8 h-8 text-white" />,
        iconBg: 'bg-gradient-to-r from-teal-500 to-blue-500'
      };
    } else {
      // 已领取状态
      const remainingTime = formatActivityRemainingTime(activityStatus.expireTime);
      return {
        showClaimButton: false,
        buttonText: '',
        statusText: `会员权益 ${remainingTime}`,
        icon: <CheckCircle className="w-8 h-8 text-white" />,
        iconBg: 'bg-gradient-to-r from-green-500 to-emerald-500'
      };
    }
  };

  const modalContent = getModalContent();

  return (
    <div className={`fixed inset-0 z-50 flex items-center justify-center transition-all duration-300 ${
      isOpen ? 'opacity-100' : 'opacity-0'
    }`}>
      {/* 背景遮罩 */}
      <div 
        className="absolute inset-0 bg-black bg-opacity-50 transition-opacity duration-300"
        onClick={onClose}
      />
      
      {/* 弹窗内容 */}
      <div className={`relative bg-white rounded-2xl shadow-2xl max-w-md w-full mx-4 transform transition-all duration-300 ${
        isOpen ? 'scale-100 translate-y-0' : 'scale-95 translate-y-4'
      }`}>
        {/* 关闭按钮 */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 text-gray-400 hover:text-gray-600 transition-colors rounded-full hover:bg-gray-100"
        >
          <X size={20} />
        </button>

        {/* 弹窗头部 */}
        <div className="text-center pt-8 pb-4 px-6">
          <div className={`inline-flex items-center justify-center w-16 h-16 ${modalContent.iconBg} rounded-full mb-4`}>
            {modalContent.icon}
          </div>
          <h2 className="text-xl font-bold text-gray-800 mb-2">
            飞驳数字医生工作台2.0全新更新
          </h2>
        </div>

        {/* 弹窗内容 */}
        <div className="px-6 pb-6">
          <div className="text-center mb-6">
            <p className="text-lg text-gray-600 mb-2">
              所有用户免费赠送
            </p>
            <div className="text-3xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-teal-500 to-blue-500">
              7天会员时长
            </div>
          </div>

          {/* 特性列表 */}
          <div className="bg-gray-50 rounded-lg p-4 mb-6">
            <ul className="space-y-2 text-sm text-gray-600">
              <li className="flex items-center">
                <div className="w-2 h-2 bg-teal-500 rounded-full mr-3"></div>
                全新界面设计，操作更便捷
              </li>
              <li className="flex items-center">
                <div className="w-2 h-2 bg-teal-500 rounded-full mr-3"></div>
                AI智能辅助，效率大幅提升
              </li>
              <li className="flex items-center">
                <div className="w-2 h-2 bg-teal-500 rounded-full mr-3"></div>
                更多专业功能，满足各种需求
              </li>
            </ul>
          </div>

          {/* 领取按钮或状态显示 */}
          {modalContent.showClaimButton ? (
            <button
              onClick={handleClaim}
              disabled={isLoading}
              className="w-full bg-gradient-to-r from-teal-500 to-blue-500 hover:from-teal-600 hover:to-blue-600 disabled:from-gray-400 disabled:to-gray-500 text-white font-semibold py-3 px-6 rounded-lg transition-all duration-200 transform hover:scale-105 disabled:hover:scale-100 shadow-lg hover:shadow-xl disabled:cursor-not-allowed"
            >
              {isLoading ? (
                <div className="flex items-center justify-center">
                  <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white mr-2"></div>
                  领取中...
                </div>
              ) : (
                modalContent.buttonText
              )}
            </button>
          ) : (
            <div className="w-full bg-gradient-to-r from-green-500 to-emerald-500 text-white font-semibold py-3 px-6 rounded-lg text-center">
              <div className="flex items-center justify-center">
                <Clock className="w-5 h-5 mr-2" />
                会员权益已激活
              </div>
            </div>
          )}

          {/* 底部提示 */}
          <p className="text-xs text-gray-400 text-center mt-3">
            {modalContent.statusText}
          </p>
        </div>
      </div>
    </div>
  );
};

export default PromotionModal;