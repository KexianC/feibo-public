import React, { useState, useRef, useEffect } from 'react';
import { Send, User, Bot, Plus, Trash2, Download, MessageCircle, Edit2, X, RefreshCw, ChevronLeft, ChevronRight, Square, Mic, MicOff, Loader2, Sparkles, Clock3, ShieldCheck, Volume2, VolumeX, Pause, Play, RotateCcw } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import AgentLibrary from './AgentLibrary';
import RechargeModal from './RechargeModal';
import CachedAvatar from './CachedAvatar';
import { getCurrentUser } from '../services/authApi';
import { API_CONFIG } from '../config/api';
import { useAuthGuard } from '../hooks/useAuthGuard';
import { authenticatedRawRequest } from '../services/http/authenticatedRawRequest';
import {
  AuthSessionExpiredError,
  authSession,
  isSessionExpiredCode,
} from '../services/authSession';
import { useVoiceInput } from '../hooks/useVoiceInput';
import { useTtsPlayback } from '../hooks/useTtsPlayback';
import { 
  getAllConversations, 
  getAllConversationMessages, 
  getConversationsWithPagination,
  loadMoreConversations,
  convertConversationToInternal, 
  convertMessageToInternal,
  updateConversationTitle,
  deleteConversation,
  ConversationItem,
  MessageItem 
} from '../services/aiConversationApi';

// 手机号模糊处理函数
const maskPhoneNumber = (phoneNumber: string): string => {
  if (!phoneNumber || phoneNumber.length < 7) {
    return phoneNumber;
  }
  
  // 如果是11位手机号，显示前3位和后4位，中间用****替换
  if (phoneNumber.length === 11) {
    return phoneNumber.substring(0, 3) + '****' + phoneNumber.substring(7);
  }
  
  // 其他长度的号码，显示前3位和后2位，中间用****替换
  if (phoneNumber.length > 5) {
    return phoneNumber.substring(0, 3) + '****' + phoneNumber.substring(phoneNumber.length - 2);
  }
  
  // 太短的号码不做处理
  return phoneNumber;
};

interface Message {
  id: string;
  content: string;
  sender: 'user' | 'ai';
  timestamp: Date;
  agentName?: string;
  createTime?: string; // 服务器返回的相对时间字符串
}

interface ChatItem {
  id: string;
  title: string;
  timestamp: string;
  updatedAt?: string;
  active: boolean;
  messages: Message[];
  agentId?: string;
  modelType?: string; // 模型类型：字符串格式避免精度丢失
  isNewChat?: boolean; // 标记是否为新创建的对话
}

/**
 * 智能体接口定义
 */
interface Agent {
  id: string;
  name: string;
  avatar: string;
  description: string;
  category: 'general' | 'vertical';
  specialty?: string;
  aiModelConfigId?: string; // AI模型配置ID - 使用字符串避免大整数精度丢失
  parentName?: string; // 父级模型名称（用于子版本显示模型系列名）
}

const resolveModelConfigId = (...candidates: Array<string | undefined>): string | undefined => {
  for (const candidate of candidates) {
    const normalized = candidate?.trim();
    if (normalized && /^[1-9]\d*$/.test(normalized)) {
      return normalized;
    }
  }
  return undefined;
};

const ZhibiYiyan: React.FC = () => {
  // 登录状态检测 - 必须在组件顶层调用
  const { isAuthenticated, isChecking } = useAuthGuard({
    autoRedirect: true,
    checkInterval: 30000, // 30秒检查一次
  });

  const [inputValue, setInputValue] = useState('');
  const [selectedAgent, setSelectedAgent] = useState<Agent | null>(null);
  const [userInfo, setUserInfo] = useState<any>(() => {
    try {
      return getCurrentUser();
    } catch (error) {
      console.error('获取用户信息失败:', error);
      return null;
    }
  });
  const [chatList, setChatList] = useState<ChatItem[]>([
    {
      id: Date.now().toString(), // 使用时间戳作为本地对话ID
      title: 'New Chat',
      timestamp: '刚刚',
      updatedAt: new Date().toISOString(),
      active: true,
      messages: [],
      agentId: undefined,
      isNewChat: true // 确保默认的New Chat也排在历史对话前面
    }
  ]);
  const [isLoading, setIsLoading] = useState(false);
  const [isSending, setIsSending] = useState(false); // 跟踪是否正在发送消息
  const activeResponseControllerRef = useRef<AbortController | null>(null);
  const activeResponseReaderRef = useRef<ReadableStreamDefaultReader<Uint8Array> | null>(null);
  const responseRunIdRef = useRef(0);

  const {
    phase: voiceInputPhase,
    notice: voiceInputNotice,
    remainingSeconds: voiceRemainingSeconds,
    connected: isConnected,
    toggle: toggleVoiceCapture,
    stop: stopVoiceInput,
    edit: editVoiceInput,
    clearNotice: clearVoiceInputNotice,
  } = useVoiceInput({ value: inputValue, onChange: setInputValue, disabled: isSending || isLoading });
  const isRecording = voiceInputPhase === 'recording';
  const isRecognizing = voiceInputPhase === 'starting';
  const isAwaitingAsrCompletion = voiceInputPhase === 'finishing';

  const [showRechargeModal, setShowRechargeModal] = useState(false);
  const [isLoadingConversations, setIsLoadingConversations] = useState(false);
  const [serverConversations, setServerConversations] = useState<ConversationItem[]>([]);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [conversationPagination, setConversationPagination] = useState<{
    hasNext: boolean;
    nextCursorId: number | null;
    isLoadingMore: boolean;
  }>({ hasNext: false, nextCursorId: null, isLoadingMore: false });
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [leftSidebarVisible, setLeftSidebarVisible] = useState(true);
  const [rightSidebarVisible, setRightSidebarVisible] = useState(true);
  const [isAllHistoryEnabled, setIsAllHistoryEnabled] = useState(true);
  const [voiceDialogueEnabled, setVoiceDialogueEnabled] = useState(false);
  const voiceDialogueEnabledRef = useRef(false);
  const {
    status: ttsStatus,
    currentSegment: currentTtsSegment,
    totalSegments: totalTtsSegments,
    error: ttsError,
    startStream: startTtsStream,
    enqueueStream: enqueueTtsStream,
    finishStream: finishTtsStream,
    stop: stopTts,
    unlock: unlockTts,
    speak: speakTts,
    pause: pauseTts,
    resume: resumeTts,
    retry: retryTts,
    canPause: canPauseTts,
    canResume: canResumeTts,
    canRetry: canRetryTts,
    playbackRate,
    setPlaybackRate,
  } = useTtsPlayback();
  const ttsContextVersionRef = useRef(0);
  const cancelTts = () => {
    ttsContextVersionRef.current += 1;
    stopTts();
  };
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const currentAiMessageRef = useRef<string>('');
  const currentThoughtContentRef = useRef<string>(''); // 单独管理思考内容
  const abortActiveResponse = () => {
    responseRunIdRef.current += 1;
    void activeResponseReaderRef.current?.cancel('conversation-context-changed')
      .catch(() => undefined);
    activeResponseReaderRef.current = null;
    activeResponseControllerRef.current?.abort();
    activeResponseControllerRef.current = null;
    setIsSending(false);
    setIsLoading(false);
    currentAiMessageRef.current = '';
    currentThoughtContentRef.current = '';
  };
  const cancelActiveInteraction = () => {
    stopVoiceInput(true);
    clearVoiceInputNotice();
    cancelTts();
    abortActiveResponse();
  };
  const firstReplyShownRef = useRef<boolean>(false);

  const normalizeMarkdownContent = (rawContent: string) => {
    if (!rawContent) return '';

    return rawContent
      .replace(/\r\n/g, '\n')
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/&nbsp;/gi, ' ')
      .replace(/~~\s*([^~]+?)\s*~~/g, '$1')
      .replace(/(\d+)\s*\n+\s*(\d+\s*(?:小时|分钟|天|年|月|次|ml|mL|%))/g, '$1-$2')
      .replace(/(^|\n)-(?=\S)/g, '$1- ')
      .replace(/^\s*\|[-\s|:]+\|\s*$/gm, '\n---\n')
      .replace(/^\s*\|-{6,}\|\s*$/gm, '\n---\n')
      .replace(/^[-|]{8,}$/gm, '---')
      .replace(/^\|?\s*([^|\n]{1,16})\s*\|\s*([^|\n]+?)\s*\|?\s*$/gm, (_match, label, value) => {
        const cleanLabel = String(label).trim();
        const cleanValue = String(value).trim();
        if (!cleanLabel || /^-+$/.test(cleanLabel) || /^-+$/.test(cleanValue)) return '';
        return `**${cleanLabel}**：${cleanValue}`;
      })
      .replace(/(^|\n)\s*\|\s*(?=\d+[.、]|\S+[:：])/g, '\n')
      .replace(/\|\s*$/gm, '')
      .replace(/\*\*\s*([^*\n]+?)\s*\n\s*([^*\n]+?)\s*\*\*/g, '**$1$2**')
      .replace(/^(#)\s+(.{24,})$/gm, '## $2')
      .replace(/(常见检查)(以下项目)/g, '$1\n\n$2')
      .replace(/(检查项目)(及注意事项|通常|一般|如下)/g, '$1\n\n$2')
      .replace(/([^\n])(\s*[✅❓💧⚠️📌🔹🔸]\s*)/gu, '$1\n\n$2')
      .replace(/(^|\n)\s*(\d+[.、])\s*/g, '$1$2 ')
      .replace(/(^|\n)(\d+[.、]\s*[^\n]+)\n(?=\S)/g, '$1$2\n\n')
      .replace(/(^|\n)(步骤|序号|项目|说明)\s*\|/g, '$1| $2 |')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
  };
  
  // 获取模型类型
  const getModelType = () => {
    if (!selectedAgent) return 0; // 通用模型
    switch (selectedAgent.category) {
      case 'general': return 0;
      case 'vertical': return 1;
      default: return 0;
    }
  };

  // 获取显示名称（模型系列名或原名称）
  const getDisplayName = (agent: Agent | null) => {
    if (!agent) return '通用助手';
    // 如果是通用模型的子版本且有父级名称，显示父级名称
    if (agent.category === 'general' && agent.parentName) {
      return agent.parentName;
    }
    // 否则显示原名称
    return agent.name;
  };

  
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };
  
  useEffect(() => {
    scrollToBottom();
  }, [chatList]);
  
  // 监听AI消息内容变化，自动滚动到底部
  useEffect(() => {
    if (currentAiMessageRef.current) {
      scrollToBottom();
    }
  }, [currentAiMessageRef.current]);

  // 加载服务器对话列表（分页版本）
  const loadServerConversations = async () => {
    if (!userInfo) {
      return;
    }
    
    setIsLoadingConversations(true);
    try {
      const response = await getConversationsWithPagination(20);
      setServerConversations(response.data);
      setConversationPagination({
        hasNext: response.hasNext,
        nextCursorId: response.nextCursorId,
        isLoadingMore: false
      });
    } catch (error) {
      console.error('加载服务器对话列表失败:', error);
      // 如果加载失败，保持使用本地对话列表
    } finally {
      setIsLoadingConversations(false);
    }
  };
  
  // 加载更多对话
  const loadMoreServerConversations = async () => {
    if (!conversationPagination.hasNext || !conversationPagination.nextCursorId || conversationPagination.isLoadingMore) {
      return;
    }
    
    setConversationPagination(prev => ({ ...prev, isLoadingMore: true }));
    try {
      const response = await loadMoreConversations(conversationPagination.nextCursorId, 20);
      setServerConversations(prev => [...prev, ...response.data]);
      setConversationPagination({
        hasNext: response.hasNext,
        nextCursorId: response.nextCursorId,
        isLoadingMore: false
      });
    } catch (error) {
      console.error('加载更多对话失败:', error);
      setConversationPagination(prev => ({ ...prev, isLoadingMore: false }));
    }
  };
  
  // 用户登录后自动加载对话列表
  useEffect(() => {
    if (userInfo) {
      loadServerConversations();
    }
  }, [userInfo]);
  
  // 加载指定对话的消息记录
  const loadConversationMessages = async (conversationId: string) => {
    setIsLoadingMessages(true);
    try {
      const messages = await getAllConversationMessages(conversationId);
      // 直接按服务器返回的data顺序倒序排列，然后进行转换
      const convertedMessages = messages.reverse().map(convertMessageToInternal);
      
      // 更新对话列表中对应对话的消息
      setChatList(prev => prev.map(chat => 
        chat.id === conversationId 
          ? { ...chat, messages: convertedMessages }
          : chat
      ));
      
    } catch (error) {
      console.error(`加载对话${conversationId}的消息记录失败:`, error);
    } finally {
      setIsLoadingMessages(false);
    }
  };
  
  // 组件卸载时清理资源
  useEffect(() => {
    return () => {
      responseRunIdRef.current += 1;
      void activeResponseReaderRef.current?.cancel('component-unmounted')
        .catch(() => undefined);
      activeResponseReaderRef.current = null;
      activeResponseControllerRef.current?.abort();
      activeResponseControllerRef.current = null;
      // 清理当前AI消息内容
      currentAiMessageRef.current = '';
      currentThoughtContentRef.current = '';
    };
  }, []);
  
  const getCurrentChat = () => {
    return chatList.find(chat => chat.active);
  };
  
  const getCurrentMessages = () => {
    const currentChat = getCurrentChat();
    return currentChat?.messages || [];
  };

  /**
   * 中断AI对话的处理函数
   */
  const handleAbortConversation = () => {
    cancelActiveInteraction();
  };

  const toggleVoiceInput = async () => {
    cancelTts();
    await toggleVoiceCapture();
  };

  const handleSendMessage = async () => {
    const messageContent = inputValue.trim();
    if (!messageContent || isSending || isLoading) return;

    const aiModelConfigId = resolveModelConfigId(selectedAgent?.aiModelConfigId, selectedAgent?.id);
    if (!aiModelConfigId) {
      alert('模型配置异常，请重新选择模型或刷新页面后重试');
      return;
    }
    
    const currentChat = getCurrentChat();
    if (!currentChat) return;

    // 点击发送即结束本次语音采集，并在 AI 回复期间拒绝迟到的 ASR 回填。
    clearVoiceInputNotice();
    stopVoiceInput(true);
    setInputValue('');

    // 用户开始新一轮提问时停止上一条回复的合成或播放，避免语音重叠。
    cancelTts();
    activeResponseControllerRef.current?.abort();
    const responseRunId = responseRunIdRef.current + 1;
    responseRunIdRef.current = responseRunId;
    let replyTtsContextVersion = ttsContextVersionRef.current;
    let replyTtsStreamId = voiceDialogueEnabledRef.current
      ? startTtsStream()
      : null;
    
    setIsSending(true); // 开始发送，设置发送状态
    
    const userMessage: Message = {
      id: Date.now().toString(),
      content: messageContent,
      sender: 'user',
      timestamp: new Date()
    };
    
    const activityTime = new Date().toISOString();
    
    // 添加用户消息
    setChatList(prev => prev.map(chat => 
      chat.id === currentChat.id 
        ? { ...chat, messages: [...chat.messages, userMessage], isNewChat: false, timestamp: activityTime, updatedAt: activityTime }
        : chat
    ));
    
    setIsLoading(true);
    
    // 创建AI消息占位符（但不立即添加到消息列表）
    const aiMessageId = (Date.now() + 1).toString();
    const aiMessage: Message = {
      id: aiMessageId,
      content: '',
      sender: 'ai',
      timestamp: new Date(),
      agentName: getDisplayName(selectedAgent)
    };
    
    // 重置当前AI消息内容和思考内容
    currentAiMessageRef.current = '';
    currentThoughtContentRef.current = '';
    let replyContent = '';
    let thoughtContent = '';
    const syncResponseBuffers = () => {
      currentAiMessageRef.current = replyContent;
      currentThoughtContentRef.current = thoughtContent;
    };
    const appendThoughtContent = (value: unknown) => {
      const delta = String(value ?? '');
      if (!delta) return;
      thoughtContent += delta;
      syncResponseBuffers();
    };
    const appendReplyContent = (value: unknown, speakable = true) => {
      const delta = String(value ?? '');
      if (!delta) return;
      replyContent += delta;
      syncResponseBuffers();
      if (
        speakable
        && replyTtsStreamId !== null
        && voiceDialogueEnabledRef.current
        && replyTtsContextVersion === ttsContextVersionRef.current
        && responseRunId === responseRunIdRef.current
      ) {
        enqueueTtsStream(replyTtsStreamId, delta);
      }
    };
    const handleModelFallback = (data: string) => {
      // This control event replaces this attempt's partial answer, including audio.
      let fallbackName = 'DeepSeek';
      try {
        const payload = JSON.parse(data);
        if (typeof payload.model === 'string' && payload.model.trim()) {
          fallbackName = payload.model.trim();
        }
      } catch {
        // Never display a control payload as answer text, even if it is malformed.
      }
      replyContent = '';
      thoughtContent = '';
      syncResponseBuffers();
      firstReplyShownRef.current = false;
      setIsLoading(true);
      const resumeSpeech = replyTtsStreamId !== null
        && voiceDialogueEnabledRef.current
        && replyTtsContextVersion === ttsContextVersionRef.current;
      if (replyTtsContextVersion === ttsContextVersionRef.current) cancelTts();
      replyTtsContextVersion = ttsContextVersionRef.current;
      replyTtsStreamId = resumeSpeech ? startTtsStream() : null;
      const messageId = aiMessageId;
      setChatList(prev => prev.map(chat => ({
        ...chat,
        messages: chat.messages.map(msg => msg.id === messageId
          ? { ...msg, content: '', agentName: fallbackName }
          : msg),
      })));
    };
    const failResponse = (data: string): never => {
      // A terminal server error invalidates any partial output from this attempt.
      replyContent = '';
      thoughtContent = '';
      syncResponseBuffers();
      firstReplyShownRef.current = false;
      throw new Error(data.trim() || '模型服务暂时不可用，请稍后重试。');
    };
    firstReplyShownRef.current = false; // 重置首次回复标记
    
    try {
      // 使用fetch流式读取方式
      const url = `${API_CONFIG.BASE_URL}/api/v1/ai/symptomAnalysis`;
      
      // 处理conversationId参数
      // 如果currentConversationId不为null，说明是继续已存在的对话
      // 如果为null，说明是新对话，不传conversationId

      
      // 构建统一的AI对话接口参数

      
      const requestBody = {
        conversationId: currentConversationId, // 对话ID，新对话时为null
        content: messageContent, // 消息内容
        aiModelConfigId: aiModelConfigId, // AI模型配置ID，从选中的智能体获取
        isAllHistory: isAllHistoryEnabled // 是否包含全部历史记录
      };
      // 创建AbortController用于中断请求
      const controller = new AbortController();
      activeResponseControllerRef.current = controller;
      const streamSession = authSession.capture();
      
      // 仅在响应体交给流读取器前允许认证刷新重试；读取开始后不会自动重放。
      const response = await authenticatedRawRequest(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody),
        signal: controller.signal,
        timeoutMs: 60_000,
      }, {
        idempotent: true,
      });
      
      if (!response.ok) {
        throw new Error(`服务器错误: ${response.status} ${response.statusText}`);
      }
      
      // 保持“思考中”直到收到首条回复内容
      // 添加AI消息到列表（开始接收数据时）
      setChatList(prev => prev.map(chat => 
        chat.id === currentChat.id 
          ? { ...chat, messages: [...chat.messages, aiMessage], timestamp: activityTime, updatedAt: activityTime }
          : chat
      ));
      
      // 检查响应头中是否包含新的conversationId（用于新对话）
      const newConversationId = response.headers.get('X-Conversation-Id');
      if (newConversationId && currentConversationId === null) {
        // 如果是新对话且服务器返回了conversationId，更新本地对话ID和状态
        // 确保作为字符串处理，避免精度丢失
        const conversationIdStr = String(newConversationId);
        
        // 更新当前对话ID状态
        setCurrentConversationId(conversationIdStr);
        
        // 更新本地对话列表中的对话ID
        setChatList(prev => prev.map(chat => 
          chat.id === currentChat.id 
            ? { ...chat, id: conversationIdStr, timestamp: activityTime, updatedAt: activityTime }
            : chat
        ));
        
        // Keep this response's message ID stable while the conversation ID changes.
        
      }
      
      // 获取流式读取器
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      
      if (!reader) {
        throw new Error('无法获取响应流');
      }
      activeResponseReaderRef.current = reader;
      
      // 流式读取响应
      let buffer = '';
      // 用于标记下一行data是否为aiConversationId
      let expectingAiConversationId = false;
      // 用于标记当前处理的事件类型
      let currentEventType = '';
      
      while (true) {
        const readResult = await reader.read();
        if (responseRunIdRef.current !== responseRunId) {
          await reader.cancel().catch(() => undefined);
          if (activeResponseReaderRef.current === reader) {
            activeResponseReaderRef.current = null;
          }
          return;
        }
        try {
          authSession.assertCurrent(streamSession);
        } catch (error) {
          await reader.cancel().catch(() => undefined);
          throw error;
        }
        const { value, done } = readResult;
        if (done) {
          setIsLoading(false); // 确保流结束时关闭加载状态
          // 处理缓冲区中剩余的数据
          if (buffer.trim()) {
            if (buffer.trimStart().startsWith('data:')) {
              const data = buffer.trimStart().substring(5).replace(/^ /, '');
              if (currentEventType === 'error_message') failResponse(data);
              if (data.trim() !== '') {
                // 根据当前事件类型决定累积到哪里
                if (currentEventType === 'model_fallback') {
                  handleModelFallback(data);
                  currentEventType = '';
                  setIsLoading(false);
                } else if (currentEventType === 'thought') {
                  appendThoughtContent(data);
                } else if (currentEventType === 'reply') {
                  appendReplyContent(data);
                } else {
                  // 未声明类型的兼容数据继续显示，但不自动朗读，避免误播系统事件。
                  appendReplyContent(data, false);
                }
              }
            }
          }

          if (
            replyTtsStreamId !== null
            && replyTtsContextVersion === ttsContextVersionRef.current
            && responseRunId === responseRunIdRef.current
          ) {
            finishTtsStream(replyTtsStreamId);
          }
          
          // 流结束后进行一次性格式化处理
          let formattedContent = replyContent;
          
          // 清理末尾的conversationId数字（移除类似"1966775226239188993"的纯数字行）
          formattedContent = formattedContent
            .replace(/\n?\d{13,}\s*$/g, '') // 移除末尾的长数字ID
            .replace(/\s*\d{13,}\s*$/g, '') // 移除末尾可能带空格的长数字ID
            .trim(); // 清理末尾空白
          formattedContent = normalizeMarkdownContent(formattedContent);
          
          // 智能重组分散的Markdown内容
          formattedContent = formattedContent
            // 首先处理连续的标题标记分离（如###和####连在一起）
            .replace(/(#{3,6})(#{1,6})/g, (match, first, second) => {
              return `${first}\n\n${second}`;
            })
            // 重组分散的标题标记和内容
            .replace(/(#{1,6})\s*([^\n#]*?)\s*\n\s*([、，。：；！？\u4e00-\u9fff])/g, '$1 $2$3')
            // 重组分散的数字编号
            .replace(/(\d+)\s*\n\s*([.、])\s*/g, '$1$2 ')
            // 重组分散的列表项标记（但不处理emoji开头的新列表项）
            .replace(/(-\s*)\n\s*([^\n-\u{1F300}-\u{1F9FF}])/gu, '$1$2')
            // 重组分散的粗体标记
            .replace(/\*\*\s*([^*\n]*?)\s*\*\*/g, (match, content) => {
              const cleanContent = content.replace(/\s+/g, '');
              return cleanContent ? `**${cleanContent}**` : match;
            })
            // 重组表格分隔符
            .replace(/\|\s*\n\s*-\s*\|/g, '|-|')
            // 处理emoji后的分离内容（但保持列表项分离）
            .replace(/([\u{1F300}-\u{1F9FF}])\s*\n\s*([^-])/gu, '$1$2')
            // 处理标题标记后的分离
            .replace(/(#{1,6})\s*\n\s*([^\n#])/g, '$1 $2')
            // 重组分散的中文标点和内容
            .replace(/([、，。：；！？])\s*\n\s*([\u4e00-\u9fff])/g, '$1$2')
            // 重组分散的英文字母和数字
            .replace(/([A-Za-z])\s*\n\s*([A-Za-z0-9])/g, '$1$2')
            // 重组分散的括号内容
            .replace(/（\s*([^）\n]*?)\s*）/g, '（$1）')
            .replace(/\(\s*([^)\n]*?)\s*\)/g, '($1)');
          
          // 格式化和美化处理
          formattedContent = formattedContent
            // 处理标题与其他内容的分离（特别处理连续标题的情况）
            .replace(/(#{1,6}\s*[^\n#]+)(#{1,6})/g, (match, title, nextHash) => {
              return `${title}\n\n${nextHash}`;
            })
            // 处理标题后紧跟数字编号的情况
            .replace(/(#{1,6}\s*[^\n#]+)(\d+[.、])/g, (match, title, number) => {
              return `${title}\n\n#### ${number}`;
            })
            // 处理标题后紧跟列表项的情况
            .replace(/(#{1,6}\s*[^\n#]+)(-\s*)/g, (match, title, dash) => {
              return `${title}\n\n${dash}`;
            })
            // 确保标题格式正确
            .replace(/(^|\n)(#{1,6})\s*([^\n]+)/g, (match, prefix, hashes, content) => {
              const cleanContent = content.trim();
              if (cleanContent) {
                return `${prefix}\n${hashes} ${cleanContent}\n`;
              }
              return match;
            })
            // 确保列表项格式正确，特别处理emoji开头的列表项
            .replace(/(^|\n)-\s*([^\n]+)/g, (match, prefix, content) => {
              const cleanContent = content.trim();
              if (cleanContent) {
                // 如果前面已经有换行，就不再添加额外换行
                return prefix === '\n' ? `\n- ${cleanContent}` : `${prefix}- ${cleanContent}`;
              }
              return match;
            })
            // 强制分离emoji开头的新列表项
            .replace(/([^\n])(-\s*[\u{1F300}-\u{1F9FF}])/gu, '$1\n$2')
            // 确保数字编号后的列表项正确分离
             .replace(/(\d+[.、][^\n]*?)(-\s*[\u{1F300}-\u{1F9FF}])/gu, '$1\n$2')
            // 确保数字编号格式正确（只处理独立的数字编号，不处理已有标题标记的）
            .replace(/(^|\n)(?!#{1,6}\s*)(\d+[.、])\s*([^\n]+)/g, (match, prefix, number, content) => {
              const cleanContent = content.trim();
              if (cleanContent) {
                return `${prefix}\n#### ${number} ${cleanContent}\n`;
              }
              return match;
            })
            // 处理连续列表项，减少不必要的空行
            .replace(/(- [^\n]+)\n\n(- [^\n]+)/g, '$1\n$2')
            // 确保emoji列表项之间有换行分隔
            .replace(/([^\n])(- [\u{1F300}-\u{1F9FF}])/gu, '$1\n$2')
            // 清理多余的空行
            .replace(/\n{3,}/g, '\n\n')
            // 确保标题前有空行（除了开头）
            .replace(/([^\n])\n(#{1,6}\s)/g, '$1\n\n$2')
            // 确保段落间有适当间距，但不影响列表项
            .replace(/([^\n-])\n([^\n\s#-])/g, '$1\n\n$2');
          
          // 更新格式化后的内容
          replyContent = normalizeMarkdownContent(formattedContent);
          syncResponseBuffers();
          
          // 最终更新UI显示格式化后的内容（包含思考内容）
          setChatList(prev => prev.map(chat => 
            chat.messages.some(msg => msg.id === aiMessageId)
              ? {
                  ...chat,
                  messages: chat.messages.map(msg => 
                    msg.id === aiMessageId 
                      ? { 
                          ...msg, 
                          content: thoughtContent
                            ? `<thought>${thoughtContent}</thought>\n\n${replyContent}`
                            : replyContent
                        }
                      : msg
                  )
                }
              : chat
          ));
          
          setIsSending(false); // 流式响应结束，重置发送状态
          if (activeResponseReaderRef.current === reader) {
            activeResponseReaderRef.current = null;
          }
          if (activeResponseControllerRef.current === controller) {
            activeResponseControllerRef.current = null;
          }
          break;
        }
        
        const chunk = decoder.decode(value, { stream: true });
        
        // 将新数据添加到缓冲区
        buffer += chunk;
        
        // 按行分割数据
        const lines = buffer.split('\n');
        // 保留最后一行（可能不完整）
        buffer = lines.pop() || '';
        
        // 处理每一行
        for (const line of lines) {
          const trimmedLine = line.trim();
          if (trimmedLine === '') {
            // 跳过空行
            continue;
          }
          

          
          // 解析SSE格式的数据
          if (trimmedLine.startsWith('data:')) {
            const data = trimmedLine.startsWith('data: ') 
              ? trimmedLine.substring(6) // 移除"data: "前缀
              : trimmedLine.substring(5); // 移除"data:"前缀
            if (currentEventType === 'error_message') failResponse(data);
            if (currentEventType === 'model_fallback') {
              handleModelFallback(data);
              currentEventType = '';
              expectingAiConversationId = false;
              continue;
            }
            
            // 检查是否是期待的aiConversationId数据
            if (expectingAiConversationId) {
              // 这是aiConversationId的值
              const receivedAiConversationId = data.trim();
              
              // 确保作为字符串处理，避免精度丢失
              const aiConversationIdStr = String(receivedAiConversationId);
              
              // 更新currentConversationId状态
              setCurrentConversationId(aiConversationIdStr);
              
              // 更新本地对话列表中对应对话的ID
              setChatList(prev => prev.map(chat => 
                chat.id === currentChat.id 
                  ? { ...chat, id: aiConversationIdStr, timestamp: activityTime, updatedAt: activityTime }
                  : chat
              ));
              
              // Keep this response's message ID stable while the conversation ID changes.
              
              // 重置标记
              expectingAiConversationId = false;
              // 不将aiConversationId添加到消息内容中，直接跳过
              continue;
            }
            
            // 检查是否是旧格式的aiConversationId数据（格式：aiConversationId\t数字ID）
            if (data.startsWith('aiConversationId\t')) {
              // 解析aiConversationId字段
              const parts = data.split('\t');
              if (parts.length >= 2) {
                const receivedAiConversationId = parts[1].trim();
                
                // 确保作为字符串处理，避免精度丢失
                const aiConversationIdStr = String(receivedAiConversationId);
                
                // 更新currentConversationId状态
                setCurrentConversationId(aiConversationIdStr);
                
                // 更新本地对话列表中对应对话的ID
                setChatList(prev => prev.map(chat => 
                  chat.id === currentChat.id 
                    ? { ...chat, id: aiConversationIdStr, timestamp: activityTime, updatedAt: activityTime }
                    : chat
                ));
                
                // Keep this response's message ID stable while the conversation ID changes.
                
              }
              // 不将aiConversationId添加到消息内容中，直接跳过
              continue;
            }
            
            // 尝试解析JSON格式的数据（支持thought和reply类型）
            let handledStructuredData = false;
            try {
              const jsonData = JSON.parse(data);
              if (isSessionExpiredCode(jsonData?.code)) {
                if (!authSession.expire(streamSession)) {
                  authSession.assertCurrent(streamSession);
                }
                throw new AuthSessionExpiredError(
                  typeof jsonData.info === 'string' ? jsonData.info : undefined,
                );
              }
              if (jsonData.type && jsonData.content !== undefined) {
                if (jsonData.type === 'thought') {
                  appendThoughtContent(jsonData.content);
                } else if (jsonData.type === 'reply') {
                  appendReplyContent(jsonData.content);
                }
                // 结构化事件即使是未知类型也不能再按普通文本重复累积。
                handledStructuredData = true;
                
                // JSON格式数据处理完成后，不立即更新UI，而是继续处理
                // 让统一的UI更新逻辑处理显示
                
                // 只要有内容输出，就关闭loading状态
                if (!firstReplyShownRef.current && (replyContent.length > 0 || thoughtContent.length > 0)) {
                  setIsLoading(false);
                  firstReplyShownRef.current = true;
                }
              }
            } catch (jsonError) {
              if (jsonError instanceof AuthSessionExpiredError) {
                throw jsonError;
              }
              // 不是JSON格式，继续按原来的方式处理
            }
            
            if (!handledStructuredData) {
              // 处理普通消息数据内容：根据事件类型分别处理
              const processedData = data.trim() === '' ? '\n' : data;

              if (currentEventType === 'thought') {
                appendThoughtContent(processedData);
              } else if (currentEventType === 'reply') {
                appendReplyContent(processedData);
              } else {
                // 未声明类型的兼容数据继续显示，但不进入自动播报队列。
                appendReplyContent(processedData, false);
              }
            }

            if (!firstReplyShownRef.current && (replyContent.length > 0 || thoughtContent.length > 0)) {
              setIsLoading(false);
              firstReplyShownRef.current = true;
            }
            
            /**
             * 立即更新UI显示（合并思考内容和回复内容）
             * 修复setChatList回调函数返回undefined的问题
             */
            setChatList(prev => {
              // 合并思考内容和回复内容
              let combinedContent = '';
              if (thoughtContent) {
                combinedContent += `<thought>${thoughtContent}</thought>`;
              }
              if (replyContent) {
                if (combinedContent) combinedContent += '\n\n';
                combinedContent += replyContent;
              }
              
              // 查找目标对话
              const targetChat = prev.find(chat => chat.messages.some(msg => msg.id === aiMessageId));
              if (!targetChat) {
                return prev; // 返回原始状态，避免undefined
              }
              
              const newChatList = prev.map(chat => 
                chat.id === targetChat.id
                  ? {
                      ...chat,
                      messages: chat.messages.map(msg => {
                        if (msg.id === aiMessageId) {
                          return { ...msg, content: combinedContent };
                        }
                        return msg;
                      })
                    }
                  : chat
              );
              
              return newChatList;
            });
            
            // 自动滚动到底部
            scrollToBottom();
          } else if (trimmedLine.startsWith('event:')) {
            // 处理event类型的数据
            const eventType = trimmedLine.substring(6).trim(); // 移除"event:"前缀
            
            // 避免重复设置相同的事件类型
            if (currentEventType !== eventType) {
              // 记录当前事件类型
              currentEventType = eventType;
              
              // 如果是aiConversationId事件，标记下一行data为ID数据
              if (eventType === 'aiConversationId') {
                expectingAiConversationId = true;
              }
            }
            // 其他event类型也不添加到消息内容中
            continue;
          } else {
            // 跳过其他不识别的行，避免显示调试信息
            continue;
          }
        }
      }
      
    } catch (error) {
      if (responseRunIdRef.current !== responseRunId) {
        return;
      }
      if (replyTtsStreamId !== null && replyTtsContextVersion === ttsContextVersionRef.current) {
        cancelTts();
      }
      void activeResponseReaderRef.current?.cancel('response-error').catch(() => undefined);
      if (activeResponseControllerRef.current) {
        activeResponseControllerRef.current.abort();
        activeResponseControllerRef.current = null;
      }
      activeResponseReaderRef.current = null;
      setIsLoading(false);
      setIsSending(false); // 出错时重置发送状态
      
      // 检查是否是用户主动中断的请求
      if (error instanceof Error && error.name === 'AbortError') {
        return; // 中断错误不需要显示错误消息，因为handleAbortConversation已经处理了
      }
      
      // 如果还没有添加AI消息，先添加一个
      const currentMessages = getCurrentMessages();
      const hasAiMessage = currentMessages.some(msg => msg.id === aiMessageId);
      
      if (!hasAiMessage) {
        setChatList(prev => prev.map(chat => 
          chat.id === currentChat.id 
            ? { ...chat, messages: [...chat.messages, aiMessage] }
            : chat
        ));
      }
      
      // 显示错误消息
      const errorMessage = `发生错误: ${error instanceof Error ? error.message : '未知错误'}`;
      setChatList(prev => prev.map(chat => 
        chat.messages.some(msg => msg.id === aiMessageId)
          ? {
              ...chat,
                messages: chat.messages.map(msg =>
                  msg.id === aiMessageId
                  ? { ...msg, content: replyContent || errorMessage }
                  : msg
              )
            }
          : chat
      ));
    }
  };
  
  const generateAIResponse = (userInput: string, agent: Agent | null): string => {
    const responses = [
      '这是一个很好的问题。让我为您详细解答...',
      '根据您的描述，我建议您可以考虑以下几个方面...',
      '感谢您的提问。基于我的专业知识，我认为...',
      '这个问题涉及多个方面，让我逐一为您分析...',
      '您提到的情况确实需要注意，建议您...'
    ];
    

    
    return responses[Math.floor(Math.random() * responses.length)];
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && e.shiftKey) {
      return; // 允许换行
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (!isSending) { // 只有在不发送时才允许发送
        handleSendMessage();
      }
    }
  };

  const startNewChat = () => {
    cancelActiveInteraction();
    const now = new Date().toISOString();
    // 重置当前AI消息内容
    currentAiMessageRef.current = '';
    // 重置思考内容
    currentThoughtContentRef.current = '';
    // 重置发送状态
    setIsSending(false);
    
    // 重置当前对话ID（新对话开始时为null）
    setCurrentConversationId(null);
    
    const newChat: ChatItem = {
      id: Date.now().toString(),
      title: 'New Chat',
      timestamp: '刚刚',
      updatedAt: now,
      active: true,
      messages: [],
      agentId: selectedAgent?.id,
      isNewChat: true // 添加新对话标记
    };
    setChatList(prev => [newChat, ...prev.map(chat => ({ ...chat, active: false }))]);
  };
  
  const switchToChat = (chatId: string) => {
    cancelActiveInteraction();
    // 重置当前AI消息内容
    currentAiMessageRef.current = '';
    // 重置思考内容
    currentThoughtContentRef.current = '';
    // 重置发送状态
    setIsSending(false);
    
    setChatList(prev => prev.map(chat => ({
      ...chat,
      active: chat.id === chatId
    })));
    
    // 设置当前对话ID - 修复类型比较问题
    const isServerConversation = (serverConversations || []).some(conv => 
      conv.aiConversationId.toString() === chatId
    );
    
    if (isServerConversation) {
      setCurrentConversationId(chatId);
    } else {
      setCurrentConversationId(null);
    }
    
    // 如果是服务器对话且还没有加载消息，则加载消息记录
    const targetChat = chatList.find(chat => chat.id === chatId);
    
    if (isServerConversation && targetChat && targetChat.messages.length === 0) {
      loadConversationMessages(chatId);
    }
  };
  
  const deleteChat = (chatId: string) => {
    cancelActiveInteraction();
    setChatList(prev => {
      const filteredChats = prev.filter(chat => chat.id !== chatId);
      if (filteredChats.length === 0) {
        // 如果删除后没有对话了，创建一个新的
        return [{
          id: Date.now().toString(),
          title: 'New Chat',
          timestamp: '刚刚',
          updatedAt: new Date().toISOString(),
          active: true,
          messages: [],
          agentId: undefined
        }];
      }
      // 如果删除的是当前活跃的对话，激活第一个对话
      const deletedChat = prev.find(chat => chat.id === chatId);
      if (deletedChat?.active && filteredChats.length > 0) {
        filteredChats[0].active = true;
      }
      return filteredChats;
    });
  };
  
  // 清空当前对话的消息
  const clearCurrentChat = () => {
    const currentChat = getCurrentChat();
    if (!currentChat) return;
    cancelActiveInteraction();
    
    // 重置当前AI消息内容
    currentAiMessageRef.current = '';
    // 重置思考内容
    currentThoughtContentRef.current = '';
    // 重置发送状态
    setIsSending(false);
    
    // 清空当前对话的消息列表
    setChatList(prev => prev.map(chat => 
      chat.id === currentChat.id 
        ? { ...chat, messages: [] }
        : chat
    ));
    
  };
  
  // 下载当前对话内容为txt文件
  const handleDownloadChat = () => {
    const currentChat = getCurrentChat();
    if (!currentChat || currentChat.messages.length === 0) {
      alert('当前对话没有内容可以下载');
      return;
    }
    
    // 格式化对话内容
    let chatContent = `对话记录\n`;
    chatContent += `对话标题: ${currentChat.title}\n`;
    chatContent += `智能体: ${getDisplayName(selectedAgent)}\n`;
    chatContent += `导出时间: ${new Date().toLocaleString('zh-CN')}\n`;
    chatContent += `消息数量: ${currentChat.messages.length}条\n`;
    chatContent += `${'='.repeat(50)}\n\n`;
    
    // 遍历消息并格式化
    currentChat.messages.forEach((message, index) => {
      const timeStr = message.createTime 
        ? message.createTime
        : (message.timestamp && !isNaN(message.timestamp.getTime()) 
          ? formatTime(message.timestamp) 
          : '刚刚');
      
      const senderName = message.sender === 'user' 
        ? (userInfo?.phoneNumber ? maskPhoneNumber(userInfo.phoneNumber) : '用户')
        : (message.agentName || getDisplayName(selectedAgent));
      
      chatContent += `[${index + 1}] ${senderName} (${timeStr}):\n`;
      
      // 处理消息内容，分离思考内容和回复内容
      let messageContent = message.content;
      
      // 检查是否包含思考内容
      const thoughtMatch = messageContent.match(/<thought>(.*?)<\/thought>/s);
      if (thoughtMatch) {
        const thoughtContent = thoughtMatch[1].trim();
        const replyContent = messageContent.replace(/<thought>.*?<\/thought>\s*/s, '').trim();
        
        if (thoughtContent) {
          chatContent += `\n💭 思考过程:\n${thoughtContent}\n\n`;
        }
        if (replyContent) {
          chatContent += `📝 回复内容:\n${replyContent}\n`;
        }
      } else {
        // 没有思考内容，直接输出消息内容
        chatContent += `${messageContent}\n`;
      }
      
      chatContent += `\n${'-'.repeat(30)}\n\n`;
    });
    
    // 创建并下载文件
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const filename = `对话记录_${timestamp}.txt`;
    
    const blob = new Blob([chatContent], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    
    // 清理URL对象
    URL.revokeObjectURL(url);
    
  };
  
  const updateChatTitle = (chatId: string, newTitle: string) => {
    setChatList(prev => prev.map(chat => 
      chat.id === chatId ? { ...chat, title: newTitle } : chat
    ));
  };

  /**
   * 处理智能体选择，确保大整数ID不会发生精度丢失
   * @param agent - 选中的智能体
   * @param modelType - 模型类型（字符串格式）
   */
  const handleAgentSelect = (agent: Agent, modelType: string) => {
    cancelActiveInteraction();
    const aiModelConfigId = resolveModelConfigId(agent.aiModelConfigId, agent.id, modelType);
    if (!aiModelConfigId) {
      alert('模型配置异常，请刷新页面后重试');
      return;
    }
    
    const agentWithConfigId = {
      ...agent,
      aiModelConfigId: aiModelConfigId
    };
    
    setSelectedAgent(agentWithConfigId);
    
    // 存储模型类型，用于API调用
    const currentChat = getCurrentChat();
    if (currentChat) {
      setChatList(prev => prev.map(chat => 
        chat.id === currentChat.id 
          ? { 
              ...chat, 
              title: `与${agent.name}的对话`,
              agentId: agent.id,
              modelType: aiModelConfigId // 保存已校验的模型配置 ID
            }
          : chat
      ));
    }
  };

  const toggleVoiceDialogue = () => {
    const nextEnabled = !voiceDialogueEnabledRef.current;
    voiceDialogueEnabledRef.current = nextEnabled;
    setVoiceDialogueEnabled(nextEnabled);
    if (nextEnabled) {
      unlockTts();
    } else {
      cancelTts();
    }
  };

  // 确保在组件初始化时有默认的智能体选择
  useEffect(() => {
    // 移除默认智能体创建逻辑，完全依赖AgentLibrary组件的自动选择
    // 这样可以确保selectedAgent有正确的aiModelConfigId
  }, [selectedAgent]);
  
  const formatTime = (date: Date) => {
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟前`;
    if (hours < 24) return `${hours}小时前`;
    if (days < 7) return `${days}天前`;
    return date.toLocaleDateString();
  };

  const getConversationSortTime = (value?: string | Date | null) => {
    if (!value) return 0;
    if (value instanceof Date) return Number.isNaN(value.getTime()) ? 0 : value.getTime();
    const parsed = new Date(value).getTime();
    return Number.isNaN(parsed) ? 0 : parsed;
  };

  const medicalTopics = [
    '体检时哪些检查需要空腹 →',
    '如何拥有一个健康肠胃，生活中做好这几件事对肠道健康非常重要 →',
    '肺癌的最常见并发症有哪些？→'
  ];

  const healthTopics = [
    '生活中有哪些常见的致癌物？该如何分辨 →',
    '心梗逐渐年轻化，是什么原因导致的 →',
    '保健品真的保健吗？保健品都有哪些坑 →'
  ];

  const mythBustingTopics = [
    '褪黑素到底是不是失眠救星 →',
    '运动有助于入睡吗 →',
    ''
  ];

  const currentMessages = getCurrentMessages();
  const activeChat = chatList.find(chat => chat.active);
  const activeAgentName = getDisplayName(selectedAgent);
  const promptGroups = [
    { title: '医疗科普', topics: medicalTopics.slice(0, 2) },
    { title: '生活健康', topics: healthTopics.slice(0, 2) },
    { title: '健康辟谣', topics: mythBustingTopics.slice(0, 2).filter(Boolean) }
  ];

  // 条件渲染逻辑必须在所有Hooks调用之后
  // 如果正在检查登录状态，显示加载界面
  if (isChecking) {
    return (
      <div className="h-screen flex items-center justify-center bg-gray-100">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-teal-600 mx-auto mb-4"></div>
          <p className="text-gray-600">正在验证登录状态...</p>
        </div>
      </div>
    );
  }

  // 如果未登录，显示提示信息（实际上会被autoRedirect处理）
  if (!isAuthenticated) {
    return (
      <div className="h-screen flex items-center justify-center bg-gray-100">
        <div className="text-center">
          <p className="text-gray-600 mb-4">请先登录后使用智笔一言功能</p>
          <button 
            onClick={() => window.location.reload()}
            className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
          >
            返回登录
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 h-full bg-[#f4f7f6] transition-all overflow-hidden">
      <div className="flex h-full gap-3 p-3">
        {/* 左侧边栏 */}
        <aside className={`${leftSidebarVisible ? 'w-[19rem]' : 'w-0'} flex flex-col h-full flex-shrink-0 transition-all duration-300 overflow-hidden`}>
          <div className="flex flex-col h-full w-[19rem] overflow-hidden rounded-2xl border border-gray-200/80 bg-white/95 shadow-sm">
            <main className="flex flex-col flex-1 min-h-0 overflow-hidden">
              {/* 新建聊天按钮和侧边栏收起按钮 */}
              <div className="p-4 border-b border-gray-100">
                <div className="mb-4 flex items-start justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2 text-base font-semibold text-gray-950">
                      <Sparkles size={18} className="text-teal-600" />
                      智笔医言
                    </div>
                    <p className="mt-1 text-xs text-gray-500">医学内容智能工作台</p>
                  </div>
                  <button
                    onClick={() => setLeftSidebarVisible(!leftSidebarVisible)}
                    className="rounded-xl p-2 text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900"
                    title={leftSidebarVisible ? '收起侧边栏' : '展开侧边栏'}
                  >
                    <ChevronLeft size={16} />
                  </button>
                </div>
                <button 
                  onClick={startNewChat}
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-gray-950 px-4 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-gray-800"
                >
                  <Plus size={16} />
                  新建对话
                </button>
              </div>
                    
            {/* 聊天历史列表 */}
            <div className="flex-1 min-h-0 pb-4 flex flex-col overflow-hidden">
              <div className="flex-1 min-h-0 px-4 pt-4 flex flex-col">
                {/* 刷新按钮 */}
                <div className="flex items-center justify-between mb-3 flex-shrink-0">
                  <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.12em] text-gray-500">
                    <Clock3 size={14} />
                    对话历史
                  </h3>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={loadServerConversations}
                      disabled={isLoadingConversations}
                      className="rounded-lg p-2 text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 disabled:opacity-50"
                      title="刷新对话列表"
                    >
                      <RefreshCw size={14} className={`text-gray-500 ${isLoadingConversations ? 'animate-spin' : ''}`} />
                    </button>
                  </div>
                </div>
                
                <div className="flex flex-col gap-2 text-sm flex-1 overflow-y-auto scrollbar-hide min-h-0">
                  {/* 合并并排序所有对话 */}
                  {(() => {
                    // 合并服务器对话和本地对话
                    const allConversations = [];
                    
                    // 添加服务器对话
                    (serverConversations || []).forEach(serverConv => {
                      const localChat = chatList.find(chat => chat.id === serverConv.aiConversationId.toString());
                      const chat = localChat || convertConversationToInternal(serverConv);
                      const localSortTime = getConversationSortTime(localChat?.updatedAt || localChat?.timestamp);
                      const serverSortTime = getConversationSortTime(serverConv.createTime);
                      allConversations.push({
                        ...chat,
                        isServerConversation: true,
                        serverConv,
                        sortTime: localSortTime || serverSortTime
                      });
                    });
                    
                    // 添加本地对话（排除已在服务器列表中的）
                    chatList.filter(chat => 
                      !(serverConversations || []).some(serverConv => serverConv.aiConversationId.toString() === chat.id)
                    ).forEach(chat => {
                      allConversations.push({
                        ...chat,
                        isServerConversation: false,
                        sortTime: getConversationSortTime(chat.updatedAt || chat.timestamp) || Date.now()
                      });
                    });
                    
                    // 自定义排序：新创建的本地对话优先，然后按时间从新到旧排序
                    allConversations.sort((a, b) => {
                      // 新创建的本地对话优先显示
                      if (a.isNewChat && !b.isNewChat) return -1;
                      if (!a.isNewChat && b.isNewChat) return 1;
                      
                      // 其他情况按最近活动时间排序
                      return (b.sortTime || 0) - (a.sortTime || 0);
                    });
                    
                    return allConversations.map((conversation) => (
                      <div key={conversation.id}>
                        <div 
                          onClick={() => {
                            cancelActiveInteraction();
                            if (conversation.isServerConversation) {
                              // 服务器对话处理逻辑
                              const conversationIdStr = conversation.serverConv.aiConversationId.toString();
                              
                              const localChat = chatList.find(chat => chat.id === conversationIdStr);
                              if (!localChat) {
                                const newChat = convertConversationToInternal(conversation.serverConv);
                                setChatList(prev => {
                                  const updatedList = prev.map(c => ({ ...c, active: false }));
                                  return [{ ...newChat, active: true }, ...updatedList];
                                });
                                // 自动加载消息历史
                                loadConversationMessages(conversationIdStr);
                              } else {
                                switchToChat(conversationIdStr);
                                // 如果消息为空，自动加载消息历史
                                if (localChat.messages.length === 0) {
                                  loadConversationMessages(conversationIdStr);
                                }
                              }
                            } else {
                              // 本地对话处理逻辑
                              switchToChat(conversation.id);
                            }
                          }}
                          className={`relative flex items-center gap-3 break-all rounded-xl border px-3 py-3 pr-14 cursor-pointer group transition-all ${
                            conversation.active 
                              ? 'border-teal-500/70 bg-teal-50 text-teal-700 shadow-sm'
                              : 'border-transparent bg-gray-50/70 text-gray-700 hover:border-gray-200 hover:bg-white'
                          }`}
                        >
                          <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${
                            conversation.active ? 'bg-teal-100 text-teal-700' : 'bg-white text-gray-500'
                          }`}>
                            <MessageCircle size={15} />
                          </span>
                          <div className="relative flex-1 overflow-hidden">
                            <div className="text-sm font-medium truncate">{conversation.title}</div>

                          </div>
                          {isLoadingMessages && conversation.active && (
                            <div className="absolute right-8 top-1/2 transform -translate-y-1/2">
                              <RefreshCw size={12} className="animate-spin text-teal-500" />
                            </div>
                          )}
                          <div className="absolute z-10 flex visible right-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button 
                              onClick={async (e) => {
                                e.stopPropagation();
                                const newTitle = prompt('请输入新的对话标题:', conversation.title);
                                if (newTitle && newTitle.trim()) {
                                  if (conversation.isServerConversation) {
                                    try {
                                      await updateConversationTitle({
                                        aiConversationId: conversation.serverConv.aiConversationId,
                                        title: newTitle.trim()
                                      });
                                      // 更新本地状态
                                      setServerConversations(prev => 
                                        prev.map(conv => 
                                          conv.aiConversationId === conversation.serverConv.aiConversationId 
                                            ? { ...conv, title: newTitle.trim() } 
                                            : conv
                                        )
                                      );
                                      alert('标题修改成功！');
                                    } catch (error) {
                                      console.error('修改标题失败:', error);
                                      alert('修改标题失败，请稍后重试');
                                    }
                                  } else {
                                    updateChatTitle(conversation.id, newTitle.trim());
                                  }
                                }
                              }}
                            className="p-1 hover:bg-teal-100 rounded-lg"
                            >
                              <Edit2 size={14} />
                            </button>
                            <button 
                              onClick={async (e) => {
                                e.stopPropagation();
                                if (confirm('确定要删除这个对话吗？')) {
                                  cancelActiveInteraction();
                                  if (conversation.isServerConversation) {
                                    try {
                                      await deleteConversation(conversation.serverConv.aiConversationId);
                                      // 从本地状态中移除已删除的对话
                                      setServerConversations(prev => 
                                        prev.filter(conv => 
                                          conv.aiConversationId !== conversation.serverConv.aiConversationId
                                        )
                                      );
                                      // 如果删除的是当前活跃对话，切换到默认对话
                                      if (currentConversationId === conversation.serverConv.aiConversationId) {
                                        setCurrentConversationId(null);
                                        // 切换到本地新对话
                                        setChatList(prev => 
                                          prev.map(chat => ({ ...chat, active: chat.isNewChat || false }))
                                        );
                                      }
                                      alert('对话删除成功！');
                                    } catch (error) {
                                      console.error('删除对话失败:', error);
                                      alert('删除对话失败，请稍后重试');
                                    }
                                  } else {
                                    deleteChat(conversation.id);
                                  }
                                }
                              }}
                              className="p-1 hover:bg-red-100 rounded-lg text-red-500"
                            >
                              <Trash2 size={14} />
                            </button>
                          </div>
                        </div>
                      </div>
                    ));
                  })()}
                              
                  {/* 加载更多按钮 */}
                  {conversationPagination.hasNext && (
                    <div className="mt-4 text-center">
                      <button
                        onClick={loadMoreServerConversations}
                        disabled={conversationPagination.isLoadingMore}
                        className="px-4 py-2 text-sm text-teal-600 hover:text-teal-700 hover:bg-teal-50 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {conversationPagination.isLoadingMore ? (
                          <div className="flex items-center gap-2">
                            <RefreshCw size={14} className="animate-spin" />
                            <span>加载中...</span>
                          </div>
                        ) : (
                          '加载更多对话'
                        )}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>
                    
          </main>
          
          {/* 底部用户信息 */}
          <footer className="m-3 flex-shrink-0 flex items-center justify-between min-w-0 overflow-hidden rounded-2xl border border-gray-200 bg-gray-50 p-3">
            <div className="flex-1 flex-shrink-0 overflow-hidden">
              <div className="flex items-center overflow-hidden">
                <div className="w-10 h-10 overflow-hidden rounded-xl shrink-0">
                  <CachedAvatar
                    src={userInfo?.avatar}
                    alt="用户头像"
                    className="h-full w-full object-contain p-0.5"
                    fallbackClassName="flex h-full w-full items-center justify-center bg-gray-200"
                    iconClassName="h-6 w-6 text-gray-400"
                  />
                </div>
                <div className="flex-1 min-w-0 ml-2">
                  <h2 className="overflow-hidden text-sm font-semibold text-gray-900 text-ellipsis whitespace-nowrap">
                    {userInfo?.nickName || userInfo?.username || '未登录用户'}
                  </h2>
                  <p className="text-xs text-gray-500 truncate">
                    {userInfo?.phoneNumber ? maskPhoneNumber(userInfo.phoneNumber) : (userInfo?.userId || '暂无信息')}
                  </p>
                </div>
              </div>
            </div>
            
            {/* 充值按钮 */}
            <button 
              onClick={() => setShowRechargeModal(true)}
              className="px-3 py-1.5 border border-teal-500 text-teal-700 hover:bg-teal-50 rounded-lg transition-colors duration-200 text-sm font-medium flex-shrink-0 ml-2"
            >
              充值
            </button>

          </footer>
        </div>
      </aside>

        {/* 左侧边栏展开按钮（当侧边栏收起时显示） */}
        {!leftSidebarVisible && (
          <div className="fixed left-4 top-1/2 -translate-y-1/2 z-10">
            <button
              onClick={() => setLeftSidebarVisible(true)}
              className="p-2 bg-white border border-gray-200 hover:bg-gray-50 rounded-lg shadow-md transition-colors"
              title="展开左侧边栏"
            >
              <ChevronRight size={16} className="text-gray-600" />
            </button>
          </div>
        )}

        {/* 主内容区域 */}
        <div className="flex-1 flex min-w-0 flex-col h-full overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm transition-all duration-300">
          <header className="flex flex-shrink-0 items-center justify-between gap-4 border-b border-gray-100 px-6 py-4">
            <div className="min-w-0">
              <div className="flex items-center gap-2 text-xs font-medium text-teal-700">
                <span className="h-2 w-2 rounded-full bg-teal-500"></span>
                {isConnected ? '实时服务已连接' : '本地对话模式'}
              </div>
              <h1 className="mt-1 truncate text-xl font-semibold text-gray-950">{activeChat?.title || '新对话'}</h1>
            </div>
            <div className="flex flex-shrink-0 items-center gap-2">
              <div className="hidden items-center gap-2 rounded-full border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-700 md:flex">
                <span className="flex h-5 w-5 overflow-hidden rounded-md border border-gray-200 bg-white">
                  <CachedAvatar
                    src={selectedAgent?.avatar}
                    alt={activeAgentName}
                    className="h-full w-full object-contain p-0.5"
                    fallbackClassName="flex h-full w-full items-center justify-center bg-white"
                    iconClassName="h-3.5 w-3.5 text-teal-700"
                  />
                </span>
                <span className="max-w-[12rem] truncate">{activeAgentName}</span>
              </div>
              <button
                onClick={() => setIsAllHistoryEnabled(!isAllHistoryEnabled)}
                className={`inline-flex items-center gap-2 rounded-full border px-3 py-2 text-sm font-medium transition-colors ${
                  isAllHistoryEnabled
                    ? 'border-teal-200 bg-teal-50 text-teal-700'
                    : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                }`}
                title={isAllHistoryEnabled ? '当前携带全部历史记录' : '当前不携带历史记录'}
              >
                <ShieldCheck size={15} />
                上下文
              </button>
            </div>
          </header>
          {/* 消息显示区域 */}
          <main className="flex-1 overflow-hidden bg-[radial-gradient(circle_at_top_left,#ecfdf5_0,#ffffff_34%,#f8fafc_100%)]">
            <div className="h-full flex flex-col">
              {currentMessages.length === 0 ? (
                /* 欢迎界面 */
                <div className="flex-1 overflow-y-auto px-6 py-8">
                  <div className="mx-auto flex min-h-full max-w-5xl flex-col justify-center">
                    <div className="mb-8 max-w-3xl">
                      <div className="mb-5 flex h-14 w-14 items-center justify-center overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
                        <CachedAvatar
                          src={selectedAgent?.avatar}
                          alt={activeAgentName}
                          className="h-full w-full object-contain p-1.5"
                          fallbackClassName="flex h-full w-full items-center justify-center bg-white"
                          iconClassName="h-7 w-7 text-teal-700"
                        />
                      </div>
                      <p className="mb-3 text-sm font-semibold text-teal-700">AI medical workspace</p>
                      <h2 className="text-4xl font-semibold tracking-normal text-gray-950">
                        {selectedAgent ? `与 ${activeAgentName} 开始对话` : '开始新的医学创作'}
                      </h2>
                      <p className="mt-4 max-w-2xl text-base leading-7 text-gray-600">
                        {selectedAgent 
                          ? selectedAgent.description 
                          : '选择一个智能体开始对话，或直接输入您的问题'
                        }
                      </p>
                    </div>
                    
                    {/* 推荐话题 */}
                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                      {promptGroups.map((group, groupIndex) => (
                        <div key={group.title} className={`${groupIndex === 2 ? 'hidden xl:block' : groupIndex === 1 ? 'hidden md:block' : ''} rounded-2xl border border-gray-200 bg-white/90 p-4 shadow-sm`}>
                          <h3 className="mb-3 text-sm font-semibold text-gray-950">{group.title}</h3>
                          <div className="space-y-2">
                            {group.topics.map((topic, index) => (
                              <button
                                type="button"
                                key={`${group.title}-${index}`}
                                onClick={() => setInputValue(topic.replace(' →', ''))}
                                className="w-full rounded-xl border border-gray-100 bg-gray-50 px-3 py-3 text-left text-sm leading-6 text-gray-700 transition-colors hover:border-teal-200 hover:bg-teal-50 hover:text-teal-800"
                              >
                                {topic.replace(' →', '')}
                              </button>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              ) : (
                /* 消息列表 */
                <div className="flex-1 overflow-y-auto px-6 py-6">
                  <div className="max-w-4xl mx-auto space-y-6">
                    {currentMessages.map((message) => (
                      <div key={message.id} className={`flex ${message.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                        <div className={`flex max-w-[82%] ${message.sender === 'user' ? 'flex-row-reverse' : 'flex-row'} gap-3`}>
                          {/* 头像 */}
                          <div className={`w-9 h-9 rounded-2xl flex items-center justify-center flex-shrink-0 overflow-hidden shadow-sm ${
                            message.sender === 'user' 
                              ? 'bg-gray-950' 
                              : 'border border-gray-200 bg-white'
                          }`}>
                            {message.sender === 'user' ? (
                              <User size={16} className="text-white" />
                            ) : (
                              <CachedAvatar
                                src={selectedAgent?.avatar}
                                alt={activeAgentName}
                                className="h-full w-full object-contain p-1"
                                fallbackClassName="flex h-full w-full items-center justify-center bg-white"
                                iconClassName="h-4 w-4 text-teal-700"
                              />
                            )}
                          </div>
                          
                          {/* 消息内容 */}
                          <div className={`rounded-3xl px-5 py-4 max-w-full overflow-hidden shadow-sm ${
                            message.sender === 'user'
                              ? 'bg-gray-950 text-white'
                              : 'border border-gray-200 bg-white text-gray-800'
                          }`}>
                            {message.sender === 'ai' && message.agentName && (
                              <div className="text-xs text-teal-600 font-medium mb-1">{getDisplayName(selectedAgent) || message.agentName}</div>
                            )}
                            {/* 根据消息发送者选择渲染方式 */}
                            {message.sender === 'ai' ? (
                              <div className="text-base leading-relaxed markdown-content break-words">
                                {/* 处理思考内容和普通内容的混合显示 */}
                                {(() => {
                                  const content = message.content;
                                  const thoughtRegex = /<thought>(.*?)<\/thought>/gs;
                                  const parts = [];
                                  let lastIndex = 0;
                                  let match;
                                  
                                  // 分离思考内容和普通内容
                                  while ((match = thoughtRegex.exec(content)) !== null) {
                                    // 添加思考内容之前的普通内容
                                    if (match.index > lastIndex) {
                                      const normalContent = content.slice(lastIndex, match.index);
                                      if (normalContent.trim()) {
                                        parts.push({
                                          type: 'normal',
                                          content: normalContent,
                                          key: `normal-${lastIndex}`
                                        });
                                      }
                                    }
                                    
                                    // 添加思考内容
                                    parts.push({
                                      type: 'thought',
                                      content: match[1],
                                      key: `thought-${match.index}`
                                    });
                                    
                                    lastIndex = match.index + match[0].length;
                                  }
                                  
                                  // 添加最后剩余的普通内容
                                  if (lastIndex < content.length) {
                                    const remainingContent = content.slice(lastIndex);
                                    if (remainingContent.trim()) {
                                      parts.push({
                                        type: 'normal',
                                        content: remainingContent,
                                        key: `normal-${lastIndex}`
                                      });
                                    }
                                  }
                                  
                                  // 如果没有思考内容，直接渲染普通内容
                                  if (parts.length === 0) {
                                    parts.push({
                                      type: 'normal',
                                      content: content,
                                      key: 'normal-0'
                                    });
                                  }
                                  
                                  return parts.map(part => {
                                    if (part.type === 'thought') {
                                      return (
                                        <div key={part.key} className="mb-2 p-3 bg-blue-50 border-l-4 border-blue-300 rounded-r-md max-w-full overflow-hidden">
                                          <div className="text-sm text-blue-600 font-medium mb-2 flex items-center">
                                            <span className="mr-1">💭</span>
                                            思考中...
                                          </div>
                                          <div className="text-base text-blue-800 italic leading-relaxed break-words">
                                            {part.content}
                                          </div>
                                        </div>
                                      );
                                    } else {
                                      return (
                                        <div key={part.key} className="max-w-full overflow-hidden">
                                          <ReactMarkdown
                                            remarkPlugins={[remarkGfm]}
                                            components={{
                                              h1: ({children}) => <h1 className="text-lg font-bold mb-3 mt-4 first:mt-0 break-words leading-snug">{children}</h1>,
                                              h2: ({children}) => <h2 className="text-base font-bold mb-2 mt-3 first:mt-0 break-words leading-snug">{children}</h2>,
                                              h3: ({children}) => <h3 className="text-base font-bold mb-2 mt-3 first:mt-0 break-words leading-snug">{children}</h3>,
                                              h4: ({children}) => <h4 className="text-sm font-semibold mb-1 mt-2 first:mt-0 break-words leading-snug">{children}</h4>,
                                              h5: ({children}) => <h5 className="text-sm font-semibold mb-1 mt-2 first:mt-0 break-words">{children}</h5>,
                                              h6: ({children}) => <h6 className="text-sm font-medium mb-1 mt-2 first:mt-0 break-words">{children}</h6>,
                                              ul: ({children}) => <ul className="list-disc list-inside mb-2 text-base">{children}</ul>,
                                              ol: ({children}) => <ol className="list-decimal list-inside mb-2 text-base">{children}</ol>,
                                              li: ({children}) => <li className="ml-2 mb-1 text-base break-words">{children}</li>,
                                              p: ({children}) => <p className="mb-2 last:mb-0 text-base leading-relaxed break-words">{children}</p>,
                                              strong: ({children}) => <strong className="font-semibold">{children}</strong>,
                                              em: ({children}) => <em className="italic">{children}</em>,
                                              del: ({children}) => <span>{children}</span>,
                                              code: ({children}) => <code className="bg-gray-100 px-1 py-0.5 rounded text-sm font-mono break-all">{children}</code>,
                                              pre: ({children}) => <pre className="bg-gray-100 p-3 rounded-xl text-sm font-mono overflow-x-auto mb-3 whitespace-pre-wrap break-words">{children}</pre>,
                                              blockquote: ({children}) => <blockquote className="my-3 border-l-4 border-teal-200 bg-teal-50/70 px-3 py-2 text-gray-700">{children}</blockquote>,
                                              hr: () => <hr className="my-4 border-gray-200" />,
                                              table: ({children}) => <div className="my-3 overflow-x-auto"><table className="min-w-full border-collapse text-sm">{children}</table></div>,
                                              th: ({children}) => <th className="border border-gray-200 bg-gray-50 px-3 py-2 text-left font-semibold">{children}</th>,
                                              td: ({children}) => <td className="border border-gray-200 px-3 py-2 align-top">{children}</td>
                                            }}
                                          >
                                            {normalizeMarkdownContent(part.content)}
                                          </ReactMarkdown>
                                        </div>
                                      );
                                    }
                                  });
                                })()}
                              </div>
                            ) : (
                              <div className="text-base leading-relaxed whitespace-pre-wrap break-words">{message.content}</div>
                            )}
                            {message.sender === 'ai' && message.content.trim() && (
                              <button type="button" disabled={isSending || isLoading || voiceInputPhase !== 'idle'}
                                onClick={() => { cancelTts(); unlockTts(); void speakTts(message.content); }}
                                className="mt-2 inline-flex min-h-8 items-center gap-1 rounded-full px-2 text-xs text-teal-700 hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-40"
                                aria-label="重新朗读这条回复">
                                <Volume2 size={13} />朗读
                              </button>
                            )}
                            <div className={`text-xs mt-2 ${
                              message.sender === 'user' ? 'text-teal-100' : 'text-gray-500'
                            }`}>
                              {message.createTime 
                                ? message.createTime
                                : (message.timestamp && !isNaN(message.timestamp.getTime()) 
                                  ? formatTime(message.timestamp) 
                                  : '刚刚')
                              }
                            </div>
                          </div>
                        </div>
                      </div>
                    ))}
                    
                    {/* AI正在输入指示器 - 显示AI思考状态的加载动画和文本 */}
                    {isLoading && (
                      <div className="flex justify-start">
                        <div className="flex gap-3 max-w-[80%]">
                          <div className="w-9 h-9 rounded-2xl border border-gray-200 bg-white flex items-center justify-center flex-shrink-0 overflow-hidden shadow-sm">
                            <CachedAvatar
                              src={selectedAgent?.avatar}
                              alt={activeAgentName}
                              className="h-full w-full object-contain p-1"
                              fallbackClassName="flex h-full w-full items-center justify-center bg-white"
                              iconClassName="h-4 w-4 text-teal-700"
                            />
                          </div>
                          <div className="bg-gray-100 rounded-2xl px-4 py-3">
                            <div className="flex items-center space-x-2">
                              <div className="flex space-x-1">
                                <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                                <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '0.1s'}}></div>
                                <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '0.2s'}}></div>
                              </div>
                              <span className="text-xs text-gray-500">思考中</span>
                            </div>
                          </div>
                        </div>
                      </div>
                    )}
                    
                    <div ref={messagesEndRef} />
                  </div>
                </div>
              )}
            </div>
          </main>
                  
                  {/* 底部输入区域 */}
                  <footer className="flex-shrink-0 border-t border-gray-100 bg-white/95 px-6 py-4">
          <div className="max-w-4xl mx-auto">
            {/* DeepSeek风格的输入容器 */}
            <div className="relative rounded-3xl border border-gray-200 bg-white p-3 shadow-[0_16px_45px_rgba(15,23,42,0.08)]">
              {/* 输入区域 */}
              <div className="relative">
                <textarea 
                  className="w-full resize-none rounded-2xl border-0 bg-gray-50 px-4 py-4 pr-28 text-gray-900 placeholder-gray-500 outline-none transition-colors focus:bg-white focus:ring-2 focus:ring-teal-100 disabled:bg-gray-50 disabled:text-gray-400" 
                  rows={2} 
                  placeholder={isSending ? '正在发送消息...' : `给 ${getDisplayName(selectedAgent)} 发送消息`}
                  value={inputValue}
                  onChange={(e) => editVoiceInput(e.target.value)}
                  onKeyPress={handleKeyPress}
                  disabled={isSending || isLoading}
                />
                
                {/* 右侧按钮组 */}
                <div className="absolute right-3 bottom-3 flex items-center space-x-1">
                  {/* 语音输入按钮 */}
                  <button
                    type="button"
                    aria-label={isRecording ? '停止语音输入' : isRecognizing ? '取消启动录音' : '开始语音输入'}
                    aria-pressed={isRecording}
                    onClick={toggleVoiceInput}
                    className={`w-8 h-8 rounded-lg transition-colors duration-200 flex items-center justify-center ${
                      isRecording 
                        ? 'bg-red-100 text-red-600 hover:bg-red-200 animate-pulse border border-red-200' 
                        : 'bg-white text-gray-500 hover:bg-gray-100 hover:text-gray-700 border border-gray-200'
                    }`}
                    title={
                      isAwaitingAsrCompletion
                        ? "正在完成上一段语音识别"
                        : isRecording
                          ? "录音中：连续静音 30 秒或达到 5 分钟将自动停止；点击可手动停止"
                          : isRecognizing ? "正在连接麦克风和语音服务，点击取消" : "点击开始语音输入"
                    }
                    disabled={isSending || isLoading || isAwaitingAsrCompletion}
                  >
                    {isAwaitingAsrCompletion || isRecognizing ? <Loader2 size={14} className="animate-spin" /> : isRecording ? <MicOff size={14} /> : <Mic size={14} />}
                  </button>

                  {/* 清空输入框按钮 */}
                  {inputValue.trim() && (
                    <button 
                      onClick={() => editVoiceInput('')}
                      className="w-8 h-8 bg-gray-900 hover:bg-gray-700 text-white rounded-lg transition-colors duration-200 flex items-center justify-center"
                      title="清空输入框"
                    >
                      <X size={14} />
                    </button>
                  )}
                  
                  {/* 发送/停止按钮 */}
                  <button 
                    onClick={isSending || isLoading ? handleAbortConversation : handleSendMessage}
                    disabled={!isSending && !isLoading && !inputValue.trim()}
                    className={`w-8 h-8 text-white rounded-lg transition-colors duration-200 flex items-center justify-center ${
                      isSending || isLoading 
                        ? 'bg-red-600 hover:bg-red-700' 
                        : 'bg-teal-600 hover:bg-teal-700 disabled:bg-gray-300'
                    }`}
                    title={isSending || isLoading ? '停止对话' : '发送消息'}
                  >
                    {isSending || isLoading ? (
                      <Square size={14} />
                    ) : (
                      <Send size={14} />
                    )}
                  </button>
                </div>
              </div>

              {voiceInputPhase !== 'idle' && (
                <p className="mt-2 px-1 text-xs text-teal-700" role="status" aria-live="off">
                  {isRecording
                    ? `正在录音 · 剩余 ${Math.floor(voiceRemainingSeconds / 60)}:${String(voiceRemainingSeconds % 60).padStart(2, '0')} · 静音 30 秒自动停止`
                    : isAwaitingAsrCompletion ? '正在整理最后一句…' : '正在启动麦克风，请稍候…'}
                </p>
              )}
              {voiceInputNotice && (
                <p className="mt-2 px-1 text-xs text-amber-600" role="status" aria-live="polite">
                  {voiceInputNotice}
                </p>
              )}
              
              {/* 底部功能按钮 */}
              <div className="mt-3 flex flex-wrap items-center justify-between gap-2 px-1">
                <div className="flex flex-wrap items-center gap-2">

                  <button 
                    onClick={handleDownloadChat}
                    className="inline-flex items-center gap-2 rounded-full px-3 py-2 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100"
                    title="下载对话记录"
                  >
                    <Download size={16} />
                    导出
                  </button>
                  <button 
                    onClick={() => setIsAllHistoryEnabled(!isAllHistoryEnabled)}
                    className={`inline-flex items-center gap-2 rounded-full px-3 py-2 text-sm font-medium transition-colors ${
                      isAllHistoryEnabled 
                        ? 'text-teal-600 bg-teal-50 hover:bg-teal-100' 
                        : 'text-gray-600 hover:bg-gray-100'
                    }`}
                    title={isAllHistoryEnabled ? '当前携带全部历史记录' : '当前不携带历史记录'}
                  >
                    <MessageCircle size={16} />
                    全部历史
                  </button>
                </div>
                
                <div className="ml-auto mr-2 flex min-w-0 max-w-full flex-wrap items-center justify-end gap-2">
                  {(voiceDialogueEnabled || ttsStatus !== 'idle') && (
                    <div className="flex min-w-0 flex-wrap items-center justify-end gap-2 text-xs" role="status" aria-live="polite">
                      <span className={`break-words ${ttsError ? 'text-red-600' : 'text-gray-500'}`}>
                        {ttsError || (ttsStatus === 'playing' ? `正在播报 ${currentTtsSegment}/${totalTtsSegments}`
                          : ttsStatus === 'paused' ? '朗读已暂停'
                          : ttsStatus === 'synthesizing' ? '正在合成语音…'
                          : ttsStatus === 'waiting-text' ? '等待 AI 后续文字…'
                          : isLoading ? 'AI 正在思考…' : '语音已就绪')}
                      </span>
                      {canPauseTts && <button type="button" onClick={pauseTts} className="inline-flex min-h-8 shrink-0 items-center gap-1 rounded-full px-2 text-teal-700 hover:bg-teal-50"><Pause size={13} />暂停</button>}
                      {canResumeTts && !canRetryTts && <button type="button" onClick={() => { unlockTts(); resumeTts(); }} className="inline-flex min-h-8 shrink-0 items-center gap-1 rounded-full px-2 text-teal-700 hover:bg-teal-50"><Play size={13} />继续</button>}
                      {canRetryTts && <button type="button" onClick={() => { unlockTts(); retryTts(); }} className="inline-flex min-h-8 shrink-0 items-center gap-1 rounded-full px-2 text-teal-700 hover:bg-teal-50"><RotateCcw size={13} />从失败句继续</button>}
                      {ttsStatus !== 'idle' && <button type="button" onClick={cancelTts} className="min-h-8 shrink-0 rounded-full px-2 text-gray-600 hover:bg-gray-100">停止朗读</button>}
                    </div>
                  )}
                  {voiceDialogueEnabled && (
                    <label className="inline-flex shrink-0 items-center gap-1 text-xs text-gray-500">
                      语速
                      <select aria-label="朗读语速" value={playbackRate} onChange={event => setPlaybackRate(Number(event.target.value))}
                        className="min-h-8 rounded-lg border border-gray-200 bg-white px-1 text-gray-700">
                        {[0.75, 1, 1.25, 1.5].map(rate => <option key={rate} value={rate}>{rate}×</option>)}
                      </select>
                    </label>
                  )}
                  <button
                    type="button"
                    onClick={toggleVoiceDialogue}
                    aria-pressed={voiceDialogueEnabled}
                    className={`inline-flex items-center gap-2 rounded-full border px-3 py-2 text-sm font-medium transition-colors ${
                      ttsStatus === 'error'
                        ? 'border-red-200 bg-red-50 text-red-700'
                        : voiceDialogueEnabled
                        ? 'border-teal-200 bg-teal-50 text-teal-700'
                        : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                    }`}
                    title={
                      ttsError
                        ? ttsError
                        : ttsStatus === 'synthesizing'
                          ? `正在生成语音 ${currentTtsSegment}/${totalTtsSegments}`
                          : ttsStatus === 'playing'
                            ? `正在播报 ${currentTtsSegment}/${totalTtsSegments}`
                            : voiceDialogueEnabled
                              ? '已开启，后续 AI 回复将自动播报；点击关闭'
                              : '开启后自动播报后续 AI 回复'
                    }
                  >
                    {ttsStatus === 'synthesizing' ? (
                      <Loader2 size={15} className="animate-spin" />
                    ) : ttsStatus === 'error' ? (
                      <VolumeX size={15} />
                    ) : voiceDialogueEnabled ? (
                      <Volume2 size={15} />
                    ) : (
                      <VolumeX size={15} />
                    )}
                    {ttsStatus === 'error' ? '语音异常' : '语音对话'}
                    <span className={`relative h-5 w-9 shrink-0 rounded-full transition-colors ${
                      voiceDialogueEnabled ? 'bg-teal-500' : 'bg-gray-300'
                    }`} aria-hidden="true">
                      <span className={`absolute left-0.5 top-0.5 h-4 w-4 rounded-full bg-white shadow-sm transition-transform ${
                        voiceDialogueEnabled ? 'translate-x-4' : 'translate-x-0'
                      }`} />
                    </span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </footer>
        </div>
        
        {/* 右侧智能体库 */}
        <div className={`${rightSidebarVisible ? 'w-[22rem]' : 'w-0'} flex-shrink-0 transition-all duration-300 overflow-hidden`}>
          <div className="w-[22rem] h-full overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm flex flex-col">
            <div className="border-b border-gray-100 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-sm font-semibold text-gray-950">模型与智能体</div>
                  <p className="mt-1 text-xs leading-5 text-gray-500">选择通用模型或垂类能力。</p>
                </div>
                <button
                  onClick={() => setRightSidebarVisible(!rightSidebarVisible)}
                  className="rounded-xl p-2 text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900"
                  title={rightSidebarVisible ? '收起侧边栏' : '展开侧边栏'}
                >
                  <ChevronRight size={16} />
                </button>
              </div>
              {selectedAgent && (
                <div className="mt-4 rounded-2xl bg-teal-50 p-3">
                  <div className="flex items-center gap-2 text-sm font-semibold text-teal-800">
                    <Bot size={15} />
                    {activeAgentName}
                  </div>
                  <p className="mt-1 line-clamp-2 text-xs leading-5 text-teal-700">{selectedAgent.description}</p>
                </div>
              )}
            </div>
            {/* 右侧边栏收起/展开按钮 */}

            {/* 智能体库内容 */}
            <div className="flex-1 overflow-hidden">
              <AgentLibrary 
                selectedAgent={selectedAgent}
                onAgentSelect={handleAgentSelect}
                rightSidebarVisible={rightSidebarVisible}
                onToggleRightSidebar={() => setRightSidebarVisible(!rightSidebarVisible)}
              />
            </div>
          </div>
        </div>

        {/* 右侧边栏展开按钮（当侧边栏收起时显示） */}
        {!rightSidebarVisible && (
          <div className="fixed right-4 top-1/2 -translate-y-1/2 z-10">
            <button
              onClick={() => setRightSidebarVisible(true)}
              className="p-2 bg-white border border-gray-200 hover:bg-gray-50 rounded-lg shadow-md transition-colors"
              title="展开右侧边栏"
            >
              <ChevronLeft size={16} className="text-gray-600" />
            </button>
          </div>
        )}
      </div>
      

      
      {/* 充值弹窗 */}
      <RechargeModal 
        isOpen={showRechargeModal} 
        onClose={() => setShowRechargeModal(false)} 
      />
    </div>
  );
};

export default ZhibiYiyan;
