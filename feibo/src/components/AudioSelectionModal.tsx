import React, { useState, useRef, useEffect } from 'react';
import { X, Plus, Mic, Play, Pause, Trash2, Clock4, FileText } from 'lucide-react';
import ModalPortal from './ModalPortal';
import { uploadRecording, getUserRecordings } from '../services/fileUploadApi';

// 合成只接受服务端已识别并保存的有效时长。
const hasRecognizedDuration = (duration: unknown): duration is number =>
  typeof duration === 'number' && Number.isFinite(duration) && duration > 0 && duration <= 86400;

interface AudioFile {
  id: string;
  name: string;
  duration: number;
  size: number;
  url: string;
  createdAt: Date;
}

interface AudioSelectionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (audioFile: AudioFile) => void;
}

const AudioSelectionModal: React.FC<AudioSelectionModalProps> = ({
  isOpen,
  onClose,
  onSelect
}) => {
  const [audioFiles, setAudioFiles] = useState<AudioFile[]>([]);
  const [isLoadingRecordings, setIsLoadingRecordings] = useState(false);
  const [hasMoreRecordings, setHasMoreRecordings] = useState(true);
  const [lastCursorId, setLastCursorId] = useState('');
  
  const [isRecording, setIsRecording] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [playingId, setPlayingId] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<AudioFile | null>(null);
  
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const recordingIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const audioElementsRef = useRef<{ [key: string]: HTMLAudioElement }>({});

  // 格式化时间显示
  const formatTime = (seconds: number) => {
    const wholeSeconds = Math.floor(seconds);
    const mins = Math.floor(wholeSeconds / 60);
    const secs = wholeSeconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // 格式化文件大小
  const formatFileSize = (bytes: number) => {
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  };

  // 开始录音
  const startRecording = async () => {
    try {
      // 检查浏览器兼容性
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        throw new Error('BROWSER_NOT_SUPPORTED');
      }

      // 检查是否为HTTPS环境（本地开发环境除外）
      if (location.protocol !== 'https:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
        throw new Error('HTTPS_REQUIRED');
      }

      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mediaRecorder = new MediaRecorder(stream);
      
      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];
      
      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };
      
      mediaRecorder.onstop = async () => {
        const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/wav' });
        
        // 创建文件对象用于上传
        const fileName = new Date().toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit'
        }).replace(/\//g, '-') + '.wav';
        
        const file = new File([audioBlob], fileName, { type: 'audio/wav' });
        
        try {
          // 使用统一的上传接口
          const response = await uploadRecording(file);
          if (response.success && response.data) {
            // 上传成功后刷新录音列表
            await loadRecordings(true);
          } else {
            throw new Error(response.info || '录音上传失败，请稍后重试');
          }
        } catch (error) {
          console.error('录音上传失败:', error);
          alert(error instanceof Error && error.message ? error.message : '录音上传失败，请稍后重试');
        }
        
        setRecordingTime(0);
        
        // 停止所有音轨
        stream.getTracks().forEach(track => track.stop());
      };
      
      mediaRecorder.start();
      setIsRecording(true);
      
      // 开始计时
      recordingIntervalRef.current = setInterval(() => {
        setRecordingTime(prev => prev + 1);
      }, 1000);
      
    } catch (error) {
      console.error('录音失败:', error);
      
      let errorMessage = '录音失败，请检查麦克风权限';
      
      if (error instanceof Error) {
        switch (error.message) {
          case 'BROWSER_NOT_SUPPORTED':
            errorMessage = '您的浏览器不支持录音功能，请使用Chrome、Firefox、Safari等现代浏览器';
            break;
          case 'HTTPS_REQUIRED':
            errorMessage = '录音功能需要在HTTPS环境下使用，请确保网站使用安全连接';
            break;
          default:
            if (error.name === 'NotAllowedError') {
              errorMessage = '麦克风权限被拒绝，请在浏览器设置中允许麦克风访问权限';
            } else if (error.name === 'NotFoundError') {
              errorMessage = '未检测到麦克风设备，请检查麦克风是否正确连接';
            } else if (error.name === 'NotReadableError') {
              errorMessage = '麦克风被其他应用占用，请关闭其他使用麦克风的应用后重试';
            } else if (error.name === 'OverconstrainedError') {
              errorMessage = '麦克风不支持所需的音频格式，请尝试使用其他麦克风设备';
            }
            break;
        }
      }
      
      alert(errorMessage);
    }
  };

  // 停止录音
  const stopRecording = () => {
    if (mediaRecorderRef.current && isRecording) {
      mediaRecorderRef.current.stop();
      setIsRecording(false);
      
      if (recordingIntervalRef.current) {
        clearInterval(recordingIntervalRef.current);
        recordingIntervalRef.current = null;
      }
    }
  };

  // 上传音频文件
  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file && file.type.startsWith('audio/')) {
      try {
        // 使用统一的上传接口
        const response = await uploadRecording(file);
        if (response.success && response.data) {
          // 上传成功后刷新录音列表
          await loadRecordings(true);
        } else {
          throw new Error(response.info || '音频文件上传失败，请稍后重试');
        }
      } catch (error) {
        console.error('录音上传失败:', error);
        alert(error instanceof Error && error.message ? error.message : '音频文件上传失败，请稍后重试');
      }
    }
    
    // 重置文件输入
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // 播放/暂停音频
  const togglePlayAudio = (audioFile: AudioFile) => {
    const audioId = audioFile.id;
    
    if (playingId === audioId) {
      // 暂停当前播放的音频
      if (audioElementsRef.current[audioId]) {
        audioElementsRef.current[audioId].pause();
        setPlayingId(null);
      }
    } else {
      // 停止其他正在播放的音频
      Object.values(audioElementsRef.current).forEach(audio => {
        audio.pause();
        audio.currentTime = 0;
      });
      
      // 播放选中的音频
      if (!audioElementsRef.current[audioId]) {
        const audio = new Audio(audioFile.url);
        audio.onended = () => setPlayingId(null);
        audioElementsRef.current[audioId] = audio;
      }
      
      audioElementsRef.current[audioId].play();
      setPlayingId(audioId);
    }
  };

  // 删除音频文件
  const deleteAudioFile = (audioId: string) => {
    setAudioFiles(prev => prev.filter(file => file.id !== audioId));
    setSelectedFile(prev => prev?.id === audioId ? null : prev);
    
    // 清理音频元素
    if (audioElementsRef.current[audioId]) {
      audioElementsRef.current[audioId].pause();
      delete audioElementsRef.current[audioId];
    }
    
    if (playingId === audioId) {
      setPlayingId(null);
    }
  };

  // 选择音频文件
  const handleSelectAudio = (audioFile: AudioFile) => {
    if (!hasRecognizedDuration(audioFile.duration)) return;
    setSelectedFile(audioFile);
  };

  // MedNote导入处理
  const handleMedNoteImport = () => {
    // TODO: 实现MedNote导入逻辑
    console.log('MedNote导入功能');
  };

  // 确认选择
  const handleConfirm = () => {
    if (selectedFile && hasRecognizedDuration(selectedFile.duration)) {
      onSelect(selectedFile);
      onClose();
    }
  };

  // 加载用户录音列表
  const loadRecordings = async (reset: boolean = false) => {
    if (isLoadingRecordings || (!hasMoreRecordings && !reset)) return;
    
    setIsLoadingRecordings(true);
    try {
      const cursorId = reset ? '' : lastCursorId;
      const response = await getUserRecordings(cursorId, 10);
      
      if (response.data && response.data.data) {
        const recordings: AudioFile[] = response.data.data.map((item, index) => ({
          id: `recording_${cursorId}_${index}`,
          name: item.name || `录音_${new Date().toLocaleString()}`,
          duration: item.duration ?? 0,
          size: 1024 * 1024, // 默认大小
          url: item.url,
          createdAt: item.createTime ? new Date(item.createTime) : new Date()
        }));
        
        if (reset) {
          setAudioFiles(recordings);
          setSelectedFile(null);
        } else {
          setAudioFiles(prev => [...prev, ...recordings]);
        }
        
        setHasMoreRecordings(response.data.hasNext);
        setLastCursorId(response.data.nextCursorId || '');
      }
    } catch (error) {
      console.error('加载录音列表失败:', error);
    } finally {
      setIsLoadingRecordings(false);
    }
  };

  // 初始化加载录音列表
  useEffect(() => {
    if (isOpen) {
      loadRecordings(true);
    }
  }, [isOpen]);

  // 清理资源
  useEffect(() => {
    return () => {
      if (recordingIntervalRef.current) {
        clearInterval(recordingIntervalRef.current);
      }
      
      Object.values(audioElementsRef.current).forEach(audio => {
        audio.pause();
      });
    };
  }, []);

  if (!isOpen) return null;

  return (
    <ModalPortal>
      <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-6">
      <div className="my-auto flex h-[600px] max-h-[calc(100vh-2rem)] w-full max-w-[800px] flex-col overflow-hidden rounded-lg bg-white shadow-xl">
        {/* 标题栏 */}
        <div className="flex flex-shrink-0 items-center justify-between border-b border-gray-200 p-4">
          <h2 className="text-lg font-semibold text-gray-900">音频驱动合成视频</h2>
          <button
            onClick={onClose}
            className="p-1 hover:bg-gray-100 rounded transition-colors"
          >
            <X size={20} className="text-gray-500" />
          </button>
        </div>

        {/* 操作按钮区 */}
        <div className="flex flex-shrink-0 items-center justify-between border-b border-gray-200 p-4">
          <div className="flex space-x-3">
            {/* 添加音频按钮 */}
            <button
              onClick={() => fileInputRef.current?.click()}
              className="flex items-center space-x-2 px-4 py-2 bg-teal-600 hover:bg-teal-700 text-white rounded-lg transition-colors"
            >
              <Plus size={16} />
              <span>添加音频</span>
            </button>
            
            {/* 录音按钮 */}
            <button
              onClick={isRecording ? stopRecording : startRecording}
              className={`flex items-center space-x-2 px-4 py-2 rounded-lg transition-colors ${
                isRecording
                  ? 'bg-red-600 hover:bg-red-700 text-white'
                  : 'bg-cyan-600 hover:bg-cyan-700 text-white'
              }`}
            >
              <Mic size={16} />
              <span>{isRecording ? `录音中 ${formatTime(recordingTime)}` : '在线录音'}</span>
            </button>
            
            {/* MedNote导入按钮 */}
            <button
              onClick={handleMedNoteImport}
              className="flex items-center space-x-2 px-4 py-2 bg-teal-600 hover:bg-teal-700 text-white rounded-lg transition-colors"
            >
              <FileText size={16} />
              <span>MedNote导入</span>
            </button>
          </div>
        </div>

        {/* 音频列表 */}
        <div className="flex-1 p-4 overflow-y-auto">
          {isLoadingRecordings && audioFiles.length === 0 ? (
            <div className="flex items-center justify-center h-32">
              <div className="text-gray-500">加载录音列表中...</div>
            </div>
          ) : audioFiles.length === 0 ? (
            <div className="flex items-center justify-center h-32">
              <div className="text-gray-500">暂无录音文件，请添加或录制音频</div>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-3 gap-4">
                {audioFiles.map((audioFile) => (
                  <div
                    key={audioFile.id}
                    className={`border rounded-lg p-3 transition-all ${hasRecognizedDuration(audioFile.duration) ? 'cursor-pointer' : ''} ${
                      selectedFile?.id === audioFile.id
                        ? 'border-teal-500 bg-teal-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                    onClick={() => handleSelectAudio(audioFile)}
                  >
                {/* 音频图标和信息 */}
                <div className="flex items-center space-x-3 mb-3">
                  <div className="w-12 h-12 bg-teal-600 rounded-lg flex items-center justify-center">
                    <svg className="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 24 24">
                      <path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z" />
                    </svg>
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium text-gray-900 truncate">
                      {audioFile.name}
                    </div>
                    <div className="flex items-center space-x-2 text-xs text-gray-500">
                      <Clock4 size={12} />
                      <span className={hasRecognizedDuration(audioFile.duration) ? undefined : 'text-red-500'}>
                        {hasRecognizedDuration(audioFile.duration) ? formatTime(audioFile.duration) : '时长识别失败'}
                      </span>
                    </div>
                  </div>
                </div>

                {/* 操作按钮 */}
                <div className="flex items-center justify-between">
                  <div className="flex space-x-2">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        deleteAudioFile(audioFile.id);
                      }}
                      className="p-1 text-gray-400 hover:text-red-500 transition-colors"
                      title="删除"
                    >
                      <Trash2 size={14} />
                    </button>
                    
                    {audioFile.url && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          togglePlayAudio(audioFile);
                        }}
                        className="p-1 text-gray-400 hover:text-teal-600 transition-colors"
                        title={playingId === audioFile.id ? "暂停" : "播放"}
                      >
                        {playingId === audioFile.id ? <Pause size={14} /> : <Play size={14} />}
                      </button>
                    )}
                  </div>
                  
                  <button
                    disabled={!hasRecognizedDuration(audioFile.duration)}
                    title={hasRecognizedDuration(audioFile.duration) ? undefined : '时长识别失败，请重新上传音频'}
                    onClick={(e) => {
                      e.stopPropagation();
                      handleSelectAudio(audioFile);
                    }}
                    className="px-3 py-1 text-xs bg-gray-100 enabled:hover:bg-teal-100 text-gray-600 enabled:hover:text-teal-600 disabled:opacity-50 disabled:cursor-not-allowed rounded transition-colors"
                  >
                    使用
                  </button>
                </div>
              </div>
            ))}
          </div>
          
          {/* 加载更多按钮 */}
          {hasMoreRecordings && (
            <div className="flex justify-center mt-4">
              <button
                onClick={() => loadRecordings(false)}
                disabled={isLoadingRecordings}
                className="px-4 py-2 text-sm text-teal-600 hover:text-teal-700 disabled:text-gray-400 transition-colors"
              >
                {isLoadingRecordings ? '加载中...' : '加载更多'}
              </button>
            </div>
          )}
        </>
      )}
    </div>

        {/* 底部按钮 */}
        <div className="flex flex-shrink-0 items-center justify-end space-x-3 border-t border-gray-200 p-4">
          <button
            onClick={onClose}
            className="px-4 py-2 text-gray-600 hover:text-gray-800 transition-colors"
          >
            取消
          </button>
          <button
            onClick={handleConfirm}
            disabled={!selectedFile || !hasRecognizedDuration(selectedFile.duration)}
            className={`px-6 py-2 rounded-lg transition-colors ${
              selectedFile && hasRecognizedDuration(selectedFile.duration)
                ? 'bg-teal-600 hover:bg-teal-700 text-white'
                : 'bg-gray-300 text-gray-500 cursor-not-allowed'
            }`}
          >
            使用
          </button>
        </div>

        {/* 隐藏的文件输入 */}
        <input
          ref={fileInputRef}
          type="file"
          accept="audio/*"
          onChange={handleFileUpload}
          className="hidden"
        />
      </div>
      </div>
    </ModalPortal>
  );
};

export default AudioSelectionModal;
