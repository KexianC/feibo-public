import React, { useState } from 'react';
import { X } from 'lucide-react';
import ModalPortal from './ModalPortal';

interface UserFeedbackModalProps {
  isOpen: boolean;
  onClose: () => void;
}

type ProblemType = '功能问题' | '体验感优化' | '其它';

const UserFeedbackModal: React.FC<UserFeedbackModalProps> = ({ isOpen, onClose }) => {
  const [selectedProblemType, setSelectedProblemType] = useState<ProblemType>('功能问题');
  const [problemDescription, setProblemDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async () => {
    if (!problemDescription.trim()) {
      alert('请输入问题描述');
      return;
    }

    setIsSubmitting(true);
    try {
      // 这里可以添加实际的提交逻辑
      console.log('提交反馈，类型与正文长度:', selectedProblemType, problemDescription.length);
      
      // 模拟提交延迟
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      alert('反馈提交成功！');
      handleClose();
    } catch (error) {
      console.error('提交失败:', error);
      alert('提交失败，请重试');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleClose = () => {
    setSelectedProblemType('功能问题');
    setProblemDescription('');
    onClose();
  };

  const handleFeedbackRecord = () => {
    // 这里可以添加查看反馈记录的逻辑
    console.log('查看反馈记录');
    alert('反馈记录功能开发中...');
  };

  return (
    <ModalPortal>
      <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-6">
      <div className="my-auto flex w-full max-w-md max-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-lg bg-white shadow-xl">
        {/* 标题栏 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">用户反馈</h2>
          <button
            onClick={handleClose}
            className="p-1 hover:bg-gray-100 rounded-full transition-colors"
          >
            <X size={20} className="text-gray-500" />
          </button>
        </div>

        {/* 内容区域 */}
        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4">
          {/* 问题属性 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              问题属性:
            </label>
            <div className="flex space-x-4">
              {(['功能问题', '体验感优化', '其它'] as ProblemType[]).map((type) => (
                <label key={type} className="flex items-center">
                  <input
                    type="radio"
                    name="problemType"
                    value={type}
                    checked={selectedProblemType === type}
                    onChange={(e) => setSelectedProblemType(e.target.value as ProblemType)}
                    className="w-4 h-4 text-teal-600 border-gray-300 focus:ring-teal-500"
                  />
                  <span className="ml-2 text-sm text-gray-700">{type}</span>
                </label>
              ))}
            </div>
          </div>

          {/* 问题描述 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              问题描述:
            </label>
            <textarea
              value={problemDescription}
              onChange={(e) => setProblemDescription(e.target.value)}
              placeholder="请输入您的问题描述"
              rows={6}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent text-sm resize-none"
            />
          </div>
        </div>

        {/* 按钮区域 */}
        <div className="flex items-center justify-between p-4 border-t border-gray-200">
          <button
            onClick={handleFeedbackRecord}
            className="px-4 py-2 bg-cyan-600 hover:bg-cyan-700 text-white text-sm font-medium rounded-md transition-colors"
          >
            反馈记录
          </button>
          <div className="flex space-x-2">
            <button
              onClick={handleClose}
              className="px-4 py-2 bg-gray-300 hover:bg-gray-400 text-gray-700 text-sm font-medium rounded-md transition-colors"
            >
              取消
            </button>
            <button
              onClick={handleSubmit}
              disabled={isSubmitting}
              className="px-4 py-2 bg-teal-600 hover:bg-teal-700 disabled:bg-teal-400 text-white text-sm font-medium rounded-md transition-colors"
            >
              {isSubmitting ? '提交中...' : '提交'}
            </button>
          </div>
        </div>
      </div>
      </div>
    </ModalPortal>
  );
};

export default UserFeedbackModal;
