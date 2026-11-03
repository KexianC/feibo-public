import React from 'react';
import { Save, X } from 'lucide-react';
import ModalPortal from './ModalPortal';

interface SaveDraftModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  inputValue: string;
  onInputChange: (value: string) => void;
  inputPlaceholder?: string;
  confirmText?: string;
  cancelText?: string;
}

const SaveDraftModal: React.FC<SaveDraftModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  inputValue,
  onInputChange,
  inputPlaceholder = '请输入名称',
  confirmText = '确认保存',
  cancelText = '取消'
}) => {
  if (!isOpen) return null;

  const handleConfirm = () => {
    if (inputValue.trim()) {
      onConfirm();
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && inputValue.trim()) {
      handleConfirm();
    }
  };

  return (
    <ModalPortal>
      <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-6">
      <div className="my-auto flex w-full max-w-md max-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-lg bg-white shadow-xl">
        {/* 标题栏 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <div className="flex items-center space-x-2">
            <Save className="text-teal-500" size={20} />
            <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X size={20} />
          </button>
        </div>
        
        {/* 内容区 */}
        <div className="min-h-0 overflow-y-auto p-4">
          <p className="text-gray-700 mb-4">请为您的设计草稿输入一个名称：</p>
          <input
            type="text"
            value={inputValue}
            onChange={(e) => onInputChange(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder={inputPlaceholder}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
            autoFocus
          />
        </div>
        
        {/* 按钮区 */}
        <div className="flex items-center justify-end space-x-3 p-4 border-t border-gray-200">
          <button
            onClick={onClose}
            className="px-4 py-2 text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors"
          >
            {cancelText}
          </button>
          <button
            onClick={handleConfirm}
            disabled={!inputValue.trim()}
            className="px-4 py-2 bg-teal-600 hover:bg-teal-700 disabled:bg-gray-300 disabled:cursor-not-allowed text-white rounded-lg transition-colors"
          >
            {confirmText}
          </button>
        </div>
      </div>
      </div>
    </ModalPortal>
  );
};

export default SaveDraftModal;
