import React, { useState, useRef, useEffect } from 'react';
import { X, Camera, Check, AlertCircle } from 'lucide-react';
import ModalPortal from './ModalPortal';
import CachedAvatar from './CachedAvatar';
import { getUserInfo, uploadAvatar, updateUsername, UserInfo, getCurrentUser, saveUserInfo } from '../services/authApi';
import { sanitizeAvatarUrl } from '../utils/avatar';

interface PersonalInfoProps {
  isOpen: boolean;
  onClose: () => void;
  currentUser?: string;
  onUserInfoUpdate?: (patch: { nickName?: string; avatar?: string }) => void;
}

interface PersonalUserInfo {
  username: string;
  phoneNumber: string;
  avatar: string;
}

const PersonalInfo: React.FC<PersonalInfoProps> = ({ isOpen, onClose, currentUser, onUserInfoUpdate }) => {
  const [hasOpened, setHasOpened] = useState(isOpen);
  const [userInfo, setUserInfo] = useState<PersonalUserInfo>(() => {
    const localUser = getCurrentUser();
    return {
      username: localUser?.nickName || '',
      phoneNumber: localUser?.phoneNumber || '',
      avatar: sanitizeAvatarUrl(localUser?.avatar)
    };
  });
  const [isEditing, setIsEditing] = useState({
    username: false
  });
  const [tempValues, setTempValues] = useState({
    username: ''
  });

  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [localAvatarUrl, setLocalAvatarUrl] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);



  // 获取用户信息
  useEffect(() => {
    if (isOpen) {
      setHasOpened(true);
      fetchUserInfo();
    }
  }, [isOpen]);



  /**
   * 获取并设置用户信息；优先本地，其次接口；头像URL会被清理
   */
  const fetchUserInfo = async () => {
    try {
      setLoading(true);
      
      // 首先尝试从本地存储获取用户信息（authApi.getCurrentUser已做清理）
      const localUser = getCurrentUser();
      
      let userData;
      if (localUser) {
        // 使用本地存储的用户信息，并保留头像
        userData = {
          username: localUser.nickName || '',
          nickName: localUser.nickName || '',
          phoneNumber: localUser.phoneNumber || '',
          avatar: sanitizeAvatarUrl(localUser.avatar)
        };
      } else {
        // 如果本地没有，则调用API获取，并清理头像URL
        const apiData = await getUserInfo();
        userData = {
          username: apiData.username || apiData.nickName || '',
          nickName: apiData.nickName || '',
          phoneNumber: apiData.phoneNumber || '',
          avatar: sanitizeAvatarUrl(apiData.avatar)
        };
      }
      
      // 设置用户信息状态，使用清理后的avatar
      const nextUserInfo = {
        username: userData.username || userData.nickName || '',
        phoneNumber: userData.phoneNumber || '',
        avatar: userData.avatar
      };
      
      setUserInfo(prev => (
        prev.username === nextUserInfo.username &&
        prev.phoneNumber === nextUserInfo.phoneNumber &&
        prev.avatar === nextUserInfo.avatar
          ? prev
          : nextUserInfo
      ));
      setTempValues(prev => (
        prev.username === nextUserInfo.username ? prev : { username: nextUserInfo.username }
      ));
      
    } catch (error: any) {
      console.error('获取用户信息失败:', error);
      setMessage({ type: 'error', text: error.message || '获取用户信息失败' });
    } finally {
      setLoading(false);
    }
  };

  // 处理头像上传
  const handleAvatarChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      // 检查文件类型
      if (!file.type.startsWith('image/')) {
        setMessage({ type: 'error', text: '请选择图片文件' });
        return;
      }
      
      // 检查文件大小 (5MB)
      if (file.size > 5 * 1024 * 1024) {
        setMessage({ type: 'error', text: '图片大小不能超过5MB' });
        return;
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        const result = e.target?.result as string;
        setAvatarPreview(result);
      };
      reader.readAsDataURL(file);
    }
  };

  // 上传头像
  /**
   * 上传头像并使用接口返回的数据更新状态与本地存储
   * - 解析返回的 data（包含 nickName、avatar 等）
   * - 清理返回的 avatar，去除反引号/引号
   * - 更新 localStorage 中的用户信息
   */
  const handleAvatarUpload = async () => {
    if (!avatarPreview || !fileInputRef.current?.files?.[0]) return;

    try {
      setLoading(true);
      const file = fileInputRef.current.files[0];
      const serverData = await uploadAvatar(file);

      // 服务端返回的头像URL可能包含反引号，这里进行清理
      const cleanedAvatar = sanitizeAvatarUrl(serverData?.avatar);

      // 优先显示服务端返回的已清理头像URL
      setUserInfo(prev => ({ ...prev, avatar: cleanedAvatar }));
      setAvatarPreview(null);
      setMessage({ type: 'success', text: '头像更新成功' });

      // 更新 localStorage 中的用户信息
      const currentUser = getCurrentUser();
      if (currentUser) {
        const updatedUser = {
          ...currentUser,
          nickName: serverData?.nickName ?? currentUser.nickName,
          avatar: cleanedAvatar
        };
        saveUserInfo(updatedUser);
      }

      // 通知父组件用户信息已更新
      if (onUserInfoUpdate) {
        onUserInfoUpdate({
          nickName: serverData?.nickName ?? currentUser?.nickName,
          avatar: cleanedAvatar,
        });
      }
    } catch (error: any) {
      setMessage({ type: 'error', text: error.message || '头像上传失败' });
    } finally {
      setLoading(false);
    }
  };

  // 开始编辑
  const startEdit = (field: 'username') => {
    setIsEditing(prev => ({ ...prev, [field]: true }));
  };

  // 取消编辑
  const cancelEdit = (field: 'username') => {
    setIsEditing(prev => ({ ...prev, [field]: false }));
    setTempValues(prev => ({ ...prev, [field]: userInfo[field] }));
  };

  // 保存用户名
  /**
   * 保存用户名并使用接口返回的数据更新状态与本地存储
   * - 解析返回的 data（包含 nickName、avatar 等）
   * - 清理返回的 avatar，去除反引号/引号
   * - 更新 localStorage 中的用户信息
   */
  const saveUsername = async () => {
    if (!tempValues.username.trim()) {
      setMessage({ type: 'error', text: '用户名不能为空' });
      return;
    }

    try {
      setLoading(true);
      const serverData = await updateUsername({ username: tempValues.username });
      const nextName = serverData?.nickName || tempValues.username;
      const cleanedAvatar = sanitizeAvatarUrl(serverData?.avatar);

      setUserInfo(prev => ({ ...prev, username: nextName, avatar: cleanedAvatar || prev.avatar }));
      setIsEditing(prev => ({ ...prev, username: false }));
      setMessage({ type: 'success', text: '用户名更新成功' });

      // 更新 localStorage 中的用户信息
      const currentUser = getCurrentUser();
      if (currentUser) {
        const updatedUser = {
          ...currentUser,
          nickName: nextName,
          avatar: cleanedAvatar || currentUser.avatar
        };
        saveUserInfo(updatedUser);
      }

      // 通知父组件用户信息已更新
      if (onUserInfoUpdate) {
        onUserInfoUpdate({
          nickName: nextName,
          avatar: cleanedAvatar || currentUser?.avatar,
        });
      }
    } catch (error: any) {
      setMessage({ type: 'error', text: error.message || '用户名更新失败' });
    } finally {
      setLoading(false);
    }
  };



  // 清除消息
  useEffect(() => {
    if (message.text) {
      const timer = setTimeout(() => {
        setMessage({ type: '', text: '' });
      }, 3000);
      return () => clearTimeout(timer);
    }
  }, [message]);

  // 组件卸载时清理本地URL，防止内存泄漏
  useEffect(() => {
    return () => {
      if (localAvatarUrl) {
        URL.revokeObjectURL(localAvatarUrl);
      }
    };
  }, [localAvatarUrl]);

  if (!hasOpened) return null;

  const avatarSrc = avatarPreview || userInfo.avatar;

  return (
    <ModalPortal>
      <div
        className={`${isOpen ? 'fixed' : 'hidden'} inset-0 z-50 items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-6 ${isOpen ? 'flex' : ''}`}
        aria-hidden={!isOpen}
      >
      <div className="my-auto flex w-full max-w-md max-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-lg bg-white shadow-xl">
        {/* 头部 */}
        <div className="flex flex-shrink-0 items-center justify-between border-b border-gray-200 p-6">
          <h2 className="text-xl font-semibold text-gray-900">个人信息</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X size={24} />
          </button>
        </div>

        {/* 内容区域 */}
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto p-6">
          {/* 消息提示 */}
          {message.text && (
            <div className={`p-3 rounded-md flex items-center space-x-2 ${
              message.type === 'success' 
                ? 'bg-green-50 text-green-700 border border-green-200' 
                : 'bg-red-50 text-red-700 border border-red-200'
            }`}>
              {message.type === 'success' ? <Check size={16} /> : <AlertCircle size={16} />}
              <span className="text-sm">{message.text}</span>
            </div>
          )}

          {/* 头像区域 */}
          <div className="text-center">
            <div className="relative inline-block">
              <div className="w-24 h-24 rounded-full bg-gray-200 flex items-center justify-center overflow-hidden">
                <CachedAvatar
                  src={avatarSrc}
                  alt="头像"
                  className="h-full w-full object-cover"
                  fallbackClassName="flex h-full w-full items-center justify-center bg-gray-200"
                  iconClassName="h-8 w-8 text-gray-400"
                />
              </div>
              <button
                onClick={() => fileInputRef.current?.click()}
                className="absolute bottom-0 right-0 bg-blue-500 text-white rounded-full p-2 hover:bg-blue-600 transition-colors"
              >
                <Camera size={16} />
              </button>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handleAvatarChange}
              className="hidden"
            />
            {avatarPreview && (
              <div className="mt-3 space-x-2">
                <button
                  onClick={handleAvatarUpload}
                  disabled={loading}
                  className="px-4 py-2 bg-blue-500 text-white rounded-md hover:bg-blue-600 disabled:opacity-50 transition-colors"
                >
                  {loading ? '上传中...' : '确认上传'}
                </button>
                <button
                  onClick={() => setAvatarPreview(null)}
                  className="px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 transition-colors"
                >
                  取消
                </button>
              </div>
            )}
          </div>

          {/* 用户名 */}
          <div className="space-y-2">
            <label className="block text-sm font-medium text-gray-700">用户名</label>
            {isEditing.username ? (
              <div className="space-y-2">
                <input
                  type="text"
                  value={tempValues.username}
                  onChange={(e) => setTempValues(prev => ({ ...prev, username: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="请输入用户名"
                />
                <div className="flex space-x-2">
                  <button
                    onClick={saveUsername}
                    disabled={loading}
                    className="px-4 py-2 bg-blue-500 text-white rounded-md hover:bg-blue-600 disabled:opacity-50 transition-colors"
                  >保存</button>
                  <button
                    onClick={() => cancelEdit('username')}
                    className="px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 transition-colors"
                  >
                    取消
                  </button>
                </div>
              </div>
            ) : (
              <div className="flex items-center justify-between p-3 bg-gray-50 rounded-md">
                <span className="text-gray-900">{userInfo.username || '未设置'}</span>
                <button
                  onClick={() => startEdit('username')}
                  className="text-blue-500 hover:text-blue-600 transition-colors"
                >
                  编辑
                </button>
              </div>
            )}
          </div>

          {/* 手机号 */}
          <div className="space-y-2">
            <label className="block text-sm font-medium text-gray-700">手机号</label>
            <div className="p-3 bg-gray-50 rounded-md">
              <span className="text-gray-900">
                {userInfo.phoneNumber ? 
                  userInfo.phoneNumber.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : 
                  '未设置'
                }
              </span>
            </div>
          </div>
        </div>
      </div>
      </div>
    </ModalPortal>
  );
};

export default PersonalInfo;
