import type { DigitalHuman, Template } from '@/types';

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

export interface TextContent {
  content: string;
  magicSpeakerId: string;
  srtLen?: number;
  audioType?: string;
  convert?: string;
  mergeSymbol?: boolean;
  pitch?: number;
  rate?: number;
  speed?: number;
  volume?: number;
}

export interface VideoComposeDetail {
  contentType: 'avatar' | 'video' | 'text' | 'tag' | 'subtitles' | 'back_audio' | 'audio';
  sortNum?: number;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  content?: string;
  text?: TextContent;
  wetaSpeakerId?: string;
  magicSpeakerId?: string;
  startTime?: number;
  stopTime?: number;
  fontname?: string;
  bold?: number | string;
  fontsize?: number | string;
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
  subtitleUsepx?: string;
  bottomSpace?: number;
  wordSpace?: number;
}

export interface VideoComposeResponse {
  code: string | number;
  message?: string;
  info?: string;
  data: string;
  ok?: boolean;
}

export interface VideoComposeResultResponse {
  code: number;
  message: string;
  data: {
    taskId: string;
    id?: string;
    status: 'pending' | 'processing' | 'completed' | 'suc' | 'failed' | 'fail' | 'ing';
    statusDesc?: string;
    progress?: number;
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
  [key: string]: unknown;
}

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

export interface MyModelsIndexResponse {
  data?: DigitalHuman[];
  records?: DigitalHuman[];
  hasNext?: boolean;
  nextCursorId?: string | null;
}

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
  digitalWorkId: string;
  title?: string | null;
  body: string;
  externalId: string;
}

export interface UserWorksResponse {
  code: string;
  info: string;
  data: {
    data: UserWork[];
    hasNext: boolean;
    nextCursorId: string | null;
  };
}

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
  createTime: string;
  updateTime?: string;
  errorMessage?: string;
  resultUrl?: string;
  created?: string;
  lastUpdateTime?: string;
}

export interface VideoInfoResponse {
  code: number;
  message: string;
  data: VideoInfo;
  ok: boolean;
}

export interface TemplateResponse {
  data: Template[];
  hasNext: boolean;
  nextCursorId: string | null;
}
