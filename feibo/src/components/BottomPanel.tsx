import React, { Suspense, lazy, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { v4 as uuidv4 } from 'uuid';
import { 
  Upload, 
  Play, 
  Pause, 
  Volume2, 
  Mic, 
  FileAudio, 
  Clock,
  Type,
  Settings,
  Save,
  Eye,
  Zap,
  Headphones,
  Loader2
} from 'lucide-react';
import { useCurrentProject, useSelectedVoice, useVoiceSpeed, useVoiceVolume, useActions, useStore } from '../store';
import { auditionVoice, composeVideo, buildVideoComposeRequest, pollVideoComposeResult, downloadVideo, createTemplate, VideoComposeBusinessError } from '../services/digitalHumanApi';
import { voiceDataService } from '../services/voiceDataService';
import { API_CONFIG } from '../config/api';
import { ComposeRunGuard } from '../services/composeRunGuard';
import EditorLoadingFallback from '@/features/editor/components/EditorLoadingFallback';

const AudioSelectionModal = lazy(() => import('./AudioSelectionModal'));
const CopywritingSelectionModal = lazy(() => import('./CopywritingSelectionModal'));
const ConfirmationModal = lazy(() => import('./ConfirmationModal'));
const SaveDraftModal = lazy(() => import('./SaveDraftModal'));
const VideoComposeProgress = lazy(() => import('./VideoComposeProgress'));

interface BottomPanelProps {
  actionPortalId?: string;
}

const BottomPanel: React.FC<BottomPanelProps> = ({ actionPortalId }) => {
  const currentProject = useCurrentProject();
  const selectedVoiceId = useSelectedVoice();
  const voiceSpeed = useVoiceSpeed();
  const voiceVolume = useVoiceVolume();
  const [activeTab, setActiveTab] = useState<'text' | 'audio'>('text');
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(80);
  const [script, setScript] = useState('');
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [isAudioModalOpen, setIsAudioModalOpen] = useState(false);
  const [selectedAudioFile, setSelectedAudioFile] = useState<any>(null);
  const [isPreviewConfirmOpen, setIsPreviewConfirmOpen] = useState(false);
  const [isSynthesisConfirmOpen, setIsSynthesisConfirmOpen] = useState(false);
  const [voiceValidationMessage, setVoiceValidationMessage] = useState('');
  const [isProgressVisible, setIsProgressVisible] = useState(false);
  const [composeProgress, setComposeProgress] = useState(0);
  const [composeStatus, setComposeStatus] = useState<'pending' | 'processing' | 'completed' | 'suc' | 'failed' | 'fail'>('pending');
  const [currentTaskId, setCurrentTaskId] = useState<string>('');
  const [videoUrl, setVideoUrl] = useState<string>('');
  const [coverUrl, setCoverUrl] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [isSaveConfirmOpen, setIsSaveConfirmOpen] = useState(false);
  const [saveName, setSaveName] = useState('');
  const [isAudioPreviewing, setIsAudioPreviewing] = useState(false);
  const [actionPortalElement, setActionPortalElement] = useState<HTMLElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const audioRef = useRef<HTMLAudioElement>(null);
  const composeRunGuardRef = useRef(new ComposeRunGuard());


  // 获取当前页面
  const currentPage = currentProject?.pages.find(p => p.id === currentProject.currentPageId);

  /**
   * 判断当前画布是否为竖屏模式
   * 函数级注释：根据页面的纵横比（aspectRatio），当为 '9:16' 时视为竖屏。
   * @param page 当前页面对象
   * @returns 布尔值：true 为竖屏，false 为横屏
   */
  const isPortraitCanvas = (page?: any): boolean => {
    const aspectRatio = page?.aspectRatio || '16:9';
    return aspectRatio === '9:16';
  };

  /**
   * 生成按钮类名（含禁用态样式）
   * 函数级注释：当 disabled 为 true 时，追加半透明和禁止光标样式。
   * @param base 基础类名字符串
   * @param disabled 是否禁用
   * @returns 拼接后的类名
   */
  const getButtonClasses = (base: string, disabled: boolean): string => {
    return disabled ? `${base} opacity-50 cursor-not-allowed` : base;
  };

  React.useEffect(() => {
    if (currentPage) {
      setScript(currentPage.script || '');
      setDuration(currentPage.duration || 5000);
    }
  }, [currentPage]);

  React.useEffect(() => {
    if (!actionPortalId) {
      setActionPortalElement(null);
      return;
    }

    setActionPortalElement(document.getElementById(actionPortalId));
  }, [actionPortalId]);

  const handleScriptChange = (value: string) => {
    setScript(value);
    // 实时更新当前页面的脚本到store
    const { actions } = useStore.getState();
    actions.updatePageScript(value);
  };

  const handleAudioUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file && file.type.startsWith('audio/')) {
      setAudioFile(file);
      
      // 创建音频URL用于播放
      const audioUrl = URL.createObjectURL(file);
      if (audioRef.current) {
        audioRef.current.src = audioUrl;
        audioRef.current.onloadedmetadata = () => {
          setDuration(audioRef.current!.duration * 1000);
        };
      }
    }
  };

  const handlePlayPause = () => {
    if (audioRef.current) {
      if (isPlaying) {
        audioRef.current.pause();
      } else {
        audioRef.current.play();
      }
      setIsPlaying(!isPlaying);
    }
  };

  const handleVolumeChange = (value: number) => {
    setVolume(value);
    if (audioRef.current) {
      audioRef.current.volume = value / 100;
    }
  };

  const formatTime = (ms: number) => {
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
  };

  /**
   * 处理“选择文案”按钮点击
   * - 打开文案选择弹窗，从智核医审中挑选已提交审核的文案
   */
  const [isCopywritingModalOpen, setIsCopywritingModalOpen] = useState(false);
  const handleGenerateVoice = () => {
    setIsCopywritingModalOpen(true);
  };
  /**
   * 回填选中的文案到脚本输入框
   * @param content 选择的文案正文
   * @param title 可选标题，仅用于日志或后续扩展
   */
  const handleSelectCopywriting = (content: string, title?: string) => {
    console.log('选择文案:', { hasTitle: Boolean(title), length: content?.length || 0 });
    handleScriptChange(content || '');
  };

  /**
   * 停止当前试听并恢复按钮初始状态。
   * 停止服务端返回的试听音频。
   */
  const stopAudioPreview = () => {
    const audio = audioRef.current;
    if (audio) {
      audio.pause();
      try {
        audio.currentTime = 0;
      } catch {
        // 音频元数据尚未加载时无需重置进度。
      }
    }
    window.speechSynthesis?.cancel();
    setIsPlaying(false);
    setIsAudioPreviewing(false);
  };

  /**
   * 经登录态后端代理试听，浏览器不接触第三方 Key/Secret。
   */
  const handleAudioPreview = async () => {
    if (isAudioPreviewing) {
      if (isPlaying) stopAudioPreview();
      return;
    }

    const text = script.trim();
    if (!text) {
      alert('请输入要试听的文本内容');
      return;
    }

    setIsAudioPreviewing(true);
    try {
      const voiceOptions = voiceDataService.getVoiceOptions();
      const selectedVoice = voiceOptions.find(
        voice => voice.id === selectedVoiceId || voice.magicSpeakerId === selectedVoiceId
      );
      const speaker = selectedVoice?.magicSpeakerId || selectedVoiceId;
      if (!speaker?.trim()) {
        throw new Error('请先选择可用的配音声音');
      }
      const result = await auditionVoice({
        text: text.slice(0, 500),
        speaker,
        audioType: 'mp3',
        speed: Math.max(0.5, Math.min(2.0, Number(voiceSpeed.toFixed(1)))),
        volume: Math.max(0.1, Math.min(1.0, voiceVolume / 100)),
        pitch: 0
      });

      if (!result.url || !audioRef.current) {
        throw new Error('试听接口未返回音频地址');
      }

      const audio = audioRef.current;
      audio.pause();
      audio.src = result.url;
      const finish = () => {
        setIsPlaying(false);
        setIsAudioPreviewing(false);
      };
      audio.onended = finish;
      audio.onerror = () => {
        finish();
        alert('试听音频加载失败，请稍后重试');
      };
      await audio.play();
      setIsPlaying(true);
    } catch (error) {
      stopAudioPreview();
      alert(error instanceof Error ? `试听失败：${error.message}` : '试听失败，请稍后重试');
    }
  };

  const isAuditionLoading = isAudioPreviewing && !isPlaying;
  const isAuditionDisabled = activeTab === 'audio' || isAuditionLoading;
  const auditionButtonTitle = activeTab === 'audio'
    ? '语音驱动模式下不可用'
    : isAuditionLoading
      ? '正在加载试听音频'
      : isPlaying
        ? '停止试听'
        : '试听';

  const handleAuditionButtonClick = () => {
    if (activeTab === 'audio' || isAuditionLoading) return;
    if (isPlaying) {
      stopAudioPreview();
      return;
    }
    void handleAudioPreview().catch(error => {
      console.error('[试听] 点击处理异常', error);
      stopAudioPreview();
    });
  };

  const renderAuditionButtonContent = () => {
    if (isAuditionLoading) {
      return (
        <>
          <Loader2 size={14} className="animate-spin sm:w-4 sm:h-4" />
          <span className="whitespace-nowrap">加载中</span>
        </>
      );
    }
    if (isPlaying) {
      return (
        <>
          <Pause size={14} className="sm:w-4 sm:h-4" />
          <span className="whitespace-nowrap">停止</span>
        </>
      );
    }
    return (
      <>
        <Headphones size={14} className="sm:w-4 sm:h-4" />
        <span className="whitespace-nowrap">试听</span>
      </>
    );
  };

  // 打开音频选择模态窗口
  const handleOpenAudioModal = () => {
    setIsAudioModalOpen(true);
  };

  // 关闭音频选择模态窗口
  const handleCloseAudioModal = () => {
    setIsAudioModalOpen(false);
  };

  // 选择音频文件
  const handleSelectAudioFile = async (audioFile: any) => {
    setSelectedAudioFile(audioFile);
    setActiveTab('audio');
    console.log('选择音频文件，是否包含远端地址:', Boolean(audioFile?.url));
    
    // 如果有音频URL，创建一个模拟的File对象用于显示
    if (audioFile && audioFile.url) {
      try {
        // 从URL获取音频数据并创建File对象
        const response = await fetch(audioFile.url);
        const blob = await response.blob();
        const file = new File([blob], audioFile.name, { type: 'audio/wav' });
        
        // 设置audioFile状态以更新UI显示
        setAudioFile(file);
        
        // 设置音频元素的src用于播放
        if (audioRef.current) {
          audioRef.current.src = audioFile.url;
          audioRef.current.onloadedmetadata = () => {
            setDuration(audioRef.current!.duration * 1000);
          };
        }
      } catch (error) {
        console.error('创建音频文件对象失败:', error);
        // 如果创建File对象失败，至少设置一个基本的显示状态
        const mockFile = {
          name: audioFile.name,
          size: audioFile.size || 0,
        } as File;
        setAudioFile(mockFile);
      }
    }
  };

  const resolveMagicSpeakerId = async (voiceId: string): Promise<string | null> => {
    const normalizedVoiceId = voiceId.trim();
    if (!normalizedVoiceId) return null;

    const voiceOptions = await voiceDataService.loadVoiceData();
    const libraryVoice = voiceOptions.find(
      voice => voice.id === normalizedVoiceId || voice.magicSpeakerId === normalizedVoiceId
    );
    if (libraryVoice?.magicSpeakerId?.trim()) {
      return libraryVoice.magicSpeakerId.trim();
    }

    const customVoice = useStore.getState().myModels.find(
      model => model.magicAudioId?.trim() === normalizedVoiceId
    );
    return customVoice?.magicAudioId?.trim() || null;
  };

  const validateComposePrerequisites = async (): Promise<
    { valid: true; magicSpeakerId: string } |
    { valid: false; message: string; field?: 'voice' }
  > => {
    const state = useStore.getState();
    const project = state.currentProject;
    if (!project) return { valid: false, message: '没有当前项目' };

    const page = project.pages.find(item => item.id === project.currentPageId);
    if (!page) return { valid: false, message: '没有当前页面' };
    if (!page.elements.some(element => element.type === 'digital-human')) {
      return { valid: false, message: '请先添加数字人到画布' };
    }

    if (activeTab === 'audio') {
      if (!audioFile || !selectedAudioFile?.url) {
        return { valid: false, message: '请选择有效的音频文件' };
      }
      return { valid: true, magicSpeakerId: '' };
    }

    if (!script.trim()) {
      return { valid: false, message: '请输入要合成的文本内容' };
    }

    const pageVoiceId = typeof page.voiceId === 'string' ? page.voiceId.trim() : '';
    if (!pageVoiceId) {
      return {
        valid: false,
        field: 'voice',
        message: '尚未选择配音，请前往右侧「配音」面板选择声音后再合成。'
      };
    }

    const magicSpeakerId = await resolveMagicSpeakerId(pageVoiceId);
    if (!magicSpeakerId) {
      return {
        valid: false,
        field: 'voice',
        message: '当前配音已失效或不可用，请重新选择配音后再合成。'
      };
    }

    return { valid: true, magicSpeakerId };
  };

  const showValidationError = (result: { message: string; field?: 'voice' }) => {
    if (result.field === 'voice') {
      setVoiceValidationMessage(result.message);
      return;
    }
    alert(result.message);
  };

  const handleGoToVoicePanel = () => {
    window.dispatchEvent(new CustomEvent('feibo:open-editor-tab', { detail: { tab: 'voice' } }));
  };

  // 处理预览按钮点击：校验通过后才显示确认框
  const handlePreviewClick = async () => {
    const validation = await validateComposePrerequisites();
    if (validation.valid === false) {
      showValidationError(validation);
      return;
    }
    setIsPreviewConfirmOpen(true);
  };

  // 处理合成按钮点击：校验通过后才显示扣费确认框
  const handleSynthesisClick = async () => {
    const validation = await validateComposePrerequisites();
    if (validation.valid === false) {
      showValidationError(validation);
      return;
    }
    setIsSynthesisConfirmOpen(true);
  };

  // 确认预览
  const handleConfirmPreview = async () => {
    setIsPreviewConfirmOpen(false);
    await handleVideoCompose(true); // 预览模式
  };

  // 确认合成
  const handleConfirmSynthesis = async () => {
    setIsSynthesisConfirmOpen(false);
    await handleVideoCompose(false); // 正式合成模式
  };

  // 视频合成处理函数
  const handleVideoCompose = async (isPreview: boolean = false) => {
    let composeRunId: number | undefined;

    try {
      const state = useStore.getState();
      const currentProject = state.currentProject;
      
      if (!currentProject) {
        throw new Error('没有当前项目');
      }

      const currentPage = currentProject.pages.find(p => p.id === currentProject.currentPageId);
      if (!currentPage) {
        throw new Error('没有当前页面');
      }

      // 确认框打开后页面状态仍可能变化，提交前再次校验并解析真实发音人ID。
      const validation = await validateComposePrerequisites();
      if (validation.valid === false) {
        showValidationError(validation);
        return;
      }

      // 查找数字人元素
      const digitalHumanElement = currentPage.elements.find(el => el.type === 'digital-human');
      if (!digitalHumanElement) {
        throw new Error('请先添加数字人到画布');
      }

      // 根据驱动模式获取内容
      let textContent = '';
      
      if (activeTab === 'text') {
        // 文本驱动模式：检查文本内容
        textContent = script.trim();
        if (!textContent) {
          throw new Error('请输入要合成的文本内容');
        }
      } else if (activeTab === 'audio') {
        // 语音驱动模式：检查音频文件
        if (!audioFile) {
          throw new Error('请选择音频文件');
        }
        // 语音驱动模式下，文本内容可以为空或使用默认值
        textContent = script.trim() || '语音驱动合成';
      } else {
        throw new Error('请选择驱动模式');
      }
      
      // 预览模式下截取前80个字符
      if (isPreview && textContent.length > 80) {
        textContent = textContent.substring(0, 80);
      }

      // 获取数字人信息
      // 支持从公用模型（digitalHumans）和我的模型（myModels）中查找数字人
      const digitalHumanId = digitalHumanElement.properties?.digitalHumanId || digitalHumanElement.properties?.digitalHuman?.id;

      // 添加调试日志
      console.log('=== 数字人查找调试信息 ===');
      console.log('数字人元素属性已读取');
      console.log('提取的digitalHumanId:', digitalHumanId);
      console.log('state.digitalHumans数量:', state.digitalHumans?.length || 0);
      console.log('state.myModels数量:', state.myModels?.length || 0);

      let digitalHuman = (state.digitalHumans || []).filter(Boolean).find(dh => dh?.id === digitalHumanId);
      console.log('在公用模型中查找结果:', digitalHuman ? '找到' : '未找到');

      if (!digitalHuman) {
        // 如果在公用模型中没找到，尝试从我的模型中查找
        digitalHuman = (state.myModels || []).filter(Boolean).find(dh => dh?.id === digitalHumanId);
        console.log('在我的模型中查找结果:', digitalHuman ? '找到' : '未找到');
      }

      console.log('最终查找结果:', digitalHuman ? '成功找到数字人' : '查找失败');
      console.log('=== 数字人查找调试信息结束 ===');

      if (!digitalHuman) {
        throw new Error('找不到对应的数字人信息');
      }

      // 重置进度状态（仅在通过校验后再显示弹窗）
      // 每次提交使用独立 run id。旧任务仍可在后台完成并保存作品，但不能
      // 再更新后续任务正在使用的进度弹窗。
      composeRunId = composeRunGuardRef.current.begin();
      composeRunGuardRef.current.runIfCurrent(composeRunId, () => {
        setComposeProgress(0);
        setComposeStatus('pending');
        setCurrentTaskId('');
        setVideoUrl('');
        setCoverUrl('');
        setErrorMessage('');
      });

      // 获取正确的画布尺寸
      const aspectRatio = currentPage.aspectRatio || '16:9';
      let canvasWidth, canvasHeight;
      if (aspectRatio === '9:16') {
        canvasWidth = 1080;
        canvasHeight = 1920;
      } else {
        canvasWidth = 1920;
        canvasHeight = 1080;
      }

      const selectedMagicSpeakerId = validation.magicSpeakerId;

      // 构建合成请求（通过校验后再显示合成进度弹窗）
      composeRunGuardRef.current.runIfCurrent(composeRunId, () => {
        setIsProgressVisible(true);
      });
      const pageVoiceSpeed = (currentPage.voiceSpeed !== undefined ? currentPage.voiceSpeed : voiceSpeed);
      const pageVoiceVolume = (currentPage.voiceVolume !== undefined ? currentPage.voiceVolume : voiceVolume);
      const composeRequest = buildVideoComposeRequest({
        name: `${isPreview ? '预览_' : ''}${currentProject.name}_${Date.now()}`,
        width: canvasWidth,
        height: canvasHeight,
        elements: currentPage.elements,
        digitalHuman: digitalHuman,
        text: textContent,
        magicSpeakerId: selectedMagicSpeakerId, // 使用选中发音人的magicSpeakerId
        voiceSpeed: pageVoiceSpeed, // 传递页面语速
        voiceVolume: pageVoiceVolume / 100, // 传递页面音量（0-1）
        isPreview: isPreview,
        subtitleConfig: currentPage.subtitleConfig, // 传递页面级别的字幕配置
        driveMode: activeTab === 'audio' ? 'audio' : 'text', // 根据activeTab传递驱动模式
        audioFile: activeTab === 'audio' ? selectedAudioFile : null // 在语音驱动模式下传递音频文件
      });

      console.log('发送合成请求');

      const response = await composeVideo(composeRequest);
      console.log('视频合成接口响应状态:', response.code);
      const isSuccess = response.code === '0000';
      
      if (isSuccess) {
        const taskId = response.data;
        composeRunGuardRef.current.runIfCurrent(composeRunId, () => {
          setCurrentTaskId(taskId);
          setComposeStatus('processing');
        });
        
        // 开始轮询查询结果
        try {
          const result = await pollVideoComposeResult(
            taskId,
            (progress, status) => {
              composeRunGuardRef.current.runIfCurrent(composeRunId!, () => {
                setComposeProgress(progress);
                setComposeStatus(status as any);
              });
            }
          );
          
          // 合成完成
          composeRunGuardRef.current.runIfCurrent(composeRunId, () => {
            setComposeStatus(result.status === 'suc' ? 'suc' : 'completed');
            setComposeProgress(100);
            setVideoUrl(result.resultUrl || result.videoUrl || '');
            setCoverUrl(result.coverUrl || '');
          });
          
          /**
           * 保存作品到用户作品列表
           * 无论是使用主接口还是备用接口，都需要保存作品信息
           * 特别是备用接口合成的作品，需要确保能在"我的作品"中正确显示
           */
          if (!isPreview && (result.status === 'suc' || result.status === 'completed')) {
            const state = useStore.getState();
            const workId = result.id || uuidv4();
            const newWork: import('../types').UserWork = {
              id: workId,
              taskId: taskId,
              name: composeRequest.name,
              videoUrl: result.resultUrl || result.videoUrl,
              coverUrl: result.coverUrl,
              thumbnailUrl: result.thumbnailUrl,
              duration: result.duration,
              width: result.width || canvasWidth,
              height: result.height || canvasHeight,
              status: result.status === 'suc' ? 'suc' : 'completed',
              progress: 100,
              createTime: new Date().toISOString(),
              updateTime: new Date().toISOString(),
              digitalWorkId: workId, // 使用相同的ID作为digitalWorkId
              externalId: result.id || workId, // 使用result.id作为externalId，用于查询视频信息
              title: composeRequest.name,
              body: JSON.stringify({
                name: composeRequest.name,
                width: canvasWidth,
                height: canvasHeight,
                items: [{
                  detailList: composeRequest.items || []
                }],
                interfaceType: 'backend'
              })
            };
            
            try {
              // 保存到本地store，确保作品能在"我的作品"中显示
              state.actions.saveUserWork(newWork);
              console.log('作品已保存到我的作品（后端接口），taskId:', newWork.taskId);
            } catch (error) {
              console.error('保存作品失败:', error);
            }
          }
        } catch (pollError) {
          console.error('轮询过程中发生错误:', pollError);
          // 只有在真正的合成失败时才设置为失败状态
          // 网络错误、超时等不应该导致合成失败
          if (pollError instanceof VideoComposeBusinessError) {
            composeRunGuardRef.current.runIfCurrent(composeRunId, () => {
              setComposeStatus('failed');
              setErrorMessage(pollError.message);
            });
          } else {
            // 对于网络错误等，保持处理状态，让用户可以重试
            console.warn('轮询遇到网络错误，但合成可能仍在进行中:', pollError);
            composeRunGuardRef.current.runIfCurrent(composeRunId, () => {
              setErrorMessage('网络连接异常，请检查合成状态或稍后重试');
            });
          }
        }
      } else {
        // 优先使用info字段，其次使用message字段
        throw new Error(response.info || response.message || `${isPreview ? '预览' : '合成'}失败`);
      }
    } catch (error) {
      console.error('合成失败:', error);
      const failedRunId = composeRunId ?? composeRunGuardRef.current.begin();
      composeRunGuardRef.current.runIfCurrent(failedRunId, () => {
        setComposeStatus('failed');
        setErrorMessage(error instanceof Error ? error.message : '未知错误');
        setIsProgressVisible(true);
      });
    }
  };

  // 处理视频下载
  const handleVideoDownload = async (videoUrl: string) => {
    const filename = `video_${currentTaskId}_${Date.now()}.mp4`;
    await downloadVideo(videoUrl, filename);
  };

  // 关闭进度弹窗
  const handleCloseProgress = () => {
    setIsProgressVisible(false);
  };

  // 处理保存按钮点击
  const handleSaveClick = () => {
    const defaultName = `设计草稿_${new Date().toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    }).replace(/\//g, '-')}`;
    setSaveName(defaultName);
    setIsSaveConfirmOpen(true);
  };

  // 确认保存设计草稿
  const handleConfirmSave = async () => {
    const state = useStore.getState();
    const trimmedName = saveName.trim();
    if (trimmedName) {
      try {
        // 获取当前项目数据
        const currentProject = state.currentProject;
        if (!currentProject) {
          throw new Error('没有当前项目');
        }

        // 将项目数据序列化为JSON字符串作为body
        const projectData = {
          project: currentProject,
          script: script,
          timestamp: new Date().toISOString()
        };
        const body = JSON.stringify(projectData);

        // 调用新的API保存设计草稿
        await createTemplate(trimmedName, body);
        
        // 同时保存到本地store（保持兼容性）
        state.actions.saveDesignDraft(trimmedName);
        
        setIsSaveConfirmOpen(false);
        setSaveName('');
        
        // 显示成功提示
        alert('设计草稿保存成功!');
        console.log('设计草稿保存成功，名称长度:', trimmedName.length);
      } catch (error) {
        console.error('保存设计草稿失败:', error);
        alert('保存失败: ' + (error instanceof Error ? error.message : '未知错误'));
      }
    }
  };

  // 取消保存
  const handleCancelSave = () => {
    setIsSaveConfirmOpen(false);
    setSaveName('');
  };

  const renderActionButtons = (placement: 'footer' | 'toolbar') => {
    const isToolbarPlacement = placement === 'toolbar';
    const showIcons = true;
    const groupClassName = isToolbarPlacement
      ? 'flex w-full flex-col items-stretch gap-2'
      : 'flex items-center justify-center sm:justify-end gap-1.5 sm:gap-2';
    const buttonClassName = isToolbarPlacement
      ? 'flex h-9 w-full flex-shrink-0 items-center justify-center gap-1.5 rounded-lg px-3 text-sm font-semibold tracking-[0.08em] transition-colors'
      : 'flex items-center justify-center space-x-1 sm:space-x-1.5 px-2 sm:px-3 py-2 text-xs sm:text-sm font-medium rounded-lg transition-colors min-w-0 flex-shrink-0';
    const secondaryButtonClassName = `${buttonClassName} bg-cyan-600 hover:bg-cyan-700 text-white`;
    const primaryButtonClassName = `${buttonClassName} bg-teal-600 hover:bg-teal-700 text-white`;

    return (
      <div className={groupClassName}>
        <button
          onClick={handleSaveClick}
          className={secondaryButtonClassName}
        >
          {showIcons && (<Save size={14} className="sm:w-4 sm:h-4" />)}
          <span className="whitespace-nowrap">保存</span>
        </button>

        <button
          onClick={handleAuditionButtonClick}
          disabled={isAuditionDisabled}
          className={getButtonClasses(secondaryButtonClassName, isAuditionDisabled)}
          title={auditionButtonTitle}
          aria-label={auditionButtonTitle}
          aria-pressed={isPlaying}
        >
          {renderAuditionButtonContent()}
        </button>

        <button
          onClick={handlePreviewClick}
          className={secondaryButtonClassName}
        >
          {showIcons && (<Eye size={14} className="sm:w-4 sm:h-4" />)}
          <span className="whitespace-nowrap">预览</span>
        </button>

        <button
          onClick={handleSynthesisClick}
          className={primaryButtonClassName}
        >
          {showIcons && (<Zap size={14} className="sm:w-4 sm:h-4" />)}
          <span className="whitespace-nowrap">合成</span>
        </button>
      </div>
    );
  };

  const footerActions = actionPortalElement
    ? createPortal(renderActionButtons('toolbar'), actionPortalElement)
    : null;

  return (
    <div className="h-full bg-white border-t border-gray-200 flex flex-col m-0 p-0">
      {footerActions}
      {/* 标签页切换 */}
      <div className="flex items-center border-b border-gray-200">
        <div className="flex flex-shrink-0">
          <button
            onClick={() => setActiveTab('text')}
            className={`flex flex-shrink-0 items-center space-x-1.5 px-3 py-3 text-sm font-medium transition-colors ${
              activeTab === 'text'
                ? 'bg-teal-600 text-white border-b-2 border-teal-400'
                : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
            }`}
          >
            <Type size={16} />
            <span className="whitespace-nowrap">文本驱动</span>
          </button>
          <button
            onClick={handleOpenAudioModal}
            className={`flex flex-shrink-0 items-center space-x-1.5 px-3 py-3 text-sm font-medium transition-colors ${
              activeTab === 'audio'
                ? 'bg-teal-600 text-white border-b-2 border-teal-400'
                : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
            }`}
          >
            <FileAudio size={16} />
            <span className="whitespace-nowrap">语音驱动</span>
          </button>
        </div>
        <div className="flex-1"></div>
        <div className="flex flex-shrink-0 items-center px-2 sm:px-4 space-x-2">
          <span className="text-gray-600 text-xs">
            {script.length} 字符
          </span>
          <button
            onClick={handleGenerateVoice}
            className="px-3 py-1 bg-teal-600 hover:bg-teal-700 text-white text-xs rounded transition-colors"
          >选择文案</button>
        </div>
      </div>

      {/* 内容区域 */}
      <div className="flex-1 flex">
        {activeTab === 'text' ? (
          /* 文本编辑区 */
          <div className="flex-1 flex flex-col">
            {/* 文本编辑器标题栏 */}

            
            {/* 文本编辑器 */}
            <div className="flex-1 p-4">
              <textarea
                value={script}
                onChange={(e) => handleScriptChange(e.target.value)}
                placeholder="请输入数字人要说的内容..."
                className="w-full h-full bg-white border border-gray-300 rounded-lg p-3 text-gray-900 placeholder-gray-500 resize-none focus:outline-none focus:border-teal-500 text-sm leading-relaxed"
              />
            </div>
          </div>
        ) : (
          /* 音频控制区 */
          <div className="flex-1 flex flex-col">
            {/* 音频控制标题栏 */}

            
            {/* 音频上传区 */}
            <div className="p-4 border-b border-gray-200">
              <input
                ref={fileInputRef}
                type="file"
                accept="audio/*"
                onChange={handleAudioUpload}
                className="hidden"
              />
              
              {audioFile ? (
                <div className="bg-gray-100 rounded-lg p-3 border border-gray-200">
                  <div className="flex items-center space-x-2 mb-2">
                    <FileAudio size={16} className="text-medical-green-600" />
                    <span className="text-gray-900 text-sm font-medium truncate">
                      {audioFile.name}
                    </span>
                  </div>
                  <div className="text-gray-600 text-xs">
                    {(audioFile.size / 1024 / 1024).toFixed(2)} MB
                  </div>
                </div>
              ) : (
                <button
                  onClick={() => fileInputRef.current?.click()}
                  className="w-full border-2 border-dashed border-gray-300 hover:border-teal-500 rounded-lg p-4 text-center transition-colors group"
                >
                  <Upload size={24} className="mx-auto text-gray-600 group-hover:text-teal-600 mb-2" />
        <div className="text-gray-600 group-hover:text-teal-600 text-sm">
                    点击上传音频文件
                  </div>
                  <div className="text-gray-500 text-xs mt-1">
                    支持 MP3, WAV, M4A 格式
                  </div>
                </button>
              )}
            </div>
            
            {/* 播放控制区 */}

          </div>
        )}
      </div>
      
      {/* 底部按钮栏 */}
      <div className={`border-t border-gray-200 bg-gray-50 px-2 sm:px-4 py-3 ${actionPortalElement ? 'hidden' : ''}`}>
        {/* 响应式布局：窄屏时垂直排列，宽屏时水平排列 */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-end gap-3 sm:gap-0">
          {/* 操作按钮组 */}
          <div className="flex items-center justify-center sm:justify-end gap-1.5 sm:gap-2">
            <button 
              onClick={handleSaveClick}
              className="flex items-center justify-center space-x-1 sm:space-x-1.5 px-2 sm:px-3 py-2 bg-cyan-600 hover:bg-cyan-700 text-white text-xs sm:text-sm font-medium rounded-lg transition-colors min-w-0 flex-shrink-0"
            >
              <Save size={14} className="sm:w-4 sm:h-4" />
              <span className="whitespace-nowrap">保存</span>
            </button>

            {/* 试听按钮：位于保存与预览之间 */}
            <button 
              onClick={handleAuditionButtonClick}
              disabled={isAuditionDisabled}
              className={getButtonClasses("flex items-center justify-center space-x-1 sm:space-x-1.5 px-2 sm:px-3 py-2 bg-cyan-600 hover:bg-cyan-700 text-white text-xs sm:text-sm font-medium rounded-lg transition-colors min-w-0 flex-shrink-0", isAuditionDisabled)}
              title={auditionButtonTitle}
              aria-label={auditionButtonTitle}
              aria-pressed={isPlaying}
            >
              {renderAuditionButtonContent()}
            </button>

            <button 
              onClick={handlePreviewClick}
              className="flex items-center justify-center space-x-1 sm:space-x-1.5 px-2 sm:px-3 py-2 bg-cyan-600 hover:bg-cyan-700 text-white text-xs sm:text-sm font-medium rounded-lg transition-colors min-w-0 flex-shrink-0"
            >
              <Eye size={14} className="sm:w-4 sm:h-4" />
              <span className="whitespace-nowrap">预览</span>
            </button>
            
            <button 
              onClick={handleSynthesisClick}
              className="flex items-center justify-center space-x-1 sm:space-x-1.5 px-2 sm:px-3 py-2 bg-teal-600 hover:bg-teal-700 text-white text-xs sm:text-sm font-medium rounded-lg transition-colors min-w-0 flex-shrink-0"
            >
              <Zap size={14} className="sm:w-4 sm:h-4" />
              <span className="whitespace-nowrap">合成</span>
            </button>
          </div>
        </div>
      </div>
      
      {/* 隐藏的音频元素 */}
      <audio
        ref={audioRef}
        onTimeUpdate={() => {
          if (audioRef.current) {
            setCurrentTime(audioRef.current.currentTime * 1000);
          }
        }}
        onEnded={() => {
          setIsPlaying(false);
          setIsAudioPreviewing(false);
        }}
      />
      
      <Suspense fallback={<EditorLoadingFallback label="正在加载底部弹窗..." className="fixed inset-0 z-50" />}>
        <AudioSelectionModal
          isOpen={isAudioModalOpen}
          onClose={handleCloseAudioModal}
          onSelect={handleSelectAudioFile}
        />

        <CopywritingSelectionModal
          isOpen={isCopywritingModalOpen}
          onClose={() => setIsCopywritingModalOpen(false)}
          onSelect={handleSelectCopywriting}
        />
        
        <ConfirmationModal
          isOpen={Boolean(voiceValidationMessage)}
          onClose={() => setVoiceValidationMessage('')}
          onConfirm={handleGoToVoicePanel}
          title="需要选择配音"
          message={voiceValidationMessage}
          confirmText="去选择配音"
          cancelText="取消"
        />

        <ConfirmationModal
          isOpen={isPreviewConfirmOpen}
          onClose={() => setIsPreviewConfirmOpen(false)}
          onConfirm={handleConfirmPreview}
          title="预览确认"
          message="预览不消耗时长。将仅预览前80个字符，并生成临时视频文件。是否继续预览？"
          confirmText="确认预览"
          cancelText="取消"
        />
        
        <ConfirmationModal
          isOpen={isSynthesisConfirmOpen}
          onClose={() => setIsSynthesisConfirmOpen(false)}
          onConfirm={handleConfirmSynthesis}
          title="合成确认"
          message="合成操作将消耗您的合成时长。根据视频长度和复杂度，将从您的账户余额中扣除相应的合成时长。合成完成后无法撤销此操作。确定要开始合成吗？"
          confirmText="确认合成"
          cancelText="取消"
        />
        
        <VideoComposeProgress
          isVisible={isProgressVisible}
          progress={composeProgress}
          status={composeStatus}
          taskId={currentTaskId}
          videoUrl={videoUrl}
          coverUrl={coverUrl}
          errorMessage={errorMessage}
          onDownload={handleVideoDownload}
          onClose={handleCloseProgress}
        />
        
        <SaveDraftModal
          isOpen={isSaveConfirmOpen}
          onClose={handleCancelSave}
          onConfirm={handleConfirmSave}
          title="保存设计草稿"
          inputValue={saveName}
          onInputChange={setSaveName}
          inputPlaceholder="请输入草稿名称"
          confirmText="确认保存"
          cancelText="取消"
        />
      </Suspense>
    </div>
  );
};

export default BottomPanel;
