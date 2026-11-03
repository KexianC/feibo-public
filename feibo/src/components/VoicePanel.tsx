import React, { useState, useEffect, useRef } from 'react';
import { Play, Pause, Volume2, SlidersHorizontal, Search, X, Heart, Mic, Mic2, User, UserCircle2 } from 'lucide-react';
import { voiceDataService } from '../services/voiceDataService';
import { VoiceOption } from '../utils/excelReader';
import { useSelectedVoice, useVoiceSpeed, useVoiceVolume, useActions, useStore, useCurrentProject } from '../store';
import { auditionVoice } from '../services/digitalHumanApi';

const VoicePanel: React.FC = () => {
  const selectedVoice = useSelectedVoice();
  const speed = useVoiceSpeed();
  const volume = useVoiceVolume();
  const { setSelectedVoice, setVoiceSpeed, setVoiceVolume } = useActions();
  const [isPlaying, setIsPlaying] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'library' | 'mine'>('mine');
  const [selectedModel, setSelectedModel] = useState<string>('standard');
  const [showVoiceLibrary, setShowVoiceLibrary] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedFilter, setSelectedFilter] = useState('all');
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [genderFilter, setGenderFilter] = useState('all');
  const [ageFilter, setAgeFilter] = useState('all');
  const [fieldFilter, setFieldFilter] = useState('all');
  const [styleFilter, setStyleFilter] = useState('all');
  const [languageFilter, setLanguageFilter] = useState('all');
  const [voiceOptions, setVoiceOptions] = useState<VoiceOption[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { myModels, isLoadingMyModels, myModelsError, actions: { loadMyModels } } = useStore();
  const currentProject = useCurrentProject();
  const triedMyModelsRef = useRef(false);
  const demoAudioRef = useRef<HTMLAudioElement>(null);

  const sanitizeUrl = (url?: string) => {
    if (!url) return '';
    let cleaned = url.trim();
    cleaned = cleaned.replace(/^`+|`+$/g, '');
    cleaned = cleaned.replace(/^"+|"+$/g, '');
    cleaned = cleaned.replace(/^'+|'+$/g, '');
    return cleaned;
  };

  // 动态筛选选项
  const [filterOptions, setFilterOptions] = useState({
    genders: [] as string[],
    ages: [] as string[],
    fields: [] as string[],
    styles: [] as string[],
    languages: [] as string[]
  });

  // 基础筛选选项
  const genderOptions = [
    { value: 'all', label: '全部' },
    { value: 'male', label: '男声' },
    { value: 'female', label: '女声' }
  ];

  const getAgeOptions = () => [
    { value: 'all', label: '全部' },
    ...filterOptions.ages.map(age => ({ value: age, label: age }))
  ];

  const getFieldOptions = () => [
    { value: 'all', label: '全部' },
    ...filterOptions.fields.map(field => ({ value: field, label: field }))
  ];

  const getStyleOptions = () => [
    { value: 'all', label: '全部' },
    ...filterOptions.styles.map(style => ({ value: style, label: style }))
  ];

  const getLanguageOptions = () => [
    { value: 'all', label: '全部' },
    ...filterOptions.languages.map(lang => ({ value: lang, label: lang }))
  ];

  // 加载语音数据
  useEffect(() => {
    const loadVoiceData = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        const voices = await voiceDataService.loadVoiceData();
        setVoiceOptions(voices);
        
        // 更新筛选选项
        const options = voiceDataService.getFilterOptions();
        setFilterOptions(options);
        
        // 不自动选择，保留空值以便用户显式选择
      } catch (err) {
        setError('加载语音数据失败');
        console.error('加载语音数据失败:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadVoiceData();
  }, []);

  // 页面切换时同步高亮当前页面绑定的配音
  useEffect(() => {
    const project = currentProject;
    const page = project?.pages.find(p => p.id === project.currentPageId);
    const pageVoiceId = page?.voiceId || null;
    setSelectedVoice(pageVoiceId);
    if (page?.voiceSpeed !== undefined) setVoiceSpeed(page.voiceSpeed);
    if (page?.voiceVolume !== undefined) setVoiceVolume(page.voiceVolume);
  }, [currentProject?.currentPageId]);

  useEffect(() => {
    if (activeTab !== 'mine') return;
    if (triedMyModelsRef.current) return;
    if (isLoadingMyModels) return;
    if (myModelsError) return;
    if (myModels.length === 0) {
      triedMyModelsRef.current = true;
      loadMyModels();
    }
  }, [activeTab, isLoadingMyModels, myModels.length, myModelsError, loadMyModels]);

  const handlePlayDemo = async (voiceId: string) => {
    const text = '欢迎来到飞驳数字医生工作台';
    if (!voiceId.trim()) {
      alert('该模型尚未绑定可用的声音，请先完成声音克隆');
      return;
    }
    if (isPlaying === voiceId) {
      demoAudioRef.current?.pause();
      window.speechSynthesis?.cancel();
      setIsPlaying(null);
      return;
    }

    try {
      const voices = voiceDataService.getVoiceOptions();
      const voice = voices.find(item => item.id === voiceId || item.magicSpeakerId === voiceId);
      const speaker = voice?.magicSpeakerId || voiceId;
      const result = await auditionVoice({
        text,
        speaker,
        audioType: 'mp3',
        speed: Math.max(0.5, Math.min(2.0, Number(speed))),
        volume: Math.max(0.1, Math.min(1.0, volume / 100)),
        pitch: 0
      });

      if (!result.url || !demoAudioRef.current) {
        throw new Error('试听接口未返回音频地址');
      }

      const audio = demoAudioRef.current;
      audio.pause();
      audio.src = result.url;
      audio.onended = () => setIsPlaying(null);
      audio.onerror = () => {
        setIsPlaying(null);
        alert('试听音频加载失败，请稍后重试');
      };
      await audio.play();
      setIsPlaying(voiceId);
    } catch (error) {
      demoAudioRef.current?.pause();
      setIsPlaying(null);
      alert(error instanceof Error ? `试听失败：${error.message}` : '试听失败，请稍后重试');
    }
  };

  const handleToggleFavorite = (voiceId: string) => {
    const newFavorites = new Set(favorites);
    if (newFavorites.has(voiceId)) {
      newFavorites.delete(voiceId);
    } else {
      newFavorites.add(voiceId);
    }
    setFavorites(newFavorites);
  };

  const filteredVoices = voiceDataService.getFilteredVoiceOptions({
    searchTerm,
    genderFilter,
    ageFilter,
    fieldFilter,
    styleFilter,
    languageFilter
  });



  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* 标题 */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="text-gray-900 font-semibold text-sm">配音设置</h3>
      </div>

      {/* 内容区域 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* 语音参数 */}
        <div className="space-y-4">
          <h4 className="text-gray-700 text-sm font-medium">语音参数</h4>
          
          {/* 语速 */}
          <div>
            <label className="block text-gray-600 text-xs mb-2">语速</label>
            <div className="flex items-center gap-3">
              <input
                type="range"
                min="0.5"
                max="2.0"
                step="0.1"
                value={speed}
                onChange={(e) => setVoiceSpeed(Number(e.target.value))}
                className="flex-1 h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
              />
              <span className="text-gray-900 text-sm w-12">{speed}x</span>
            </div>
          </div>



          {/* 音量 */}
          <div>
            <label className="block text-gray-600 text-xs mb-2">音量</label>
            <div className="flex items-center gap-3">
              <Volume2 size={16} className="text-gray-500" />
              <input
                type="range"
                min="0"
                max="100"
                value={volume}
                onChange={(e) => setVoiceVolume(Number(e.target.value))}
                className="flex-1 h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
              />
              <span className="text-gray-900 text-sm w-12">{volume}%</span>
            </div>
          </div>
        </div>

        {/* 语音选择 */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <label className="block text-gray-700 text-sm">选择语音</label>
            <div className="flex bg-gray-100 rounded-lg p-1">
              <button
                onClick={() => setActiveTab('library')}
                className={`px-3 py-1 text-xs rounded-md transition-colors ${
                  activeTab === 'library'
                    ? 'bg-white text-gray-900 shadow-sm'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                <Mic size={12} className="inline mr-1" />
                语音库
              </button>
              <button
                onClick={() => setActiveTab('mine')}
                className={`px-3 py-1 text-xs rounded-md transition-colors ${
                  activeTab === 'mine'
                    ? 'bg-white text-gray-900 shadow-sm'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                <User size={12} className="inline mr-1" />
                我的
              </button>
            </div>
          </div>
          
          {activeTab === 'library' && (
            <div className="mb-4">
              <label className="block text-gray-600 text-xs mb-2">语音选择</label>
              <button
                onClick={() => setShowVoiceLibrary(true)}
                className="w-full px-3 py-2 bg-white border border-gray-300 rounded text-gray-900 text-sm hover:border-gray-400 transition-colors flex items-center justify-between"
              >
                <span>高级筛选</span>
                <SlidersHorizontal size={16} className="text-gray-500" />
              </button>
            </div>
          )}
          
          <div className="space-y-2">
            {isLoading ? (
              <div className="text-center py-8 text-gray-500">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto mb-4"></div>
                <p className="text-sm">加载语音数据中...</p>
              </div>
            ) : error ? (
              <div className="text-center py-8 text-red-500">
                <p className="text-sm">{error}</p>
                <button 
                  onClick={() => window.location.reload()}
                  className="mt-3 px-4 py-2 bg-red-600 text-white text-xs rounded-lg hover:bg-red-700 transition-colors"
                >
                  重新加载
                </button>
              </div>
            ) : activeTab === 'library' ? (
              voiceOptions.map((voice) => (
                <div
                  key={voice.id}
                  onClick={() => setSelectedVoice(voice.id)}
                  className={`p-3 rounded-lg border cursor-pointer transition-colors ${
                    selectedVoice === voice.id
                      ? 'border-emerald-500 bg-emerald-50'
                      : 'border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-gray-900 font-medium">{voice.name}</span>
                        <span className={`px-2 py-0.5 text-xs rounded-full ${
                          voice.gender === 'female' ? 'bg-pink-100 text-pink-700' : 'bg-blue-100 text-blue-700'
                        }`}>
                          {voice.gender === 'female' ? '女声' : '男声'}
                        </span>
                        {voice.age && (
                          <span className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-600">
                            {voice.age}
                          </span>
                        )}
                      </div>
                      <div className="text-xs text-gray-500 mt-1">
                        {voice.language} · {voice.style}
                        {voice.field && ` · ${voice.field}`}
                      </div>
                    </div>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handlePlayDemo(voice.id);
                      }}
                      className="p-2 text-gray-500 hover:text-emerald-600 transition-colors flex-shrink-0"
                      title="试听"
                    >
                      {isPlaying === voice.id ? (
                        <Pause size={16} />
                      ) : (
                        <Play size={16} />
                      )}
                    </button>
                  </div>
                </div>
              ))
            ) : (
              <>
                {isLoadingMyModels ? (
                  <div className="text-center py-8 text-gray-500">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto mb-4"></div>
                    <p className="text-sm">加载我的模型中...</p>
                  </div>
                ) : myModelsError ? (
                  <div className="text-center py-8 text-red-500">
                    <p className="text-sm">{myModelsError}</p>
                    <button 
                      onClick={() => loadMyModels()}
                      className="mt-3 px-4 py-2 bg-red-600 text-white text-xs rounded-lg hover:bg-red-700 transition-colors"
                    >重新加载</button>
                  </div>
                ) : myModels.length === 0 ? (
                  <div className="text-center py-8 text-gray-500">
                    <User size={48} className="mx-auto mb-4 opacity-50" />
                    <p className="text-sm">暂无自定义语音</p>
                  </div>
                ) : (
                  myModels.filter(m => {
                    const s = (m.magicAudioId || '').trim().toLowerCase();
                    return s !== 'm091_meet_24k' && s !== 'moqianxue_meet_24k';
                  }).map(model => (
                    <div
                      key={model.id}
                      onClick={() => setSelectedVoice(model.magicAudioId || '')}
                      className={`p-3 rounded-lg border cursor-pointer transition-colors ${
                        selectedVoice === (model.magicAudioId || '')
                          ? 'border-emerald-500 bg-emerald-50'
                          : 'border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <img
                          src={sanitizeUrl(model.avatar || model.speakerUrl)}
                          alt={model.name}
                          className="w-10 h-10 rounded-full object-cover"
                          onError={(e) => { (e.currentTarget as HTMLImageElement).src = '/img/飞驳.jpg'; }}
                        />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-gray-900 font-medium">{model.name}</span>
                            {model.gender && (
                              <span className={`px-2 py-0.5 text-xs rounded-full ${model.gender === 'female' ? 'bg-pink-100 text-pink-700' : 'bg-blue-100 text-blue-700'}`}>{model.gender === 'female' ? '女' : '男'}</span>
                            )}
                          </div>
                          <div className="text-xs text-gray-500">语音ID: {model.magicAudioId || '-'}</div>
                        </div>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handlePlayDemo(model.magicAudioId || '');
                          }}
                          className="p-2 text-gray-500 hover:text-emerald-600 transition-colors ml-auto flex-shrink-0"
                          title="试听"
                        >
                          {isPlaying === (model.magicAudioId || '') ? (
                            <Pause size={16} />
                          ) : (
                            <Play size={16} />
                          )}
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </>
            )}
          </div>
        </div>


      </div>

      {/* 配音库弹窗 */}
      {showVoiceLibrary && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg w-[90vw] h-[80vh] max-w-6xl flex flex-col">
            {/* 弹窗标题栏 */}
            <div className="flex items-center justify-between p-4 border-b border-gray-200">
              <h2 className="text-lg font-semibold text-gray-900">配音库</h2>
              <button
                onClick={() => setShowVoiceLibrary(false)}
                className="p-1 text-gray-500 hover:text-gray-700 transition-colors"
              >
                <X size={20} />
              </button>
            </div>

            {/* 搜索和筛选区域 */}
            <div className="p-4 border-b border-gray-200">
              {/* 搜索框 */}
              <div className="flex items-center gap-4 mb-4">
                <div className="flex-1 relative">
                  <Search size={16} className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" />
                  <input
                    type="text"
                    placeholder="输入人姓名"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-blue-500"
                  />
                </div>
                <button className="px-4 py-2 bg-teal-600 text-white rounded-lg text-sm hover:bg-teal-700 transition-colors">
                  搜索
                </button>
              </div>

              {/* 筛选下拉框 */}
              <div className="grid grid-cols-5 gap-3 mb-4">
                {/* 性别筛选 */}
                <div>
                  <label className="block text-xs text-gray-600 mb-1">性别</label>
                  <select
                    value={genderFilter}
                    onChange={(e) => setGenderFilter(e.target.value)}
                    className="w-full px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:border-blue-500"
                  >
                    {genderOptions.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* 年龄筛选 */}
                <div>
                  <label className="block text-xs text-gray-600 mb-1">年龄</label>
                  <select
                    value={ageFilter}
                    onChange={(e) => setAgeFilter(e.target.value)}
                    className="w-full px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:border-blue-500"
                  >
                    {getAgeOptions().map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* 领域筛选 */}
                <div>
                  <label className="block text-xs text-gray-600 mb-1">领域</label>
                  <select
                    value={fieldFilter}
                    onChange={(e) => setFieldFilter(e.target.value)}
                    className="w-full px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:border-blue-500"
                  >
                    {getFieldOptions().map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* 风格筛选 */}
                <div>
                  <label className="block text-xs text-gray-600 mb-1">风格</label>
                  <select
                    value={styleFilter}
                    onChange={(e) => setStyleFilter(e.target.value)}
                    className="w-full px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:border-blue-500"
                  >
                    {getStyleOptions().map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* 语种筛选 */}
                <div>
                  <label className="block text-xs text-gray-600 mb-1">语种</label>
                  <select
                    value={languageFilter}
                    onChange={(e) => setLanguageFilter(e.target.value)}
                    className="w-full px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:border-blue-500"
                  >
                    {getLanguageOptions().map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* 收藏夹按钮 */}
              <div className="flex justify-end mb-4">
                <button className="px-4 py-2 bg-cyan-600 text-white rounded-lg text-sm hover:bg-cyan-700 transition-colors flex items-center gap-2">
                  <Heart size={14} />
                  收藏夹
                </button>
              </div>
            </div>

            {/* 语音卡片网格 */}
            <div className="flex-1 overflow-y-auto p-4">
              <div className="grid grid-cols-4 gap-4">
                {filteredVoices.map((voice) => (
                  <div
                    key={voice.id}
                    className="relative bg-white border border-gray-200 rounded-lg p-4 hover:border-blue-300 transition-colors cursor-pointer"
                    onClick={() => {
                      setSelectedVoice(voice.id);
                      setShowVoiceLibrary(false);
                    }}
                  >
                    {/* 收藏按钮 */}
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleToggleFavorite(voice.id);
                      }}
                      className="absolute top-2 right-2 p-1 text-gray-400 hover:text-red-500 transition-colors"
                    >
                      <Heart
                        size={16}
                        className={favorites.has(voice.id) ? 'fill-red-500 text-red-500' : ''}
                      />
                    </button>

                    {/* 头像 */}
                    <div className="w-16 h-16 bg-gradient-to-br from-blue-400 to-purple-500 rounded-full mx-auto mb-3 flex items-center justify-center">
                      <span className="text-white font-medium text-lg">
                        {voice.name.charAt(0)}
                      </span>
                    </div>

                    {/* 姓名 */}
                    <h3 className="text-center font-medium text-gray-900 mb-1">
                      {voice.name}
                    </h3>

                    {/* 标签 */}
                    <div className="text-center text-xs text-gray-500">
                      {voice.language} | {voice.style}
                    </div>

                    {/* 性别标签 */}
                    <div className="flex justify-center mt-2">
                      <span className={`px-2 py-0.5 text-xs rounded-full ${
                        voice.gender === 'female' ? 'bg-pink-100 text-pink-700' : 'bg-blue-100 text-blue-700'
                      }`}>
                        {voice.gender === 'female' ? '女声' : '男声'}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
      <audio ref={demoAudioRef} />
    </div>
  );
};

export default VoicePanel;
