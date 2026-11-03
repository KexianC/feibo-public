import React, { useState, useEffect } from 'react';
import { X, Eye, EyeOff, Key, Check, AlertCircle, Smartphone } from 'lucide-react';
import ModalPortal from './ModalPortal';
import { 
  sendPasswordResetSms, 
  changePasswordByOldPassword, 
  changePasswordBySms, 
  getClientFingerprint 
} from '../services/authApi';

interface ChangePasswordProps {
  isOpen: boolean;
  onClose: () => void;
  currentUser?: string;
}

const ChangePassword: React.FC<ChangePasswordProps> = ({ isOpen, onClose, currentUser }) => {
  // 修改方式：'password' | 'sms'
  const [changeMethod, setChangeMethod] = useState<'password' | 'sms'>('sms');
  
  const [formData, setFormData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
    phoneNumber: '',
    verificationCode: ''
  });
  
  const [showPasswords, setShowPasswords] = useState({
    current: false,
    new: false,
    confirm: false
  });
  
  const [errors, setErrors] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [isSendingSms, setIsSendingSms] = useState(false);
  const [smsCountdown, setSmsCountdown] = useState(0);

  // 验证码倒计时
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (smsCountdown > 0) {
      timer = setTimeout(() => {
        setSmsCountdown(smsCountdown - 1);
      }, 1000);
    }
    return () => clearTimeout(timer);
  }, [smsCountdown]);

  const validatePassword = (password: string): string[] => {
    const errors: string[] = [];
    
    if (password.length < 8) {
      errors.push('密码长度至少8位');
    }
    
    if (!/[A-Z]/.test(password)) {
      errors.push('密码必须包含至少一个大写字母');
    }
    
    if (!/[a-z]/.test(password)) {
      errors.push('密码必须包含至少一个小写字母');
    }
    
    if (!/\d/.test(password)) {
      errors.push('密码必须包含至少一个数字');
    }
    
    if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
      errors.push('密码必须包含至少一个特殊字符');
    }
    
    return errors;
  };

  const validatePhoneNumber = (phone: string): boolean => {
    const phoneRegex = /^1[3-9]\d{9}$/;
    return phoneRegex.test(phone);
  };

  const handleSendSms = async () => {
    if (!formData.phoneNumber) {
      setErrors(['请输入手机号']);
      return;
    }

    if (!validatePhoneNumber(formData.phoneNumber)) {
      setErrors(['请输入正确的手机号格式']);
      return;
    }

    setIsSendingSms(true);
    setErrors([]);

    try {
      // 调用发送修改密码验证码的API
      await sendPasswordResetSms({ 
        phoneNumber: formData.phoneNumber, 
        clientFingerprint: getClientFingerprint() 
      });
      
      setSmsCountdown(60);
      // 可以添加成功提示
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '发送验证码失败，请重试';
      setErrors([errorMessage]);
    } finally {
      setIsSendingSms(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    const validationErrors: string[] = [];
    
    if (changeMethod === 'password') {
      // 原密码修改方式的验证
      if (!formData.currentPassword) {
        validationErrors.push('请输入当前密码');
      }
      
      // 检查新密码是否与当前密码相同
      if (formData.currentPassword === formData.newPassword) {
        validationErrors.push('新密码不能与当前密码相同');
      }
    } else {
      // 手机验证码修改方式的验证
      if (!formData.phoneNumber) {
        validationErrors.push('请输入手机号');
      } else if (!validatePhoneNumber(formData.phoneNumber)) {
        validationErrors.push('请输入正确的手机号格式');
      }
      
      if (!formData.verificationCode) {
        validationErrors.push('请输入验证码');

      }
    }
    
    // 验证新密码（两种方式都需要）
    if (!formData.newPassword) {
      validationErrors.push('请输入新密码');
    } else {
      const passwordErrors = validatePassword(formData.newPassword);
      validationErrors.push(...passwordErrors);
    }
    
    // 验证确认密码（两种方式都需要）
    if (!formData.confirmPassword) {
      validationErrors.push('请确认新密码');
    } else if (formData.newPassword !== formData.confirmPassword) {
      validationErrors.push('两次输入的密码不一致');
    }
    
    if (validationErrors.length > 0) {
      setErrors(validationErrors);
      return;
    }
    
    setIsLoading(true);
    setErrors([]);
    
    try {
      if (changeMethod === 'password') {
        // 原密码修改方式
        await changePasswordByOldPassword({ 
          currentPassword: formData.currentPassword, 
          newPassword: formData.newPassword 
        });
      } else {
        // 手机验证码修改方式
        await changePasswordBySms({ 
          password: formData.newPassword, 
          code: formData.verificationCode 
        });
      }
      
      setSuccess(true);
      
      // 2秒后关闭模态框
      setTimeout(() => {
        handleClose();
      }, 2000);
      
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '修改密码失败，请重试';
      setErrors([errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleClose = () => {
    setFormData({ currentPassword: '', newPassword: '', confirmPassword: '', phoneNumber: '', verificationCode: '' });
    setErrors([]);
    setSuccess(false);
    setIsLoading(false);
    setChangeMethod('sms');
    setSmsCountdown(0);
    setIsSendingSms(false);
    onClose();
  };

  const togglePasswordVisibility = (field: 'current' | 'new' | 'confirm') => {
    setShowPasswords(prev => ({
      ...prev,
      [field]: !prev[field]
    }));
  };

  const getPasswordStrength = (password: string): { level: number; text: string; color: string } => {
    if (!password) return { level: 0, text: '', color: '' };
    
    const errors = validatePassword(password);
    const strength = 5 - errors.length;
    
    if (strength <= 1) return { level: 1, text: '弱', color: 'text-red-600' };
    if (strength <= 3) return { level: 2, text: '中等', color: 'text-yellow-600' };
    return { level: 3, text: '强', color: 'text-green-600' };
  };

  if (!isOpen) return null;

  const passwordStrength = getPasswordStrength(formData.newPassword);

  return (
    <ModalPortal>
      <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-6">
      <div className="my-auto flex w-full max-w-md max-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-lg bg-white shadow-xl">
        {/* 标题栏 */}
        <div className="flex flex-shrink-0 items-center justify-between bg-medical-green-600 px-6 py-4 text-white">
          <h2 className="text-xl font-semibold flex items-center gap-2">
            <Key size={24} />
            修改密码
          </h2>
          <button
            onClick={handleClose}
            className="text-white hover:text-gray-200 transition-colors"
          >
            <X size={24} />
          </button>
        </div>

        {/* 内容区域 */}
        <div className="min-h-0 flex-1 overflow-y-auto p-6">
          {success ? (
            <div className="text-center py-8">
              <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <Check size={32} className="text-green-600" />
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">密码修改成功</h3>
              <p className="text-gray-600">您的密码已成功更新</p>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              {/* 当前用户信息 */}
              {currentUser && (
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-3 mb-4">
                  <p className="text-sm text-blue-700">
                    <strong>当前用户：</strong>{currentUser}
                  </p>
                </div>
              )}



              {/* 当前密码 - 仅在原密码修改方式下显示 */}
              {changeMethod === 'password' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    当前密码 <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <input
                      type={showPasswords.current ? 'text' : 'password'}
                      value={formData.currentPassword}
                      onChange={(e) => setFormData({ ...formData, currentPassword: e.target.value })}
                      className="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-medical-green-500 focus:border-medical-green-500"
                      placeholder="请输入当前密码"
                      required
                    />
                    <button
                      type="button"
                      onClick={() => togglePasswordVisibility('current')}
                      className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
                    >
                      {showPasswords.current ? <EyeOff size={20} /> : <Eye size={20} />}
                    </button>
                  </div>
                </div>
              )}

              {/* 手机号和验证码 - 仅在手机验证码修改方式下显示 */}
              {changeMethod === 'sms' && (
                <>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      手机号 <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="tel"
                      value={formData.phoneNumber}
                      onChange={(e) => setFormData({ ...formData, phoneNumber: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-medical-green-500 focus:border-medical-green-500"
                      placeholder="请输入手机号"
                      maxLength={11}
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      验证码 <span className="text-red-500">*</span>
                    </label>
                    <div className="flex gap-2">
                      <input
                        type="text"
                        value={formData.verificationCode}
                        onChange={(e) => setFormData({ ...formData, verificationCode: e.target.value.replace(/\D/g, '') })}
                        className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-medical-green-500 focus:border-medical-green-500"
                        placeholder="请输入6位验证码"

                        required
                      />
                      <button
                        type="button"
                        onClick={handleSendSms}
                        disabled={isSendingSms || smsCountdown > 0 || !formData.phoneNumber}
                        className="px-4 py-2 bg-medical-green-600 text-white rounded-lg hover:bg-medical-green-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors whitespace-nowrap"
                      >
                        {isSendingSms ? (
                          <div className="flex items-center">
                            <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-1"></div>
                            发送中
                          </div>
                        ) : smsCountdown > 0 ? (
                          `${smsCountdown}s后重发`
                        ) : (
                          '获取验证码'
                        )}
                      </button>
                    </div>
                  </div>
                </>
              )}

              {/* 新密码 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  新密码 <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    type={showPasswords.new ? 'text' : 'password'}
                    value={formData.newPassword}
                    onChange={(e) => setFormData({ ...formData, newPassword: e.target.value })}
                    className="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-medical-green-500 focus:border-medical-green-500"
                    placeholder="请输入新密码"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => togglePasswordVisibility('new')}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
                  >
                    {showPasswords.new ? <EyeOff size={20} /> : <Eye size={20} />}
                  </button>
                </div>
                
                {/* 密码强度指示器 */}
                {formData.newPassword && (
                  <div className="mt-2">
                    <div className="flex items-center gap-2 mb-1">
                      <div className="flex-1 bg-gray-200 rounded-full h-2">
                        <div 
                          className={`h-2 rounded-full transition-all ${
                            passwordStrength.level === 1 ? 'bg-red-500 w-1/3' :
                            passwordStrength.level === 2 ? 'bg-yellow-500 w-2/3' :
                            'bg-green-500 w-full'
                          }`}
                        />
                      </div>
                      <span className={`text-xs font-medium ${passwordStrength.color}`}>
                        {passwordStrength.text}
                      </span>
                    </div>
                  </div>
                )}
              </div>

              {/* 确认新密码 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  确认新密码 <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    type={showPasswords.confirm ? 'text' : 'password'}
                    value={formData.confirmPassword}
                    onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })}
                    className="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-medical-green-500 focus:border-medical-green-500"
                    placeholder="请再次输入新密码"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => togglePasswordVisibility('confirm')}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
                  >
                    {showPasswords.confirm ? <EyeOff size={20} /> : <Eye size={20} />}
                  </button>
                </div>
              </div>

              {/* 密码要求提示 */}
              <div className="bg-gray-50 border border-gray-200 rounded-lg p-3">
                <p className="text-sm font-medium text-gray-700 mb-2">密码要求：</p>
                <ul className="text-xs text-gray-600 space-y-1">
                  <li>• 至少8个字符</li>
                  <li>• 包含大写字母、小写字母、数字和特殊字符</li>
                  {changeMethod === 'password' && (
                    <li>• 不能与当前密码相同</li>
                  )}
                </ul>
              </div>

              {/* 错误信息 */}
              {errors.length > 0 && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-3">
                  <div className="flex items-start gap-2">
                    <AlertCircle size={16} className="text-red-600 mt-0.5 flex-shrink-0" />
                    <div>
                      {errors.map((error, index) => (
                        <p key={index} className="text-sm text-red-600">{error}</p>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* 切换修改方式按钮 */}
              <div className="text-center pt-2 pb-2">
                {changeMethod === 'sms' ? (
                  <button
                    type="button"
                    onClick={() => setChangeMethod('password')}
                    className="text-sm text-gray-600 hover:text-medical-green-600 transition-colors underline"
                  >
                    当前手机号无法正常接收验证码
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => setChangeMethod('sms')}
                    className="text-sm text-gray-600 hover:text-medical-green-600 transition-colors underline"
                  >
                    返回手机验证码修改
                  </button>
                )}
              </div>

              {/* 按钮组 */}
              <div className="flex gap-3 pt-4">
                <button
                  type="button"
                  onClick={handleClose}
                  className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
                  disabled={isLoading}
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={isLoading}
                  className="flex-1 px-4 py-2 bg-medical-green-600 text-white rounded-lg hover:bg-medical-green-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  {isLoading ? (
                    <div className="flex items-center justify-center">
                      <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                      修改中...
                    </div>
                  ) : (
                    '确认修改'
                  )}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
      </div>
    </ModalPortal>
  );
};

export default ChangePassword;
