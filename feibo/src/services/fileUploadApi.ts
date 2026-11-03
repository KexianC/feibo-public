// 文件上传API服务
// 统一的文件上传接口实现

import { API_CONFIG, UPLOAD_CONFIG, UPLOAD_TYPE } from '../config/api';
import { authenticatedRequest } from './authApi';

// 文件上传响应接口
export interface FileUploadResponse {
  code: string;
  info: string;
  success: boolean;
  data: {
    fileId: string;
    fileUrl: string;
  };
}

// 获取当前用户ID
const getCurrentUserId = (): string => {
  const userInfo = localStorage.getItem('user-info');
  if (userInfo) {
    const user = JSON.parse(userInfo);
    return user.userId || '';
  }
  return '';
};

// 统一文件上传函数
export const uploadFile = async (
  file: File,
  uploadType: number,
  userId?: string
): Promise<FileUploadResponse> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('uploadType', String(uploadType));
  
  // 添加userid参数
  const currentUserId = userId || getCurrentUserId();
  if (currentUserId) {
    formData.append('userId', currentUserId);
  }

  const url = UPLOAD_CONFIG.getUploadUrl(uploadType);

  try {
    const response = await authenticatedRequest(url, {
      method: 'POST',
      body: formData,
      // 不设置Content-Type，让浏览器自动设置multipart/form-data
    });
    const res: any = response;
    const rawData = res?.data;

    const clean = (u: any) => {
      if (!u || typeof u !== 'string') return '';
      let s = u.trim();
      s = s.replace(/^`+|`+$/g, '');
      s = s.replace(/^"+|"+$/g, '');
      s = s.replace(/^'+|'+$/g, '');
      return s;
    };

    let fileUrl = '';
    let fileId = '';

    if (typeof rawData === 'string') {
      fileUrl = clean(rawData);
    } else if (rawData && typeof rawData === 'object') {
      fileUrl = clean(rawData.fileUrl || rawData.url);
      fileId = String(rawData.fileId || rawData.id || '');
    }

    return {
      code: String(res?.code ?? ''),
      info: String(res?.info ?? ''),
      success: String(res?.code ?? '') === '0000' && !!fileUrl,
      data: {
        fileId,
        fileUrl
      }
    } as FileUploadResponse;
  } catch (error) {
    console.error('文件上传失败:', error);
    throw error;
  }
};

// 用户背景数据接口
export interface UserBackground {
  id: string;
  name: string;
  url: string;
  createTime?: string;
}

// 用户背景列表响应接口
export interface UserBackgroundsResponse {
  code: string;
  info: string;
  data: {
    data: Array<{
      name: string;
      url: string;
    }>;
    hasNext: boolean;
    nextCursorId?: string | null;
  };
}

// 获取用户背景列表
export const getUserBackgrounds = async (
  lastCursorId: string = '',
  size: number = 10
): Promise<UserBackgroundsResponse> => {
  const url = `/digital-human/background/user?lastCursorId=${lastCursorId}&size=${size}`;
  
  try {
    const response = await authenticatedRequest(url, {
      method: 'GET',
    });
    
    return response as UserBackgroundsResponse;
  } catch (error) {
    console.error('获取用户背景失败:', error);
    throw error;
  }
};

// 用户资源接口定义
export interface UserVideo {
  id: string;
  name: string;
  url: string;
  thumbnail?: string;
  duration?: number;
}

export interface UserIllustration {
  id: string;
  name: string;
  url: string;
}

export interface UserMusic {
  id: string;
  name: string;
  url: string;
  artist?: string;
  duration?: number;
}

export interface UserRecording {
  id: string;
  name: string;
  url: string;
  duration?: number;
  createTime?: string;
}

// 通用用户资源响应接口
export interface UserResourceResponse {
  code: string;
  info: string;
  data: {
    data: Array<{
      name: string;
      url: string;
      duration?: number; // 音频/视频时长（秒）
      createTime?: string; // 创建时间
    }>;
    hasNext: boolean;
    nextCursorId?: string | null;
  };
}

// ==================== 用户资源查询接口 ====================

// 获取用户视频列表
export const getUserVideos = async (
  lastCursorId: string = '',
  size: number = 10
): Promise<UserResourceResponse> => {
  const url = `/digital-human/video/user?lastCursorId=${lastCursorId}&size=${size}`;
  
  try {
    const response = await authenticatedRequest(url, {
      method: 'GET',
    });
    
    return response as UserResourceResponse;
  } catch (error) {
    console.error('获取用户视频失败:', error);
    throw error;
  }
};

// 获取用户插图列表
export const getUserIllustrations = async (
  lastCursorId: string = '',
  size: number = 10
): Promise<UserResourceResponse> => {
  const url = `/digital-human/illustration/user?lastCursorId=${lastCursorId}&size=${size}`;
  
  try {
    const response = await authenticatedRequest(url, {
      method: 'GET',
    });
    
    return response as UserResourceResponse;
  } catch (error) {
    console.error('获取用户插图失败:', error);
    throw error;
  }
};

// 获取用户音乐列表
export const getUserMusic = async (
  lastCursorId: string = '',
  size: number = 10
): Promise<UserResourceResponse> => {
  const url = `/digital-human/music/user?lastCursorId=${lastCursorId}&size=${size}`;
  
  try {
    const response = await authenticatedRequest(url, {
      method: 'GET',
    });
    
    return response as UserResourceResponse;
  } catch (error) {
    console.error('获取用户音乐失败:', error);
    throw error;
  }
};

// 获取用户录音列表
export const getUserRecordings = async (
  lastCursorId: string = '',
  size: number = 10
): Promise<UserResourceResponse> => {
  const url = `/digital-human/recording/user?lastCursorId=${lastCursorId}&size=${size}`;
  
  try {
    const response = await authenticatedRequest(url, {
      method: 'GET',
    });
    
    return response as UserResourceResponse;
  } catch (error) {
    console.error('获取用户录音失败:', error);
    throw error;
  }
};

// ==================== 便捷的上传函数 ====================

export const uploadVideo = (file: File, userId?: string) => uploadFile(file, UPLOAD_TYPE.VIDEO, userId);
export const uploadRecording = (file: File, userId?: string) => uploadFile(file, UPLOAD_TYPE.RECORDING, userId);
export const uploadMusic = (file: File, userId?: string) => uploadFile(file, UPLOAD_TYPE.MUSIC, userId);
export const uploadBackgroundImage = (file: File, userId?: string) => uploadFile(file, UPLOAD_TYPE.BACKGROUND_IMAGE, userId);
export const uploadIllustration = (file: File, userId?: string) => uploadFile(file, UPLOAD_TYPE.ILLUSTRATION, userId);
