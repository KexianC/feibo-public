import { API_CONFIG } from '../config/api';
import { authenticatedRawRequest } from './http/authenticatedRawRequest';

/**
 * ASR 识别结果接口
 */
export interface AsrResult {
  sentences: {
    text: string;
    begin_time: number;
    end_time: number;
  }[];
}

/**
 * 语音识别服务
 */
export const asrService = {
  /**
   * 停止实时录音
   */
  stopRecording: async () => {
    try {
      const response = await authenticatedRawRequest(`${API_CONFIG.BASE_URL}/api/asr/stop`, {
        method: 'GET',
        timeoutMs: 120_000,
      });
      if (!response.ok) {
        throw new Error('停止录音失败');
      }
      return await response.text();
    } catch (error) {
      console.error('Stop recording error:', error);
      throw error;
    }
  },

  /**
   * 上传音频文件进行识别
   * @param file 音频文件
   * @returns 识别结果字符串 (可能需要JSON.parse)
   */
  uploadAudioFile: async (file: File): Promise<string> => {
    const formData = new FormData();
    formData.append('file', file);

    try {
      const response = await authenticatedRawRequest(`${API_CONFIG.BASE_URL}/api/asr/upload`, {
        method: 'POST',
        body: formData,
        timeoutMs: 120_000,
      });

      if (!response.ok) {
        throw new Error(`文件识别失败: ${response.statusText}`);
      }

      return await response.text();
    } catch (error) {
      console.error('Upload audio error:', error);
      throw error;
    }
  },
  // 不再提供 “start” 接口相关的 SSE/WS URL 构造
};
