// 数字人API服务

import { authenticatedRequest } from './authApi';
import { backendDataRequest } from './http/backendClient';
import { v4 as uuidv4 } from 'uuid';
import { Template } from '../types';

// 第三方凭据只允许由后端读取；浏览器端不再接收或签名任何 Mobvoi 密钥。
const API_CONFIG = {
  appKey: '',
  appSecret: '',
  baseUrl: ''
};

// MD5加密函数 - 浏览器兼容版本
function md5(str: string): string {
  // 简单的MD5实现，适用于浏览器环境
  function rotateLeft(value: number, amount: number): number {
    return (value << amount) | (value >>> (32 - amount));
  }

  function addUnsigned(x: number, y: number): number {
    return ((x & 0x7FFFFFFF) + (y & 0x7FFFFFFF)) ^ (x & 0x80000000) ^ (y & 0x80000000);
  }

  function md5cmn(q: number, a: number, b: number, x: number, s: number, t: number): number {
    return addUnsigned(rotateLeft(addUnsigned(addUnsigned(a, q), addUnsigned(x, t)), s), b);
  }

  function md5ff(a: number, b: number, c: number, d: number, x: number, s: number, t: number): number {
    return md5cmn((b & c) | ((~b) & d), a, b, x, s, t);
  }

  function md5gg(a: number, b: number, c: number, d: number, x: number, s: number, t: number): number {
    return md5cmn((b & d) | (c & (~d)), a, b, x, s, t);
  }

  function md5hh(a: number, b: number, c: number, d: number, x: number, s: number, t: number): number {
    return md5cmn(b ^ c ^ d, a, b, x, s, t);
  }

  function md5ii(a: number, b: number, c: number, d: number, x: number, s: number, t: number): number {
    return md5cmn(c ^ (b | (~d)), a, b, x, s, t);
  }

  function convertToWordArray(str: string): number[] {
    const wordArray: number[] = [];
    const messageLength = str.length;
    const numberOfWords = (((messageLength + 8) >>> 6) + 1) * 16;
    
    for (let i = 0; i < numberOfWords; i++) {
      wordArray[i] = 0;
    }
    
    for (let i = 0; i < messageLength; i++) {
      wordArray[i >>> 2] |= (str.charCodeAt(i) & 0xFF) << ((i % 4) * 8);
    }
    
    wordArray[messageLength >>> 2] |= 0x80 << ((messageLength % 4) * 8);
    wordArray[numberOfWords - 2] = messageLength * 8;
    
    return wordArray;
  }

  function wordToHex(value: number): string {
    let hex = '';
    for (let i = 0; i <= 3; i++) {
      const byte = (value >>> (i * 8)) & 0xFF;
      hex += ('0' + byte.toString(16)).slice(-2);
    }
    return hex;
  }

  const x = convertToWordArray(str);
  let a = 0x67452301;
  let b = 0xEFCDAB89;
  let c = 0x98BADCFE;
  let d = 0x10325476;

  for (let i = 0; i < x.length; i += 16) {
    const olda = a;
    const oldb = b;
    const oldc = c;
    const oldd = d;

    a = md5ff(a, b, c, d, x[i + 0], 7, 0xD76AA478);
    d = md5ff(d, a, b, c, x[i + 1], 12, 0xE8C7B756);
    c = md5ff(c, d, a, b, x[i + 2], 17, 0x242070DB);
    b = md5ff(b, c, d, a, x[i + 3], 22, 0xC1BDCEEE);
    a = md5ff(a, b, c, d, x[i + 4], 7, 0xF57C0FAF);
    d = md5ff(d, a, b, c, x[i + 5], 12, 0x4787C62A);
    c = md5ff(c, d, a, b, x[i + 6], 17, 0xA8304613);
    b = md5ff(b, c, d, a, x[i + 7], 22, 0xFD469501);
    a = md5ff(a, b, c, d, x[i + 8], 7, 0x698098D8);
    d = md5ff(d, a, b, c, x[i + 9], 12, 0x8B44F7AF);
    c = md5ff(c, d, a, b, x[i + 10], 17, 0xFFFF5BB1);
    b = md5ff(b, c, d, a, x[i + 11], 22, 0x895CD7BE);
    a = md5ff(a, b, c, d, x[i + 12], 7, 0x6B901122);
    d = md5ff(d, a, b, c, x[i + 13], 12, 0xFD987193);
    c = md5ff(c, d, a, b, x[i + 14], 17, 0xA679438E);
    b = md5ff(b, c, d, a, x[i + 15], 22, 0x49B40821);

    a = md5gg(a, b, c, d, x[i + 1], 5, 0xF61E2562);
    d = md5gg(d, a, b, c, x[i + 6], 9, 0xC040B340);
    c = md5gg(c, d, a, b, x[i + 11], 14, 0x265E5A51);
    b = md5gg(b, c, d, a, x[i + 0], 20, 0xE9B6C7AA);
    a = md5gg(a, b, c, d, x[i + 5], 5, 0xD62F105D);
    d = md5gg(d, a, b, c, x[i + 10], 9, 0x02441453);
    c = md5gg(c, d, a, b, x[i + 15], 14, 0xD8A1E681);
    b = md5gg(b, c, d, a, x[i + 4], 20, 0xE7D3FBC8);
    a = md5gg(a, b, c, d, x[i + 9], 5, 0x21E1CDE6);
    d = md5gg(d, a, b, c, x[i + 14], 9, 0xC33707D6);
    c = md5gg(c, d, a, b, x[i + 3], 14, 0xF4D50D87);
    b = md5gg(b, c, d, a, x[i + 8], 20, 0x455A14ED);
    a = md5gg(a, b, c, d, x[i + 13], 5, 0xA9E3E905);
    d = md5gg(d, a, b, c, x[i + 2], 9, 0xFCEFA3F8);
    c = md5gg(c, d, a, b, x[i + 7], 14, 0x676F02D9);
    b = md5gg(b, c, d, a, x[i + 12], 20, 0x8D2A4C8A);

    a = md5hh(a, b, c, d, x[i + 5], 4, 0xFFFA3942);
    d = md5hh(d, a, b, c, x[i + 8], 11, 0x8771F681);
    c = md5hh(c, d, a, b, x[i + 11], 16, 0x6D9D6122);
    b = md5hh(b, c, d, a, x[i + 14], 23, 0xFDE5380C);
    a = md5hh(a, b, c, d, x[i + 1], 4, 0xA4BEEA44);
    d = md5hh(d, a, b, c, x[i + 4], 11, 0x4BDECFA9);
    c = md5hh(c, d, a, b, x[i + 7], 16, 0xF6BB4B60);
    b = md5hh(b, c, d, a, x[i + 10], 23, 0xBEBFBC70);
    a = md5hh(a, b, c, d, x[i + 13], 4, 0x289B7EC6);
    d = md5hh(d, a, b, c, x[i + 0], 11, 0xEAA127FA);
    c = md5hh(c, d, a, b, x[i + 3], 16, 0xD4EF3085);
    b = md5hh(b, c, d, a, x[i + 6], 23, 0x04881D05);
    a = md5hh(a, b, c, d, x[i + 9], 4, 0xD9D4D039);
    d = md5hh(d, a, b, c, x[i + 12], 11, 0xE6DB99E5);
    c = md5hh(c, d, a, b, x[i + 15], 16, 0x1FA27CF8);
    b = md5hh(b, c, d, a, x[i + 2], 23, 0xC4AC5665);

    a = md5ii(a, b, c, d, x[i + 0], 6, 0xF4292244);
    d = md5ii(d, a, b, c, x[i + 7], 10, 0x432AFF97);
    c = md5ii(c, d, a, b, x[i + 14], 15, 0xAB9423A7);
    b = md5ii(b, c, d, a, x[i + 5], 21, 0xFC93A039);
    a = md5ii(a, b, c, d, x[i + 12], 6, 0x655B59C3);
    d = md5ii(d, a, b, c, x[i + 3], 10, 0x8F0CCC92);
    c = md5ii(c, d, a, b, x[i + 10], 15, 0xFFEFF47D);
    b = md5ii(b, c, d, a, x[i + 1], 21, 0x85845DD1);
    a = md5ii(a, b, c, d, x[i + 8], 6, 0x6FA87E4F);
    d = md5ii(d, a, b, c, x[i + 15], 10, 0xFE2CE6E0);
    c = md5ii(c, d, a, b, x[i + 6], 15, 0xA3014314);
    b = md5ii(b, c, d, a, x[i + 13], 21, 0x4E0811A1);
    a = md5ii(a, b, c, d, x[i + 4], 6, 0xF7537E82);
    d = md5ii(d, a, b, c, x[i + 11], 10, 0xBD3AF235);
    c = md5ii(c, d, a, b, x[i + 2], 15, 0x2AD7D2BB);
    b = md5ii(b, c, d, a, x[i + 9], 21, 0xEB86D391);

    a = addUnsigned(a, olda);
    b = addUnsigned(b, oldb);
    c = addUnsigned(c, oldc);
    d = addUnsigned(d, oldd);
  }

  return (wordToHex(a) + wordToHex(b) + wordToHex(c) + wordToHex(d)).toLowerCase();
}

// 生成签名
export function generateSignature(timestamp: string): string {
  const plainText = `${API_CONFIG.appKey}+${API_CONFIG.appSecret}+${timestamp}`;
  return md5(plainText);
}

// 获取请求头
/**
 * 构建认证请求头
 * 函数级注释：可传入秒级时间戳以复用同一签名；若不传则内部生成。
 */
export function getAuthHeaders(timestamp?: string) {
  const ts = timestamp ?? Math.floor(Date.now() / 1000).toString();
  const signature = generateSignature(ts);
  
  return {
    'Content-Type': 'application/json',
    'appKey': API_CONFIG.appKey,
    'timestamp': ts,
    'signature': signature
  };
}

// 视频合成请求体接口
export interface VideoComposeRequest {
  callbackUrl?: string;
  height: number;
  width: number;
  name: string;
  items: VideoComposeItem[];
}

export interface VideoComposeItem {
  detailList: VideoComposeDetail[];
}

// 文本内容结构体
export interface TextContent {
  content: string; // 文字内容
  magicSpeakerId: string; // 声音的id
  srtLen?: number; // 字幕最大换行字符数，默认18
  audioType?: string; // 合成音频的格式，默认wav
  convert?: string; // 是否转化为机器声，可选值：robot
  mergeSymbol?: boolean; // 粗粒度合成参数，默认false
  pitch?: number; // 语调参数，默认0，可选值：-10<pitch<10
  rate?: number; // 音频采样率：8000/16000/24000，默认16000
  speed?: number; // 语速，可选值:0.5-2.0，默认1.0
  volume?: number; // 音量，可选值：0.1-1.0，默认1.0
}

export interface VideoComposeDetail {
  contentType: 'avatar' | 'video' | 'text' | 'tag' | 'subtitles' | 'back_audio' | 'audio';
  sortNum?: number;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  content?: string;
  text?: TextContent; // 文本驱动内容
  wetaSpeakerId?: string;
  magicSpeakerId?: string; // 保留兼容性
  startTime?: number;
  stopTime?: number;
  fontname?: string;
  bold?: number | string; // 支持字符串类型
  fontsize?: number | string; // 支持字符串类型
  primaryColour?: string;
  underline?: number;
  italic?: number;
  loop?: boolean;
  srtLen?: number;
  audioType?: string;
  convert?: string;
  mergeSymbol?: boolean;
  pitch?: number;
  rate?: number;
  speed?: number;
  volume?: number;
  useAvatarHead?: boolean;
  headx?: number;
  heady?: number;
  headr?: number;
  // 字幕相关属性
  subtitleUsepx?: string;
  bottomSpace?: number;
  wordSpace?: number;
}

// 视频合成响应接口
export interface VideoComposeResponse {
  code: string | number; // 状态码，主要接口成功为"0000"（字符串），备用接口成功为200（数字）
  message?: string;
  info?: string; // 响应信息
  data: string; // 任务ID
  ok?: boolean;
}

// 视频合成结果查询响应接口
export interface VideoComposeResultResponse {
  code: number;
  message: string;
  data: {
    taskId: string;
    id?: string;
    status: 'pending' | 'processing' | 'completed' | 'suc' | 'failed' | 'fail' | 'ing';
    statusDesc?: string;
    progress?: number; // 0-100
    videoUrl?: string;
    resultUrl?: string;
    coverUrl?: string;
    thumbnailUrl?: string;
    duration?: string | number;
    name?: string;
    width?: number;
    height?: number;
    created?: string;
    createTime?: string;
    lastUpdateTime?: string;
    updateTime?: string;
    alphaUrl?: string;
    errorMessage?: string;
  };
  ok: boolean;
}

// API返回的数字人数据结构
export interface ApiDigitalHuman {
  id: string;
  speakerName: string;
  speakerId: string;
  speakerType: string;
  speakerWidth: number;
  speakerHeight: number;
  speakerUrl: string;
  speakerModelType: string;
  gender: string;
  magicAudioId: string;
  headx: number;
  heady: number;
  headr: number;
  headUrl: string;
  headWidth: number;
  headHeight: number;
  customType: string;
  created: string;
  status: string;
  lastUpdateTime: string;
  speakerDemoUrl?: string;
  [key: string]: any;
}

// API响应结构
export interface ApiResponse {
  code: number;
  data: {
    records: ApiDigitalHuman[];
    total: number;
    size: number;
    current: number;
    pages: number;
  };
  message: string;
}

// 数字人分类映射
const getCategoryFromGender = (gender: string, name: string): 'business' | 'education' | 'entertainment' => {
  const lowerName = name.toLowerCase();
  
  // 教育类关键词
  if (lowerName.includes('教师') || lowerName.includes('老师') || lowerName.includes('讲师') || 
      lowerName.includes('教授') || lowerName.includes('培训') || lowerName.includes('学者')) {
    return 'education';
  }
  
  // 娱乐类关键词
  if (lowerName.includes('主播') || lowerName.includes('演员') || lowerName.includes('艺人') || 
      lowerName.includes('网红') || lowerName.includes('明星') || lowerName.includes('娱乐')) {
    return 'entertainment';
  }
  
  // 根据性别和名称判断商务类
  return 'business';
};

// 获取数字人列表
export const fetchDigitalHumans = async (): Promise<import('../types').DigitalHuman[]> => {
  try {
    const response = await fetch(
      'https://openman.weta365.com/metaman/open/avatar/infos?customType=sys&current=1&size=139&speakerName=元',
      {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const apiResponse: ApiResponse = await response.json();
    
    if (apiResponse.code !== 200 || !apiResponse.data?.records) {
      throw new Error(apiResponse.message || '获取数字人数据失败');
    }

    // 转换API数据为应用内数据格式
    const digitalHumans = apiResponse.data.records.map((apiDH: ApiDigitalHuman) => ({
      id: apiDH.id,
      name: apiDH.speakerName || '未命名数字人',
      avatar: apiDH.headUrl?.trim() || 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjNEE1NTY4Ii8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iNDAiIHI9IjE1IiBmaWxsPSIjNkI3Mjg0Ii8+CjxwYXRoIGQ9Ik0yNSA3NUMyNSA2NS4zMzUgMzMuMzM1IDU3IDQzIDU3SDE1N0M2Ni42NjUgNTcgNzUgNjUuMzM1IDc1IDc1VjgwSDI1Vjc1WiIgZmlsbD0iIzZCNzI4NCIvPgo8L3N2Zz4K',
      category: getCategoryFromGender(apiDH.gender, apiDH.speakerName || ''),
      modelUrl: `/models/${apiDH.id}.glb`, // 假设模型文件路径
      speakerUrl: apiDH.speakerUrl?.trim(), // 完整数字人图像URL
      speakerId: apiDH.speakerId,
      gender: apiDH.gender,
      magicAudioId: apiDH.magicAudioId,
      speakerDemoUrl: apiDH.speakerDemoUrl?.trim(),
      speakerWidth: apiDH.speakerWidth, // 数字人原始宽度
      speakerHeight: apiDH.speakerHeight // 数字人原始高度
    }));

    return digitalHumans;
  } catch (error) {
    console.error('获取数字人数据失败:', error);
    
    // 返回备用数据
    return [
      {
        id: 'fallback-1',
        name: '商务男士',
        avatar: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjMkQ0Mzc0Ii8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iMzgiIHI9IjE4IiBmaWxsPSIjNjM3M0ZGIi8+CjxwYXRoIGQ9Ik0yMCA4MEMyMCA2OC45NTQzIDI4Ljk1NDMgNjAgNDAgNjBINjBDNzEuMDQ1NyA2MCA4MCA2OC45NTQzIDgwIDgwVjg1SDIwVjgwWiIgZmlsbD0iIzYzNzNGRiIvPgo8L3N2Zz4K',
        category: 'business',
        modelUrl: '/models/business-man.glb'
      },
      {
        id: 'fallback-2',
        name: '商务女士',
        avatar: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjNzQ0MjEwIi8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iMzgiIHI9IjE4IiBmaWxsPSIjRkY2Mzc0Ii8+CjxwYXRoIGQ9Ik0yMCA4MEMyMCA2OC45NTQzIDI4Ljk1NDMgNjAgNDAgNjBINjBDNzEuMDQ1NyA2MCA4MCA2OC45NTQzIDgwIDgwVjg1SDIwVjgwWiIgZmlsbD0iI0ZGNjM3NCIvPgo8L3N2Zz4K',
        category: 'business',
        modelUrl: '/models/business-woman.glb'
      },
      {
        id: 'fallback-3',
        name: '教师',
        avatar: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjMDY0RTNBIi8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iMzgiIHI9IjE4IiBmaWxsPSIjMTBCOTgxIi8+CjxwYXRoIGQ9Ik0yMCA4MEMyMCA2OC45NTQzIDI4Ljk1NDMgNjAgNDAgNjBINjBDNzEuMDQ1NyA2MCA4MCA2OC45NTQzIDgwIDgwVjg1SDIwVjgwWiIgZmlsbD0iIzEwQjk4MSIvPgo8L3N2Zz4K',
        category: 'education',
        modelUrl: '/models/teacher.glb'
      }
    ];
  }
};

// 刷新数字人数据
export const refreshDigitalHumans = async (): Promise<import('../types').DigitalHuman[]> => {
  return await fetchDigitalHumans();
};

/**
 * 计算视频合成预计时长（分钟）
 * @param request 视频合成请求体
 * @returns 预计时长（分钟），最低为1，向上取整
 */
const calculateVideoTime = (request: VideoComposeRequest): number => {
  let totalTime = 0;
  
  // 遍历所有详细项目
  for (const item of request.items) {
    for (const detail of item.detailList) {
      if (detail.contentType === 'text' && detail.content) {
        // 文本内容按160字符/分钟计算
        const textLength = detail.content.length;
        const textTime = textLength / 160;
        totalTime = Math.max(totalTime, textTime);
      } else if (detail.contentType === 'audio' && detail.content) {
        // 音频内容需要获取时长，这里暂时按1分钟计算
        // 实际项目中可能需要通过其他方式获取音频时长
        totalTime = Math.max(totalTime, 1);
      }
    }
  }
  
  // 向上取整，最低为1分钟
  return Math.max(Math.ceil(totalTime), 1);
};


/**
 * 数字人视频合成接口
 * 使用幂等令牌确保重复请求的安全性，防止意外的重复合成操作
 * 通过后端接口进行数字人视频合成
 * @param request 视频合成请求参数
 * @returns 返回合成任务ID和状态信息
 */
export const composeVideo = async (request: VideoComposeRequest): Promise<VideoComposeResponse> => {
  try {
    // 生成幂等令牌，确保重复请求的幂等性
    // 使用uuid库生成UUID，兼容性更好
    const idempotencyToken = uuidv4();
    
    // 计算预计视频时长
    const videoTime = calculateVideoTime(request);
    
    // 构建新的请求数据格式
    const requestData = {
      body: JSON.stringify(request), // 将原请求体作为字符串传递
      videoTime: videoTime // 预计合成视频消耗时长（分钟）
    };
    
    console.log('数字人合成请求，预计时长:', videoTime);
    
    // 使用authenticatedRequest发送请求，传入幂等令牌
    const result: VideoComposeResponse = await authenticatedRequest('/digital-human/work/composition', {
      method: 'POST',
      body: JSON.stringify(requestData)
    }, 0, idempotencyToken);
    
    // 检查响应状态码，成功状态码为字符串"0000"
    if (result.code !== '0000') {
      throw new Error(result.message || result.info || '视频合成失败');
    }

    return result;
  } catch (error) {
    console.error('数字人视频合成失败:', error);
    throw error;
  }
};

// 中文字符检测函数
const containsChinese = (text: string): boolean => {
  return /[\u4e00-\u9fff]/.test(text);
};

// 不支持中文的字体列表
const nonChineseFonts = new Set([
  'Arial',
  'Times New Roman',
  'Helvetica',
  'Georgia',
  'Verdana',
  'Calibri',
  'Tahoma',
  'Trebuchet MS',
  'Courier New'
]);

// 字体名称映射函数：将中文字体名转换为API所需的key值，并处理中文兼容性
const getFontKey = (fontFamily?: string, textContent?: string): string => {
  if (!fontFamily) return 'STSong';
  
  // 字体映射表
  const fontMapping: { [key: string]: string } = {
    '宋体': 'STSong',
    '黑体': 'STHeiti',
    '楷体': 'STKaiti',
    '仿宋': 'STFangsong',
    '微软雅黑': 'Microsoft YaHei',
    '华文黑体': 'STHeiti',
    '华文楷体': 'STKaiti',
    '华文宋体': 'STSong',
    '华文仿宋': 'STFangsong',
    'Arial': 'Arial',
    'Times New Roman': 'Times New Roman',
    'Helvetica': 'Helvetica',
    'Georgia': 'Georgia',
    'Verdana': 'Verdana'
  };
  
  // 获取映射后的字体
  let mappedFont = fontMapping[fontFamily] || fontFamily || 'STSong';
  
  // 如果文本包含中文且使用的是不支持中文的字体，则替换为支持中文的默认字体
  if (textContent && containsChinese(textContent) && nonChineseFonts.has(mappedFont)) {
    return 'STSong'; // 使用宋体作为中文的默认字体
  }
  
  return mappedFont;
};

/**
 * 构建数字人视频合成请求体
 * - 负责将画布元素与驱动参数转换为接口所需的数据结构
 * - 新增参数 callbackUrl：用于服务端在合成完成后回调通知结果
 *   如果未显式传入，则使用默认值 'https://api.hzmwmt.com/api/v1/digital-human/work/video-callback'
 * @param params.name        任务名称
 * @param params.width       画布宽度（默认 1920）
 * @param params.height      画布高度（默认 1080）
 * @param params.elements    画布元素列表
 * @param params.digitalHuman 数字人信息（兼容旧参数）
 * @param params.text        文本驱动内容（文本驱动模式）
 * @param params.magicSpeakerId  语音合成模型ID（默认 'moqianxue_meet_24k'）
 * @param params.voiceSpeed  语速（默认 1.0）
 * @param params.voiceVolume 音量（默认 1.0）
 * @param params.callbackUrl 回调地址（默认使用上述固定回调地址）
 * @param params.isPreview   是否预览（文本长度截断）
 * @param params.subtitleConfig 字幕配置（页面级）
 * 备注：bottomSpace 与 wordSpace 使用空值合并（nullish coalescing），保留 0 值，不回退到默认值
 * @param params.driveMode   驱动模式：'text' | 'audio'（默认 'text'）
 * @param params.audioFile   音频文件对象（语音驱动模式）
 * @returns VideoComposeRequest
 */
export const buildVideoComposeRequest = ({
  name,
  width = 1920,
  height = 1080,
  elements = [],
  digitalHuman,
  text,
  magicSpeakerId = 'moqianxue_meet_24k',
  voiceSpeed = 1.0,
  voiceVolume = 1.0,
  callbackUrl = 'https://api.hzmwmt.com/api/v1/digital-human/work/video-callback',
  isPreview = false,
  subtitleConfig,
  driveMode = 'text', // 新增驱动模式参数，默认为文本驱动
  audioFile // 新增音频文件参数
}: {
  name: string;
  width?: number;
  height?: number;
  elements?: any[]; // 画布上的所有元素
  digitalHuman?: any;
  text?: string;
  magicSpeakerId?: string;
  voiceSpeed?: number; // 语速参数
  voiceVolume?: number; // 音量参数
  callbackUrl?: string;
  isPreview?: boolean;
  subtitleConfig?: any; // 页面级别的字幕配置
  driveMode?: 'text' | 'audio'; // 驱动模式：text=文本驱动，audio=语音驱动
  audioFile?: any; // 音频文件对象，包含url等信息
}): VideoComposeRequest => {
  const detailList: VideoComposeDetail[] = [];
  
  // 标记是否已经添加了text内容，避免重复添加
  let hasTextContent = false;

  // 处理画布上的所有元素
  if (elements && elements.length > 0) {
    // 按zIndex排序元素
    const sortedElements = [...elements].sort((a, b) => a.zIndex - b.zIndex);
    
    for (const element of sortedElements) {
      switch (element.type) {
        case 'background':
          // 背景元素转换为video类型
          if (element.properties?.backgroundType === 'image' && element.properties?.imageUrl) {
            detailList.push({
              contentType: 'video',
              content: element.properties.imageUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav'
            });
          } else if (element.properties?.backgroundType === 'video' && element.properties?.videoUrl) {
            detailList.push({
              contentType: 'video',
              content: element.properties.videoUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav'
            });
          }
          // 颜色背景暂时跳过，API不支持纯色content
          // } else if (element.properties?.backgroundType === 'color' && element.properties?.color) {
          //   // 颜色背景处理逻辑待实现
          // }
          break;

        case 'illustration':
          // 插图元素转换为video类型
          if (element.properties?.imageUrl) {
            detailList.push({
              contentType: 'video',
              content: element.properties.imageUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav'
            });
          }
          break;

        case 'video':
          // 视频元素
          if (element.properties?.videoUrl) {
            detailList.push({
              contentType: 'video',
              content: element.properties.videoUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav'
            });
          }
          break;

        case 'title':
          // 标题元素转换为tag类型
          if (element.properties?.text) {
            detailList.push({
              contentType: 'tag',
              content: element.properties.text,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              startTime: element.properties?.startTime || 0,
              stopTime: element.properties?.stopTime || 999999,
              fontsize: element.properties?.fontSize,
              fontname: getFontKey(element.properties?.fontFamily, element.properties.text),
              primaryColour: element.properties?.color || '#000000',
              bold: element.properties?.fontWeight === 'bold' ? 1 : 0,
              italic: element.properties?.fontStyle === 'italic' ? 1 : 0
            });
          }
          break;

        case 'flash-text':
          // 闪文元素转换为tag类型
          if (element.properties?.text) {
            detailList.push({
              contentType: 'tag',
              content: element.properties.text,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              startTime: element.properties?.startTime || 0,
              stopTime: element.properties?.endTime || 999999,
              fontsize: element.properties?.fontSize,
              fontname: getFontKey(element.properties?.fontFamily, element.properties.text),
              primaryColour: element.properties?.textColor || element.properties?.color || '#000000'
            });
          }
          break;

        case 'subtitle':
          // 字幕元素转换为subtitles类型
          // 检查字幕元素是否有文本内容（从SubtitlePanel创建的字幕元素没有默认text，需要用户输入）
          // 如果没有text属性，使用默认的字幕文本
          const subtitleText = element.properties?.text || '请输入字幕内容';
          detailList.push({
            contentType: 'subtitles',
            content: subtitleText,
            magicSpeakerId: magicSpeakerId,
            volume: 1.0,
            audioType: 'wav',
            sortNum: element.zIndex,
            x: element.position.x,
            y: element.position.y,
            width: element.size.width,
            height: element.size.height,
            // 按照用户提供的格式设置字幕参数
            primaryColour: element.properties?.primaryColour || element.properties?.textColor || '#800000',
            fontname: element.properties?.fontname || element.properties?.fontFamily || 'ZQKNLT',
            bold: String(element.properties?.bold !== undefined ? element.properties.bold : (element.properties?.isBold ? 1 : 0)),
            subtitleUsepx: element.properties?.subtitleUsepx || 'Y',
            fontsize: String(element.properties?.fontsize || element.properties?.fontSize || 35),
            underline: element.properties?.underline !== undefined ? element.properties.underline : (element.properties?.isUnderline ? 1 : 0),
            italic: element.properties?.italic !== undefined ? element.properties.italic : (element.properties?.isItalic ? 1 : 0),
            bottomSpace: (element.properties?.bottomSpace ?? element.properties?.bottomDistance ?? 10),
            wordSpace: (element.properties?.wordSpace ?? 20)
          });
          break;

        case 'digital-human':
          // 数字人元素转换为avatar类型
          // 检查新的数据结构（digitalHumanId）或旧的数据结构（digitalHuman）
          if (element.properties?.digitalHumanId || element.properties?.digitalHuman) {
            let digitalHumanData;
            
            if (element.properties.digitalHumanId) {
              // 新的数据结构：从properties中获取数字人信息
              digitalHumanData = {
                id: element.properties.digitalHumanId,
                headx: element.properties.headx,
                heady: element.properties.heady,
                headr: element.properties.headr
              };
            } else {
              // 旧的数据结构：直接使用digitalHuman对象
              digitalHumanData = element.properties.digitalHuman;
            }
            
            const wetaSpeakerId = digitalHumanData.id; // 直接使用字符串类型的id
            
            // 添加数字人avatar元素
            detailList.push({
              contentType: 'avatar',
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              useAvatarHead: digitalHumanData.headx ? true : false,
              headx: digitalHumanData.headx,
              heady: digitalHumanData.heady,
              headr: digitalHumanData.headr,
              wetaSpeakerId: wetaSpeakerId,
              audioType: 'wav'
            });
            
            // 如果数字人有文案内容，添加对应的text元素（仅在文本驱动模式下）
            let digitalHumanText = element.properties?.text || text; // 优先使用元素自身的文案，否则使用传入的text参数
            if (digitalHumanText && !hasTextContent && driveMode === 'text') {
              // 预览模式下截取前80个字符
              if (isPreview && digitalHumanText.length > 80) {
                digitalHumanText = digitalHumanText.substring(0, 80);
              }
              
              detailList.push({
                contentType: 'text',
                content: digitalHumanText,
                magicSpeakerId: magicSpeakerId,
                srtLen: 18,
                audioType: 'wav',
                rate: 16000,
                speed: voiceSpeed,
                volume: voiceVolume,
                sortNum: element.zIndex + 100 // 确保文案在数字人之后处理
              });
              
              // 标记已经添加了text内容
              hasTextContent = true;
            }
          }
          break;

        case 'image':
          // 图片元素转换为video类型
          if (element.properties?.imageUrl) {
            detailList.push({
              contentType: 'video',
              content: element.properties.imageUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav'
            });
          }
          break;

        case 'text':
          // 文本元素转换为text类型（仅在文本驱动模式下）
          if (element.properties?.text && !hasTextContent && driveMode === 'text') {
            detailList.push({
              contentType: 'text',
              content: element.properties.text,
              magicSpeakerId: magicSpeakerId,
              srtLen: 18,
              audioType: 'wav',
              rate: 16000,
              speed: voiceSpeed,
              volume: voiceVolume,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              fontname: getFontKey(element.properties?.fontFamily, element.properties.text),
              fontsize: element.properties?.fontSize || 16,
              primaryColour: element.properties?.color || '#000000',
              bold: element.properties?.fontWeight === 'bold' ? 1 : 0,
              italic: element.properties?.fontStyle === 'italic' ? 1 : 0,
              underline: element.properties?.textDecoration === 'underline' ? 1 : 0
            });
            
            // 标记已经添加了text内容
            hasTextContent = true;
          }
          break;

        case 'shape':
          // 形状元素暂时跳过，API可能不支持
          // 可以考虑将形状转换为图片后作为video类型处理
          break;

        case 'voice':
          // 配音元素转换为audio类型
          if (element.properties?.audioUrl) {
            detailList.push({
              contentType: 'audio',
              content: element.properties.audioUrl,
              sortNum: element.zIndex,
              audioType: 'wav',
              volume: element.properties?.volume || 1.0
            });
          }
          break;

        case 'music':
          // 音乐元素转换为back_audio类型
          if (element.properties?.audioUrl) {
            detailList.push({
              contentType: 'back_audio',
              content: element.properties.audioUrl,
              sortNum: element.zIndex,
              audioType: 'wav',
              volume: element.properties?.volume || 1.0
            });
          }
          break;

        default:
          // 未知元素类型，记录警告
          console.warn(`未处理的元素类型: ${element.type}`);
          break;
      }
    }
  }

  // 兼容旧的参数方式 - 添加数字人
  if (digitalHuman && !elements?.some(el => el.type === 'digital-human')) {
    const wetaSpeakerId = digitalHuman.id;
    
    const avatarDetail: VideoComposeDetail = {
      contentType: 'avatar',
      sortNum: 2,
      x: 0,
      y: 0,
      width: digitalHuman.speakerWidth,
      height: digitalHuman.speakerHeight,
      useAvatarHead: digitalHuman.headx ? true : false,
      headx: digitalHuman.headx,
      heady: digitalHuman.heady,
      headr: digitalHuman.headr,
      wetaSpeakerId: wetaSpeakerId,
      audioType: 'wav'
    };
    
    detailList.push(avatarDetail);
  }

  // 兼容旧的参数方式 - 添加文本（仅在文本驱动模式下）
  // 检查是否已经有text元素被添加，避免重复添加
  if (text && !hasTextContent && driveMode === 'text' && !elements?.some(el => ['title', 'flash-text', 'subtitle', 'text', 'digital-human'].includes(el.type))) {
    // 预览模式下截取前80个字符
    let processedText = text;
    if (isPreview && text.length > 80) {
      processedText = text.substring(0, 80);
    }
    
    detailList.push({
      contentType: 'text',
      content: processedText,
      magicSpeakerId: magicSpeakerId,
      srtLen: 18,
      audioType: 'wav',
      rate: 16000,
      speed: voiceSpeed,
      volume: voiceVolume
    });
    
    // 标记已经添加了text内容
    hasTextContent = true;
  }

  // 处理页面级别的字幕配置（仅在文本驱动模式下）
  if (subtitleConfig && subtitleConfig.isEnabled && text && driveMode === 'text') {
    // 预览模式下截取前80个字符
    let subtitleText = text;
    if (isPreview && text.length > 80) {
      subtitleText = text.substring(0, 80);
    }
    
    detailList.push({
      contentType: 'subtitles',
      content: subtitleText,
      magicSpeakerId: magicSpeakerId,
      volume: 1.0,
      audioType: 'wav',
      sortNum: 999, // 字幕层级较高
      x: 0,
      y: height - (subtitleConfig.bottomSpace ?? 10) - parseInt(subtitleConfig.fontsize ?? '35'),
      width: width,
      height: parseInt(subtitleConfig.fontsize || '35'),
      // 按照用户提供的格式设置字幕参数
      primaryColour: subtitleConfig.primaryColour || '#000000',
      fontname: subtitleConfig.fontname || 'STSONG',
      bold: subtitleConfig.bold || '0',
      subtitleUsepx: subtitleConfig.subtitleUsepx || 'Y',
      fontsize: subtitleConfig.fontsize || '35',
      underline: subtitleConfig.underline !== undefined ? subtitleConfig.underline : 0,
      italic: subtitleConfig.italic !== undefined ? subtitleConfig.italic : 0,
      bottomSpace: (subtitleConfig.bottomSpace ?? 10),
      wordSpace: (subtitleConfig.wordSpace ?? 20)
    });
  }

  // 处理语音驱动模式下的音频文件
  if (driveMode === 'audio' && audioFile && audioFile.url) {
    detailList.push({
      contentType: 'audio',
      content: audioFile.url,
      sortNum: 1, // 音频优先级较高
      audioType: 'wav',
      volume: voiceVolume || 1.0
    });
  }

  return {
    name,
    width,
    height,
    callbackUrl,
    items: [{
      detailList
    }]
  };
};


export interface VoiceAuditionRequest {
  text: string;
  speaker: string;
  audioType?: 'mp3' | 'wav';
  speed: number;
  volume: number;
  pitch?: number;
}

export interface VoiceAuditionResult {
  url: string;
  [key: string]: unknown;
}

/**
 * 经登录态后端代理进行语音试听，第三方认证仅在服务端完成。
 */
export const auditionVoice = async (request: VoiceAuditionRequest): Promise<VoiceAuditionResult> => {
  const response = await authenticatedRequest<VoiceAuditionResult>(
    '/digital-human/open/voice/audition',
    {
      method: 'POST',
      body: JSON.stringify({
        text: request.text,
        speaker: request.speaker.trim(),
        audioType: request.audioType ?? 'mp3',
        speed: request.speed,
        volume: request.volume,
        pitch: request.pitch ?? 0
      })
    }
  );

  return response.data;
};


// 查询视频合成结果
export const getVideoComposeResult = async (taskId: string): Promise<VideoComposeResultResponse['data']> => {
  try {
    const response = await authenticatedRequest<VideoComposeResultResponse['data']>(
      `/digital-human/open/video/result/${encodeURIComponent(taskId)}`,
      { method: 'GET' }
    );
    return response.data;
  } catch (error) {
    console.error('查询视频合成结果失败:', error);
    throw error;
  }
};

export class VideoComposeBusinessError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'VideoComposeBusinessError';
  }
}

/**
 * 轮询查询视频合成结果
 * 持续查询视频合成状态直到完成或失败
 * 
 * 状态处理逻辑：
 * - 'ing': 合成中，继续轮询
 * - 'suc' | 'completed': 合成成功，返回结果
 * - 'failed' | 'fail': 合成失败，抛出错误
 * - 其他状态: 继续轮询
 * 
 * @param taskId 任务ID
 * @param onProgress 进度回调函数
 * @param interval 轮询间隔（毫秒）
 * @returns 合成结果数据
 */
export const pollVideoComposeResult = async (
  taskId: string,
  onProgress?: (progress: number, status: string) => void,
  interval: number = 2000
): Promise<VideoComposeResultResponse['data']> => {
  console.log(`开始轮询视频合成结果，任务ID: ${taskId}，轮询间隔: ${interval}ms`);
  
  return new Promise((resolve, reject) => {
    let pollCount = 0; // 轮询次数计数器
    
    const poll = async () => {
      pollCount++;
      console.log(`第 ${pollCount} 次轮询，任务ID: ${taskId}`);
      
      try {
        const result = await getVideoComposeResult(taskId);
        
        console.log(`轮询结果 #${pollCount}:`, {
          taskId,
          status: result.status === 'ing' ? 'processing' : result.status,
          progress: result.progress
        });
        
        // 状态映射：将API返回的'ing'状态映射为组件能识别的状态
        let mappedStatus = result.status;
        if (result.status === 'ing') {
          mappedStatus = 'ing'; // 保持'ing'状态，组件已支持
          console.log(`状态为 'ing'，继续轮询...`);
        }
        
        // 调用进度回调
        if (onProgress) {
          onProgress(result.progress || 0, mappedStatus);
          console.log(`进度回调已调用: progress=${result.progress || 0}, status=${mappedStatus}`);
        }
        
        // 检查是否完成
        if (result.status === 'suc' || result.status === 'completed') {
          console.log(`视频合成成功完成，状态: ${result.status}，任务ID: ${taskId}`);
          resolve(result);
          return;
        }
        
        // 检查是否失败
        if (result.status === 'failed' || result.status === 'fail') {
          const failureReason = result.errorMessage || result.statusDesc || '服务未返回具体失败原因';
          const errorMsg = `合成失败：${failureReason}`;
          console.error(`视频合成失败，状态: ${result.status}，任务ID: ${taskId}`);
          reject(new VideoComposeBusinessError(errorMsg));
          return;
        }
        
        // 继续轮询，无超时限制
        console.log(`状态为 '${result.status}'，${interval}ms 后继续轮询...`);
        setTimeout(poll, interval);
      } catch (error) {
        console.error(`轮询过程中发生错误 #${pollCount}:`, error);
        reject(error);
      }
    };
    
    poll();
  });
};

// 用户作品接口定义
export interface UserWork {
  id: string;
  taskId?: string;
  name: string;
  videoUrl?: string;
  coverUrl?: string;
  thumbnailUrl?: string;
  duration?: string | number;
  width?: number;
  height?: number;
  status: 'pending' | 'processing' | 'completed' | 'suc' | 'failed' | 'fail';
  progress?: number;
  createTime: string;
  updateTime?: string;
  errorMessage?: string;
  digitalWorkId: string; // 作品ID，用于修改和删除操作
  title?: string | null; // 作品标题，可能为null
  body: string; // 作品详细信息的JSON字符串
  externalId: string; // 外部ID，用于查询视频信息
}

// 获取用户作品列表响应接口
export interface UserWorksResponse {
  code: string;
  info: string;
  data: {
    data: UserWork[];
    hasNext: boolean;
    nextCursorId: string | null;
  };
}

// 获取用户作品列表
/**
 * 获取用户作品列表
 * 使用新的后端接口获取作品数据
 */
/**
 * 获取用户作品列表
 * 使用新的后端接口获取作品数据
 * 根据实际API响应格式调整数据结构
 */
/**
 * 获取用户作品列表
 * 支持游标分页查询用户的所有作品
 * 返回的数据包含digitalWorkId（用于修改删除）和externalId（用于查询视频信息）
 */
export const getUserWorks = async (lastCursorId?: string, size: number = 20): Promise<{ records: UserWork[]; hasNext: boolean; nextCursorId: string | null; }> => {
  try {
    // 构建查询参数
    const queryParams = new URLSearchParams();
    if (lastCursorId) {
      queryParams.append('lastCursorId', lastCursorId);
    }
    queryParams.append('size', size.toString());

    const url = `/digital-human/work/index?${queryParams.toString()}`;
    
    const response = await authenticatedRequest<UserWorksResponse>(url, {
      method: 'GET'
    });

    // 根据API响应格式映射数据结构：response.data包含嵌套的data对象
    const responseData = response.data as any;
    const works = responseData.data.map((work: any) => {
      // 优先使用title，如果为空则尝试从body中解析name，最后使用默认名称
      let workName = work.title;
      if (!workName) {
        try {
          const bodyData = JSON.parse(work.body);
          workName = bodyData.name || `作品_${work.digitalWorkId}`;
        } catch {
          workName = `作品_${work.digitalWorkId}`;
        }
      }

      const normalizedStatus = work.status === 1
        ? 'suc'
        : work.status === 0
          ? 'fail'
          : work.status || 'processing';
      
      return {
        ...work,
        id: work.digitalWorkId, // 使用digitalWorkId作为id
        name: workName, // 作品名称
        externalId: work.externalId, // 外部ID，用于查询视频信息
        digitalWorkId: work.digitalWorkId.toString(), // 确保为字符串类型
        title: work.title, // 保留原始title字段
        body: work.body, // 保留原始body字段
        status: normalizedStatus,
        errorMessage: work.failureReason || work.failure_reason || work.errorMessage,
        createTime: work.createTime // 保留创建时间
      };
    });

    console.log('获取作品列表成功:', works.length, '个作品');
    return {
      records: works,
      hasNext: responseData.hasNext,
      nextCursorId: responseData.nextCursorId
    };
  } catch (error) {
    console.error('获取用户作品列表失败:', error);
    throw error;
  }
};

// 删除用户作品
/**
 * 修改作品标题
 * 使用幂等令牌确保重复请求的安全性，防止意外的重复修改操作
 * 更新指定作品的标题信息
 */
export const updateWorkTitle = async (digitalWorkId: string, title: string): Promise<void> => {
  try {
    // 生成幂等令牌，确保重复请求的幂等性
    // 使用uuid库生成UUID，兼容性更好
    const idempotencyToken = uuidv4();
    
    const requestData = {
      digitalWorkId: parseInt(digitalWorkId), // 转换为数字类型
      title: title
    };

    // 使用authenticatedRequest发送请求，传入幂等令牌
    await authenticatedRequest('/digital-human/work/update-title', {
      method: 'PUT',
      body: JSON.stringify(requestData)
    }, 0, idempotencyToken);

    console.log(`成功修改作品${digitalWorkId}的标题，长度: ${title.length}`);
  } catch (error) {
    console.error(`修改作品${digitalWorkId}标题失败:`, error);
    throw error;
  }
};

/**
 * 删除用户作品
 * 使用幂等令牌确保重复请求的安全性，防止意外的重复删除操作
 * @param workId 作品ID
 * @returns 删除是否成功
 */
export const deleteUserWork = async (workId: string): Promise<boolean> => {
  try {
    // 生成幂等令牌，确保重复请求的幂等性
    // 使用uuid库生成UUID，兼容性更好
    const idempotencyToken = uuidv4();
    
    const url = `/digital-human/work/${workId}/delete`;
    
    // 使用authenticatedRequest发送请求，传入幂等令牌
    await authenticatedRequest(url, {
      method: 'POST'
    }, 0, idempotencyToken);

    console.log(`成功删除作品: ${workId}`);
    return true;
  } catch (error) {
    console.error(`删除用户作品${workId}失败:`, error);
    throw error;
  }
};

// 视频详细信息接口
export interface VideoInfo {
  id: string;
  taskId: string;
  name: string;
  videoUrl: string;
  coverUrl?: string;
  thumbnailUrl?: string;
  duration?: string | number;
  width?: number;
  height?: number;
  status: 'pending' | 'processing' | 'completed' | 'suc' | 'failed' | 'fail';
  progress?: number;
  createTime: string; // 创建时间，从API的created字段映射而来
  updateTime?: string; // 更新时间，从API的lastUpdateTime字段映射而来
  errorMessage?: string;
  resultUrl?: string;
  created?: string; // API原始字段
  lastUpdateTime?: string; // API原始字段
}

// 获取视频信息响应接口
export interface VideoInfoResponse {
  code: number;
  message: string;
  data: VideoInfo;
  ok: boolean;
}

/**
 * 根据视频ID获取视频详细信息
 * 将API返回的created和lastUpdateTime字段映射到createTime和updateTime
 */
export const getVideoInfo = async (videoId: string): Promise<VideoInfo> => {
  try {
    const result = await getVideoComposeResult(videoId);

    // 确保返回的数据包含必要的字段，正确映射日期字段
    const videoInfo: VideoInfo = {
      id: videoId,
      taskId: result.taskId || videoId,
      name: result.name || `视频_${videoId}`,
      videoUrl: result.resultUrl || result.videoUrl || '',
      coverUrl: result.coverUrl,
      thumbnailUrl: result.thumbnailUrl,
      duration: result.duration,
      width: result.width,
      height: result.height,
      status: result.status === 'ing' ? 'processing' : result.status,
      progress: result.progress,
      // 将API的created字段映射到createTime
      createTime: result.created || result.createTime || new Date().toISOString(),
      // 将API的lastUpdateTime字段映射到updateTime
      updateTime: result.lastUpdateTime || result.updateTime,
      errorMessage: result.errorMessage || result.statusDesc,
      // 保留原始字段便于调试
      created: result.created,
      lastUpdateTime: result.lastUpdateTime
    };

    console.log('获取视频信息成功，日期字段映射:', {
      videoId,
      created: result.created,
      lastUpdateTime: result.lastUpdateTime,
      mappedCreateTime: videoInfo.createTime,
      mappedUpdateTime: videoInfo.updateTime
    });

    return videoInfo;
  } catch (error) {
    console.error('获取视频信息失败:', error);
    throw error;
  }
};

/**
 * 下载视频文件到本地
 * 使用 fetch API 获取视频文件的 blob 数据，然后创建临时下载链接
 * 这种方法可以强制浏览器下载文件而不是播放
 * @param videoUrl 视频文件的URL地址
 * @param filename 下载文件的名称，默认为'video.mp4'
 */
export const downloadVideo = async (videoUrl: string, filename: string = 'video.mp4') => {
  try {
    // 显示下载开始提示
    console.log('开始下载视频文件...');
    
    // 使用 fetch API 获取视频文件
    const response = await fetch(videoUrl);
    
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status} ${response.statusText}`);
    }
    
    // 获取文件的 blob 数据
    const blob = await response.blob();
    
    // 创建一个新的 blob，明确设置 MIME 类型为 video/mp4
    const videoBlob = new Blob([blob], { type: 'video/mp4' });
    
    // 创建临时 URL
    const blobUrl = URL.createObjectURL(videoBlob);
    
    // 创建下载链接
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = filename;
    
    // 设置样式使链接不可见
    link.style.display = 'none';
    
    // 添加到 DOM 并触发点击
    document.body.appendChild(link);
    link.click();
    
    // 清理：移除链接并释放 blob URL
    document.body.removeChild(link);
    
    // 延迟释放 URL，确保下载完成
    setTimeout(() => {
      URL.revokeObjectURL(blobUrl);
      console.log('视频下载完成，临时URL已清理');
    }, 1000);
    
  } catch (error) {
    console.error('视频下载失败:', error);
    
    // 如果 blob 下载失败，回退到传统方法
    console.log('尝试使用传统下载方法...');
    const link = document.createElement('a');
    link.href = videoUrl;
    link.download = filename;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }
};

// 获取我的模型列表
export const fetchMyModels = async (lastCursorId?: string, pageSize: number = 100): Promise<import('../types').DigitalHuman[]> => {
  try {
    const query = new URLSearchParams();
    if (lastCursorId) query.append('lastCursorId', String(lastCursorId));
    query.append('pageSize', String(pageSize));

    const payload = await backendDataRequest<unknown>(
      `/digital-human/model/index?${query.toString()}`,
      { method: 'GET' },
      { auth: true, idempotent: false },
    );
    const payloadObject = payload && typeof payload === 'object'
      ? payload as { data?: unknown; records?: unknown }
      : {};
    const records = Array.isArray(payload)
      ? payload
      : Array.isArray(payloadObject.data)
        ? payloadObject.data
        : Array.isArray(payloadObject.records)
          ? payloadObject.records
          : [];

    const cleanUrl = (u?: unknown) => (u || '').toString().trim().replace(/^`+|`+$/g, '').replace(/^"+|"+$/g, '');

    const myModels = records.map((rawItem: unknown) => {
      const item = rawItem && typeof rawItem === 'object'
        ? rawItem as Record<string, unknown>
        : {};
      const id = String(item.id ?? item.speakerId ?? item.modelId ?? '');
      const name = String(item.modelName ?? item.speakerName ?? item.name ?? '未命名模型');
      const headUrl = cleanUrl(item.headUrl ?? item.avatar ?? item.modelUrl);
      const speakerUrl = cleanUrl(item.modelUrl ?? item.speakerUrl);
      const gender = (item.gender ?? '').toString();
      const magicAudioId = (item.magicAudioId ?? '').toString();
      const speakerWidth = Number(item.modelWidth ?? item.speakerWidth ?? item.width ?? 0) || undefined;
      const speakerHeight = Number(item.modelHeight ?? item.speakerHeight ?? item.height ?? 0) || undefined;

      return {
        id,
        name,
        avatar: headUrl || 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjNEE1NTY4Ii8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iNDAiIHI9IjE1IiBmaWxsPSIjNkI3Mjg0Ii8+CjxwYXRoIGQ9Ik0yNSA3NUMyNSA2NS4zMzUgMzMuMzM1IDU3IDQzIDU3SDE1N0M2Ni42NjUgNTcgNzUgNjUuMzM1IDc1IDc1VjgwSDI1Vjc1WiIgZmlsbD0iIzZCNzI4NCIvPgo8L3N2Zz4K',
        category: getCategoryFromGender(gender, name),
        modelUrl: speakerUrl || `/models/${id}.glb`,
        speakerUrl,
        speakerId: item.speakerId ? String(item.speakerId) : String(item.modelId ?? ''),
        gender,
        magicAudioId,
        speakerDemoUrl: cleanUrl(item.modelDemoUrl ?? item.speakerDemoUrl) || undefined,
        speakerWidth,
        speakerHeight
      } as import('../types').DigitalHuman;
    });

    return myModels;
  } catch (error) {
    console.error('获取我的模型数据失败:', error);
    return [];
  }
};

// 刷新我的模型数据
export const refreshMyModels = async (): Promise<import('../types').DigitalHuman[]> => {
  return await fetchMyModels();
};

// ==================== 设计草稿相关接口 ====================

// 设计草稿响应接口
export interface TemplateResponse {
  data: Template[];
  hasNext: boolean;
  nextCursorId: string | null;
}

/**
 * 创建设计草稿
 * 使用幂等令牌确保重复请求的安全性，防止意外的重复创建操作
 * 保存用户的设计草稿到服务器
 */
export const createTemplate = async (title: string, body: string): Promise<void> => {
  try {
    // 生成幂等令牌，确保重复请求的幂等性
    // 使用uuid库生成UUID，兼容性更好
    const idempotencyToken = uuidv4();
    
    const requestData = {
      title: title,
      body: body
    };

    // 使用authenticatedRequest发送请求，传入幂等令牌
    await authenticatedRequest('/digital-human/template/create', {
      method: 'POST',
      body: JSON.stringify(requestData)
    }, 0, idempotencyToken);

    console.log('设计草稿创建成功，标题长度:', title.length);
  } catch (error) {
    console.error('创建设计草稿失败:', error);
    throw error;
  }
};

/**
 * 获取用户设计草稿列表
 * 支持游标分页查询用户的所有设计草稿
 */
export const getUserTemplates = async (lastCursorId?: string, size: number = 20): Promise<{ templates: Template[]; hasNext: boolean; nextCursorId: string | null }> => {
  try {
    // 构建查询参数
    const queryParams = new URLSearchParams();
    if (lastCursorId) {
      queryParams.append('lastCursorId', lastCursorId);
    }
    queryParams.append('size', size.toString());

    const url = `/digital-human/template/user?${queryParams.toString()}`;
    
    const response = await authenticatedRequest<TemplateResponse>(url, {
      method: 'GET'
    });

    console.log('获取设计草稿列表成功，记录数:', response.data.data?.length ?? 0);
    // 根据API响应格式映射数据结构：response.data就是TemplateResponse类型
    const result = {
         templates: response.data.data,
         hasNext: response.data.hasNext,
         nextCursorId: response.data.nextCursorId
       };
    console.log('设计草稿列表映射完成，记录数:', result.templates?.length ?? 0);
    return result;
  } catch (error) {
    console.error('获取用户设计草稿列表失败:', error);
    throw error;
  }
};

/**
 * 修改设计草稿标题
 * 使用幂等令牌确保重复请求的安全性，防止意外的重复修改操作
 * 更新指定设计草稿的标题信息
 */
export const updateTemplateTitle = async (digitalTemplateId: string, title: string): Promise<void> => {
  try {
    // 生成幂等令牌，确保重复请求的幂等性
    // 使用uuid库生成UUID，兼容性更好
    const idempotencyToken = uuidv4();
    
    const requestData = {
      digitalTemplateId: parseInt(digitalTemplateId), // 转换为数字类型
      title: title
    };

    // 使用authenticatedRequest发送请求，传入幂等令牌
    await authenticatedRequest('/digital-human/template/update-title', {
      method: 'PUT',
      body: JSON.stringify(requestData)
    }, 0, idempotencyToken);

    console.log(`成功修改设计草稿${digitalTemplateId}的标题，长度: ${title.length}`);
  } catch (error) {
    console.error(`修改设计草稿${digitalTemplateId}标题失败:`, error);
    throw error;
  }
};

/**
 * 删除设计草稿
 * 使用幂等令牌确保重复请求的安全性，防止意外的重复删除操作
 * 使用新的后端接口删除指定设计草稿
 */
export const deleteTemplate = async (templateId: string): Promise<boolean> => {
  try {
    // 生成幂等令牌，确保重复请求的幂等性
    // 使用uuid库生成UUID，兼容性更好
    const idempotencyToken = uuidv4();
    
    const url = `/digital-human/template/${templateId}/delete`;
    
    // 使用authenticatedRequest发送请求，传入幂等令牌
    await authenticatedRequest(url, {
      method: 'POST'
    }, 0, idempotencyToken);

    console.log(`成功删除设计草稿: ${templateId}`);
    return true;
  } catch (error) {
    console.error(`删除设计草稿${templateId}失败:`, error);
    throw error;
  }
};
