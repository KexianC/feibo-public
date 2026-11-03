// 用户作品接口（从digitalHumanApi导入）
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

// 数字人角色类型
export interface DigitalHuman {
  id: string;
  name: string;
  avatar: string; // headUrl - 用于右侧库显示的头像
  category: 'business' | 'education' | 'entertainment';
  modelUrl: string;
  speakerUrl?: string; // 完整数字人图像URL - 用于编辑区显示
  speakerId?: string;
  gender?: string;
  magicAudioId?: string;
  speakerDemoUrl?: string;
  speakerWidth?: number; // 数字人原始宽度
  speakerHeight?: number; // 数字人原始高度
}

// 场景元素类型
export type ElementType = 
  | 'digital-human' 
  | 'text' 
  | 'image' 
  | 'shape'
  | 'background'
  | 'title'
  | 'illustration'
  | 'voice'
  | 'video'
  | 'music'
  | 'flash-text'
  | 'subtitle';

export interface SceneElement {
  id: string;
  type: ElementType;
  position: { x: number; y: number };
  size: { width: number; height: number };
  rotation: number;
  zIndex: number;
  properties: Record<string, any>;
}

// 背景元素属性
export interface BackgroundProperties {
  backgroundType: 'color' | 'image' | 'video';
  color?: string;
  imageUrl?: string;
  videoUrl?: string;
  opacity?: number;
}

// 标题元素属性
export interface TitleProperties {
  text: string;
  fontSize: number;
  fontFamily: string;
  color: string;
  fontWeight: 'normal' | 'bold' | '100' | '200' | '300' | '400' | '500' | '600' | '700' | '800' | '900';
  fontStyle?: 'normal' | 'italic';
  textAlign: 'left' | 'center' | 'right';
  opacity?: number;
  backgroundColor?: string;
  borderRadius?: number;
  padding?: number;
  shadow?: {
    offsetX: number;
    offsetY: number;
    blur: number;
    color: string;
  };
}

// 插图元素属性
export interface IllustrationProperties {
  imageUrl: string;
  alt?: string;
  opacity?: number;
  borderRadius?: number;
  filter?: string;
}

// 配音元素属性
export interface VoiceProperties {
  audioUrl: string;
  text: string;
  voiceId?: string;
  speed?: number;
  pitch?: number;
  volume?: number;
  startTime?: number;
  duration?: number;
}

// 视频元素属性
export interface VideoProperties {
  videoUrl: string;
  autoplay?: boolean;
  loop?: boolean;
  muted?: boolean;
  controls?: boolean;
  startTime?: number;
  endTime?: number;
}

// 音乐元素属性
export interface MusicProperties {
  audioUrl: string;
  title?: string;
  artist?: string;
  volume?: number;
  loop?: boolean;
  fadeIn?: number;
  fadeOut?: number;
  startTime?: number;
  duration?: number;
}

// 闪文元素属性
export interface FlashTextProperties {
  text: string;
  fontSize: number;
  color: string;
  animationType: 'fade' | 'slide' | 'bounce' | 'flash' | 'typewriter';
  duration: number;
  delay?: number;
  repeat?: boolean;
}


// 页面字幕配置
export interface PageSubtitleConfig {
  isEnabled: boolean;
  contentType: string;
  primaryColour: string;
  fontname: string;
  bold: string;
  subtitleUsepx: string;
  fontsize: string;
  underline: number;
  italic: number;
  bottomSpace: number;
  wordSpace: number;
}

// 页面类型
export interface Page {
  id: string;
  name: string;
  elements: SceneElement[];
  duration: number;
  audioUrl?: string;
  script?: string;
  aspectRatio: '16:9' | '9:16'; // 视频比例
  subtitleConfig?: PageSubtitleConfig; // 字幕配置
  voiceId?: string; // 页面绑定的配音ID（magicSpeakerId 或 语音库ID）
  voiceSpeed?: number; // 页面绑定的语速（0.5-2.0）
  voiceVolume?: number; // 页面绑定的音量（0-100）
}

// 项目类型
export interface Project {
  id: string;
  name: string;
  pages: Page[];
  currentPageId: string;
  createdAt: Date;
  updatedAt: Date;
}

// 设计草稿类型
export interface DesignDraft {
  id: string;
  name: string;
  thumbnail?: string; // 缩略图URL
  elements: SceneElement[];
  aspectRatio: '16:9' | '9:16';
  script?: string;
  createdAt: Date;
  updatedAt: Date;
}

// 设计模板类型（新API）
export interface Template {
  digitalTemplateId: string;
  title: string;
  body: string;
  createTime: string;
  updateTime?: string;
}

// 注意：TemplateResponse接口已移至digitalHumanApi.ts中定义

// 主题类型
export type ThemeType = 'classic' | 'dark';

// 应用状态类型
export interface AppState {
  // 项目数据
  currentProject: Project | null;
  
  // UI状态
  selectedElementId: string | null;
  isPlaying: boolean;
  currentTime: number;
  
  // 主题状态
  currentTheme: ThemeType;
  
  // 数字人库
  digitalHumans: DigitalHuman[];
  selectedCategory: string;
  isLoadingDigitalHumans: boolean;
  digitalHumansError: string | null;
  
  // 我的模型
  myModels: DigitalHuman[];
  isLoadingMyModels: boolean;
  myModelsError: string | null;
  
  // 发音人选择
  selectedVoice: string | null; // 选中的发音人ID
  
  // 语音参数
  voiceSpeed: number; // 语速，范围0.5-2.0，默认1.0
  voiceVolume: number; // 音量，范围0-100，默认100
  
  // 设计草稿
  designDrafts: DesignDraft[];
  
  // 用户作品
  userWorks: UserWork[];
  
  // 编辑状态
  history: Project[];
  historyIndex: number;
  
  // 操作方法
  actions: {
    saveToHistory: (project: Project) => void;
    loadProject: (project: Project) => void;
    addPage: (aspectRatio?: '16:9' | '9:16') => string | null;
    deletePage: (pageId: string) => void;
    switchPage: (pageId: string) => void;
    duplicatePage: (pageId: string) => void;
    renamePage: (pageId: string, newName: string) => void;
    updatePageScript: (script: string) => void;
    updatePageSubtitleConfig: (subtitleConfig: PageSubtitleConfig) => void;
    getNextZIndex: (elementType?: string) => number;
    addElement: (element: SceneElement) => void;
    updateElement: (elementId: string, updates: Partial<SceneElement>) => void;
    deleteElement: (elementId: string) => void;
    deleteElementsByType: (elementType: string) => void;
    selectElement: (elementId: string | null) => void;
    undo: () => void;
    redo: () => void;
    loadDigitalHumans: () => Promise<void>;
    refreshDigitalHumans: () => Promise<void>;
    // 我的模型管理
    loadMyModels: () => Promise<void>;
    refreshMyModels: () => Promise<void>;
    setMyModels: (models: DigitalHuman[]) => void;
    // 设计草稿操作
    saveDesignDraft: (name: string) => void;
    loadDesignDraft: (draftId: string) => void;
    deleteDesignDraft: (draftId: string) => void;
    loadDesignDrafts: () => void;
    // 应用状态管理
    initializeAppState: () => void;
    clearAllData: () => void;
    // 主题管理
    setTheme: (theme: ThemeType) => void;
    toggleTheme: () => void;
    // 发音人管理
    setSelectedVoice: (voiceId: string | null) => void;
    // 语音参数管理
    setVoiceSpeed: (speed: number) => void;
    setVoiceVolume: (volume: number) => void;
    // 用户作品管理
    saveUserWork: (work: UserWork) => void;
    loadUserWorks: () => void;
    updateUserWork: (taskId: string, updates: Partial<UserWork>) => void;
    deleteUserWork: (taskId: string) => void;
  };
}

// 用户认证接口（更新为符合新API的结构）
export interface User {
  userId: string;
  nickName: string;
  avatar: string;
  phoneNumber: string;
  gender: string;
  accessToken?: string;
  refreshToken?: string;
  tokenHead?: string;
  loginTime: Date;
  // 兼容旧字段
  username?: string;
  token?: string;
  email?: string;
  roles?: Array<{
    roleId?: number | string;
    name?: string;
    description?: string;
  }>;
  permissions?: string[];
  role?: string;
  roleName?: string;
  roleCode?: string;
  authority?: string;
  isAdmin?: boolean;
}

// 用户注册数据
export interface RegisterData {
  nickName: string;
  password: string;
  phoneNumber: string;
  gender: 'male' | 'female' | 'UnKnow';
}

// 用户登录数据
export interface LoginData {
  phoneNumber: string;
  password: string;
}

// 存储键名
export const STORAGE_KEYS = {
  PROJECTS: 'digital-human-projects',
  CURRENT_PROJECT: 'current-project-id',
  USER_PREFERENCES: 'user-preferences',
  DESIGN_DRAFTS: 'design-drafts',
  USER_WORKS: 'user-works',
  USER_AUTH: 'user-auth',
  APP_STATE: 'app-state'
};

// 存储的数据结构
export interface StoredData {
  projects: Project[];
  currentProjectId: string | null;
  userPreferences: {
    theme: 'dark' | 'light';
    autoSave: boolean;
    gridVisible: boolean;
  };
}

// 支付相关类型定义
// AI功能模板
export interface AiFeatureTemplate {
  templateCode: string;
  name: string;
  description: string;
}

// 会员套餐功能
export interface MembershipFeature {
  aiFeatureId: string; // 改为字符串避免精度问题
  aiFeatureTemplate: AiFeatureTemplate;
  value: string; // 改为字符串避免精度问题
}

// 会员套餐
export interface MembershipPlan {
  aiMembershipPlanId: string; // 改为字符串避免精度问题
  planCode: string;
  name: string;
  price: string; // 改为字符串避免精度问题
  durationDays: number;
  features: MembershipFeature[];
}

// 充值包
export interface RechargePack {
  aiRechargePackId: string; // 改为字符串避免精度问题
  packCode: string;
  name: string;
  price: string; // 改为字符串避免精度问题
  aiFeatureTemplateVO: AiFeatureTemplate;
  value: string; // 改为字符串避免精度问题
  description: string;
}

// 商品列表响应
export interface ProductListResponse {
  membershipPlans: MembershipPlan[];
  rechargePacks: RechargePack[];
}

// 锁定订单请求参数
export interface LockOrderRequest {
  productId: string; // 商品ID - 改为字符串避免精度问题
  productType: boolean; // false=会员套餐, true=充值包
}

// 锁定订单响应
export interface LockOrderResponse {
  url: string; // 支付链接
  trxId: string; // 交易ID
  orderId: string; // 订单ID - 改为字符串避免精度问题
}

// 支持的文件类型
export interface FileTypes {
  audio: ['.mp3', '.wav', '.m4a'];
  image: ['.jpg', '.jpeg', '.png', '.gif'];
  video: ['.mp4', '.webm'];
}

// 文件上传处理
export interface FileHandler {
  uploadAudio: (file: File) => Promise<string>;
  uploadImage: (file: File) => Promise<string>;
  exportVideo: (project: Project) => Promise<Blob>;
}

// 应用模板类型
export type TemplateCategory = 'business' | 'education' | 'product' | 'news' | 'entertainment';

export interface ApplicationTemplate {
  id: string;
  name: string;
  description: string;
  category: TemplateCategory;
  thumbnail: string; // 模板预览图
  aspectRatio: '16:9' | '9:16';
  elements: SceneElement[]; // 预设的元素配置
  script?: string; // 预设文案
  tags: string[]; // 标签，用于搜索和分类
  createdAt: Date;
  isPopular?: boolean; // 是否为热门模板
}
