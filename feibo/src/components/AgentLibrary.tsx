import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Bot, Stethoscope, Layers, Loader2, ChevronDown } from 'lucide-react';
import { getAllAIShardingConfigs, AIShardingConfig, getAIModels, AIModelConfig } from '../services/aiShardingApi';

interface Agent {
  id: string;
  name: string;
  avatar: string;
  description: string;
  category: 'general' | 'vertical' | 'agent';
  specialty?: string;
  aiModelConfigId?: string; // AI模型配置ID - 使用字符串避免大整数精度丢失
  type?: boolean | null; // 思考能力标识
  image?: string | null; // 模型图片URL
  childrenList?: Agent[] | null; // 子版本列表
  isExpanded?: boolean; // 是否展开子版本
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
interface AgentLibraryProps {
  selectedAgent: Agent | null;
  onAgentSelect: (agent: Agent, modelType: string) => void;
  rightSidebarVisible: boolean;
  onToggleRightSidebar: () => void;
}

const AgentLibrary: React.FC<AgentLibraryProps> = ({ selectedAgent, onAgentSelect }) => {
  const [activeCategory, setActiveCategory] = useState<'general' | 'vertical' | 'agent'>('general');
  const [generalAgents, setGeneralAgents] = useState<Agent[]>([]);
  const [verticalAgents, setVerticalAgents] = useState<Agent[]>([]);
  const [agentModels, setAgentModels] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedVersions, setSelectedVersions] = useState<{[key: string]: Agent}>({});
  const [forceUpdate, setForceUpdate] = useState(0);
  const updateLockRef = useRef(false); // 状态更新锁，防止竞争条件

  // 监听selectedVersions变化并强制重新渲染（移除以避免状态竞争）
  // useEffect(() => {
  //   console.log('selectedVersions状态变化，强制重新渲染:', selectedVersions);
  //   setForceUpdate(prev => prev + 1);
  // }, [selectedVersions]);

  /**
   * 初始化选中版本状态，确保每个有子版本的智能体都有默认选中的版本
   * 只在真正需要初始化时执行，避免覆盖用户手动选择
   * @param agents - 智能体列表
   * @param forceReset - 是否强制重置（仅在首次加载时使用）
   */
  const initializeSelectedVersions = (agents: Agent[], forceReset: boolean = false) => {
    const initialVersions: {[key: string]: Agent} = {};
    agents.forEach(agent => {
      if (agent.childrenList && agent.childrenList.length > 0) {
        // 只有在强制重置或当前没有选中版本时才初始化
        if (forceReset || !selectedVersions[agent.id]) {
          initialVersions[agent.id] = agent.childrenList[0];
          console.log('初始化默认版本:', {
            agentId: agent.id,
            agentName: agent.name,
            defaultVersion: agent.childrenList[0].name,
            forceReset
          });
        }
      }
    });
    
    if (Object.keys(initialVersions).length > 0) {
      setSelectedVersions(prev => ({
        ...prev,
        ...initialVersions
      }));
    }
  };

  /**
   * 转换AIModelConfig为Agent格式，确保大整数ID保持字符串格式
   * @param model - AI模型配置
   * @param category - 模型分类
   * @param parentImage - 父级图片URL
   * @param parentName - 父级模型名称
   * @returns Agent对象
   */
  const convertModelToAgent = (model: AIModelConfig, category: 'general' | 'vertical' | 'agent', parentImage?: string | null, parentName?: string): Agent => {
    // 确定使用的图片：优先使用自己的 image，其次使用父级 image；缺失时显示名称首字
    const imageUrl = model.image || parentImage || '';
    
    const agent: Agent = {
      id: model.aiModelConfigId,
      name: model.name,
      description: model.description,
      category,
      avatar: imageUrl,
      aiModelConfigId: model.aiModelConfigId,
      type: model.type,
      image: model.image,
      isExpanded: false,
      parentName: parentName // 设置父级模型名称
    };

    // 如果有子节点，递归转换，传递当前模型的image作为父级图片和名称
    if (model.childrenList && model.childrenList.length > 0) {
      agent.childrenList = model.childrenList.map(child => convertModelToAgent(child, category, model.image || parentImage, model.name));
    }

    return agent;
  };

  // 加载通用模型数据
  const loadGeneralAgents = async () => {
    if (loading) return;
    
    setLoading(true);
    setError(null);
    
    try {
      const models = await getAIModels();
      const generalCategory = models.find(category => category.name === '通用模型');
      
      if (generalCategory?.childrenList) {
        // Keep both provider and version order exactly as returned by the
        // backend. Each provider category becomes one card; its children are
        // rendered as the version dropdown in that same order.
        const agents = generalCategory.childrenList.map(model => convertModelToAgent(model, 'general'));
        setGeneralAgents(agents);
        // 初始化选中版本状态（首次加载时强制重置）
        initializeSelectedVersions(agents, true);
      } else {
        setGeneralAgents([]);
      }
    } catch (err) {
      console.error('Failed to load general agents:', err);
      setError('加载通用模型失败，请稍后重试');
      setGeneralAgents([]);
    } finally {
      setLoading(false);
    }
  };

  // 加载垂类模型数据
  const loadVerticalAgents = async () => {
    if (loading) return;
    
    setLoading(true);
    setError(null);
    
    try {
      const configs = await getAllAIShardingConfigs();
      const agents: Agent[] = configs.map((config: AIShardingConfig) => ({
        id: config.id,
        name: config.title,
        description: config.content,
        category: 'vertical' as const,
        avatar: config.avatar || '',
        aiModelConfigId: config.aiShardingConfigId // 保持字符串格式，避免精度丢失
      }));
      setVerticalAgents(agents);
    } catch (err) {
      console.error('Failed to load vertical agents:', err);
      setError('加载垂类模型失败，请稍后重试');
      
      // 如果API调用失败，使用备用数据
      const fallbackAgents: Agent[] = [];
      setVerticalAgents(fallbackAgents);
    } finally {
      setLoading(false);
    }
  };

  // 加载数智医生模型数据
  const loadAgentModels = async () => {
    if (loading) return;
    
    setLoading(true);
    setError(null);
    
    try {
      const models = await getAIModels();
      const agentCategory = models.find(category => category.name === '智能体');
      
      if (agentCategory?.childrenList) {
        const agents = agentCategory.childrenList.map(model => convertModelToAgent(model, 'agent'));
        setAgentModels(agents);
        // 初始化选中版本状态（首次加载时强制重置）
        initializeSelectedVersions(agents, true);
      } else {
        setAgentModels([]);
      }
    } catch (err) {
      console.error('Failed to load agent models:', err);
      setError('加载数智医生模型失败，请稍后重试');
      setAgentModels([]);
    } finally {
      setLoading(false);
    }
  };

  // 当切换分类时加载对应数据
  useEffect(() => {
    if (activeCategory === 'general' && generalAgents.length === 0) {
      loadGeneralAgents();
    } else if (activeCategory === 'vertical' && verticalAgents.length === 0) {
      loadVerticalAgents();
    } else if (activeCategory === 'agent' && agentModels.length === 0) {
      loadAgentModels();
    }
  }, [activeCategory]);

  // 组件初始化时加载默认分类数据
  useEffect(() => {
    // 初始化时加载通用模型数据
    if (generalAgents.length === 0) {
      loadGeneralAgents();
    }
  }, []);



  // 初始化默认版本选择（仅在首次加载且没有选中智能体时执行）
  useEffect(() => {
    if (generalAgents.length > 0 && !selectedAgent) {
      console.log('AgentLibrary: 自动选择第一个智能体');
      const firstAgent = generalAgents[0];
      console.log('AgentLibrary: 第一个智能体:', firstAgent);
      
      if (firstAgent.childrenList && firstAgent.childrenList.length > 0) {
        // 如果有子版本，选择第一个子版本
        const firstVersion = firstAgent.childrenList[0];
        console.log('AgentLibrary: 选择第一个子版本:', firstVersion);
        const finalModelType = resolveModelConfigId(firstVersion.aiModelConfigId, firstVersion.id);
        if (finalModelType) {
          console.log('AgentLibrary: 子版本的aiModelConfigId:', finalModelType);
          onAgentSelect(firstVersion, finalModelType);
        } else {
          setError('模型配置异常，请刷新后重试');
        }
      } else {
        // 如果没有子版本，直接选择该模型
        console.log('AgentLibrary: 直接选择该模型');
        const finalModelType = resolveModelConfigId(firstAgent.aiModelConfigId, firstAgent.id);
        if (finalModelType) {
          console.log('AgentLibrary: 模型的aiModelConfigId:', finalModelType);
          onAgentSelect(firstAgent, finalModelType);
        } else {
          setError('模型配置异常，请刷新后重试');
        }
      }
    }
  }, [generalAgents, selectedAgent, onAgentSelect]);

  /**
   * 处理版本选择，支持鼠标事件和变更事件，使用锁机制防止竞争条件
   * @param parentAgent - 父级智能体
   * @param version - 选中的版本
   * @param event - 事件对象（支持鼠标事件和变更事件）
   */
  const handleVersionSelect = useCallback((parentAgent: Agent, version: Agent, event?: React.MouseEvent | React.ChangeEvent) => {
    if (event) {
      event.stopPropagation();
    }
    
    const finalModelType = resolveModelConfigId(version.aiModelConfigId, version.id);
    if (!finalModelType) {
      setError('模型配置异常，请刷新后重试');
      return;
    }

    // 检查更新锁，防止并发更新
    if (updateLockRef.current) {
      console.log('状态更新被锁定，跳过此次更新');
      return;
    }
    
    // 设置更新锁
    updateLockRef.current = true;
    
    console.log('版本选择开始:', {
      parentAgentId: parentAgent.id,
      parentAgentName: parentAgent.name,
      selectedVersionId: version.id,
      selectedVersionName: version.name,
      aiModelConfigId: version.aiModelConfigId,
      timestamp: Date.now()
    });
    
    // 使用函数式setState确保状态更新的原子性
    setSelectedVersions(prev => {
      const newState = {
        ...prev,
        [parentAgent.id]: version
      };
      console.log('状态更新完成:', {
        previousState: prev[parentAgent.id]?.name || 'none',
        newState: version.name,
        timestamp: Date.now()
      });
      return newState;
    });
    
    // 延迟执行forceUpdate和解锁，确保状态更新完成
    setTimeout(() => {
      setForceUpdate(prev => {
        const newValue = prev + 1;
        console.log('强制重新渲染:', newValue);
        return newValue;
      });
      
      // 解除更新锁
      updateLockRef.current = false;
      console.log('状态更新锁已解除');
    }, 10); // 增加延迟时间确保状态更新完成
    
    // 选择该版本，确保modelType为字符串格式
    console.log('最终选择的模型类型:', finalModelType);
    onAgentSelect(version, finalModelType);
  }, [onAgentSelect]);

  /**
   * 处理智能体选择，确保modelType参数为字符串格式
   * @param agent - 选中的智能体
   * @param modelType - 模型类型（可选）
   */
  const handleAgentSelect = (agent: Agent, modelType?: string) => {
    // 如果是通用模型且有子版本，选择当前选中的版本
    if (agent.category === 'general' && agent.childrenList && agent.childrenList.length > 0) {
      const selectedVersion = selectedVersions[agent.id] || agent.childrenList[0];
      const finalModelType = resolveModelConfigId(selectedVersion.aiModelConfigId, selectedVersion.id);
      if (finalModelType) {
        onAgentSelect(selectedVersion, finalModelType);
      } else {
        setError('模型配置异常，请刷新后重试');
      }
      return;
    }

    // 确定模型类型，确保为字符串格式
    const finalModelType = resolveModelConfigId(agent.aiModelConfigId, agent.id, modelType);
    if (finalModelType) {
      onAgentSelect(agent, finalModelType);
    } else {
      setError('模型配置异常，请刷新后重试');
    }
  };



  // 根据分类筛选智能体
  const getFilteredAgents = () => {
    if (activeCategory === 'general') {
      return generalAgents; // 使用API获取的通用模型数据
    } else if (activeCategory === 'vertical') {
      return verticalAgents; // 使用API获取的垂类模型数据
    } else if (activeCategory === 'agent') {
      return agentModels; // 使用API获取的数智医生模型数据
    }
    return [];
  };
  
  const filteredAgents = getFilteredAgents();

  // 渲染智能体项目
  const renderAgentItem = (agent: Agent, level: number = 0) => {
    const isSelected = selectedAgent?.id === agent.id || 
      (agent.childrenList && selectedVersions[agent.id] && selectedAgent?.id === selectedVersions[agent.id].id);
    const hasChildren = agent.childrenList && agent.childrenList.length > 0;
    
    return (
      <div key={agent.id}>
        <div
          onClick={() => handleAgentSelect(agent)}
          className={`p-3 rounded-2xl border cursor-pointer transition-all duration-200 hover:shadow-sm ${
            level > 0 ? 'ml-4 border-l-2 border-l-teal-200' : ''
          } ${
            isSelected
              ? 'border-teal-500/80 bg-teal-50 shadow-sm'
              : 'border-gray-200 bg-white hover:border-teal-300 hover:bg-teal-50'
          }`}
        >
          <div className="flex items-start gap-3">
            {/* 头像 */}
            <div className="w-10 h-10 rounded-xl bg-white border border-gray-200 flex items-center justify-center text-gray-700 text-lg font-medium flex-shrink-0 overflow-hidden p-1">
              {agent.avatar ? (
                <img 
                  src={agent.avatar} 
                  alt={agent.name}
                  className="w-full h-full object-contain rounded-lg"
                  onError={(e) => {
                    const target = e.target as HTMLImageElement;
                    target.style.display = 'none';
                    const nextSibling = target.nextElementSibling as HTMLElement;
                    if (nextSibling) {
                      nextSibling.style.display = 'flex';
                    }
                  }}
                />
              ) : null}
              <span className={`w-full h-full flex items-center justify-center ${agent.avatar ? 'hidden' : ''}`}>
                {agent.name.charAt(0)}
              </span>
            </div>
            
            {/* 信息 */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1 flex-wrap">
                <h3 className={`font-medium text-gray-900 truncate ${
                  level > 0 ? 'text-sm' : ''
                }`}>
                  {agent.name}
                </h3>
                {agent.type === true && (
                  <span className="px-2 py-0.5 text-xs bg-purple-100 text-purple-700 rounded-full">
                    思考
                  </span>
                )}
                {agent.specialty && (
                  <span className="px-2 py-0.5 text-xs bg-teal-100 text-teal-700 rounded-full">
                    {agent.specialty}
                  </span>
                )}
                {hasChildren && (
                  <div className="relative flex-shrink-0">
                    <select
                      key={`${agent.id}-${selectedVersions[agent.id]?.id || 'default'}-${forceUpdate}`}
                      value={(() => {
                        // 确保value值在childrenList中存在
                        const currentSelected = selectedVersions[agent.id];
                        const defaultFirst = agent.childrenList?.[0];
                        
                        if (currentSelected && agent.childrenList?.some(child => child.id === currentSelected.id)) {
                          console.log('使用当前选中版本:', currentSelected.name, currentSelected.id);
                          return currentSelected.id;
                        } else if (defaultFirst) {
                          console.log('使用默认第一个版本:', defaultFirst.name, defaultFirst.id);
                          return defaultFirst.id;
                        }
                        return '';
                      })()}
                      onChange={(e) => {
                        console.log('下拉框onChange触发:', {
                          selectedValue: e.target.value,
                          agentId: agent.id,
                          agentName: agent.name,
                          currentSelectedVersion: selectedVersions[agent.id],
                          childrenList: agent.childrenList?.map(child => ({ id: child.id, name: child.name })),
                          forceUpdateValue: forceUpdate
                        });
                        
                        const selectedVersion = agent.childrenList?.find(child => child.id === e.target.value);
                        if (selectedVersion) {
                          console.log('找到匹配的版本:', selectedVersion);
                          handleVersionSelect(agent, selectedVersion, e);
                        } else {
                          console.error('未找到匹配的版本:', e.target.value);
                        }
                      }}
                      onClick={(e) => {
                        console.log('下拉框onClick触发, 当前value:', e.currentTarget.value, 'forceUpdate:', forceUpdate);
                        e.stopPropagation();
                      }}
                      className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded-full border-none outline-none cursor-pointer hover:bg-gray-200 transition-colors appearance-none pr-6 max-w-[120px] truncate"
                      style={{ minWidth: '80px', maxWidth: '120px' }}
                      title={selectedVersions[agent.id]?.name || agent.childrenList?.[0]?.name || ''}
                    >
                      {agent.childrenList?.map(version => (
                        <option key={version.id} value={version.id} title={version.name}>
                          {version.name}
                        </option>
                      ))}
                    </select>
                    <ChevronDown 
                      size={12} 
                      className="absolute right-1 top-1/2 transform -translate-y-1/2 text-gray-400 pointer-events-none"
                    />
                  </div>
                )}
              </div>
              <p className={`text-gray-600 line-clamp-2 ${
                level > 0 ? 'text-xs' : 'text-sm'
              }`}>
                {hasChildren && selectedVersions[agent.id] 
                  ? selectedVersions[agent.id].description 
                  : agent.description}
              </p>
            </div>
          </div>
          
          {/* 选中状态指示器 */}
          {isSelected && (
            <div className="mt-2 flex items-center gap-1 text-xs text-teal-600">
              <div className="w-2 h-2 bg-teal-500 rounded-full"></div>
              <span>当前选中</span>
            </div>
          )}
        </div>

      </div>
    );
  };

  const getCategoryIcon = (category: 'general' | 'vertical' | 'agent') => {
    switch (category) {
      case 'general':
        return <Bot size={16} />;
      case 'vertical':
        return <Layers size={16} />;
      case 'agent':
        return <Stethoscope size={16} />;
      default:
        return <Bot size={16} />;
    }
  };

  return (
    <div className="w-full bg-transparent flex flex-col h-full">
      {/* 分类 */}
      <div className="border-b border-gray-100 p-3">
        <div className="grid grid-cols-2 gap-1 rounded-2xl bg-gray-100 p-1">
          {(['general', 'vertical'] as const).map(category => (
            <button
              key={category}
              type="button"
              onClick={() => setActiveCategory(category)}
              className={`flex min-w-0 items-center justify-center gap-1 rounded-xl px-2 py-2 text-xs font-semibold transition-colors ${
                activeCategory === category
                  ? 'bg-white text-teal-700 shadow-sm'
                  : 'text-gray-500 hover:text-gray-900'
              }`}
              title={category === 'general' ? '通用模型' : '垂类模型'}
            >
              {getCategoryIcon(category)}
              <span className="truncate">{category === 'general' ? '通用' : '垂类'}</span>
            </button>
          ))}
        </div>
      </div>

      {/* 智能体列表 */}
      <div 
        className="flex-1 overflow-y-auto overflow-x-hidden p-3 scrollbar-thin scrollbar-thumb-gray-300 scrollbar-track-gray-100 hover:scrollbar-thumb-gray-400" 
      >
        {/* Loading状态 */}
        {loading && (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="w-6 h-6 animate-spin text-teal-600" />
            <span className="ml-2 text-gray-600">加载中...</span>
          </div>
        )}
        
        {/* 错误状态 */}
        {error && (
          <div className="text-center py-8">
            <div className="text-red-500 mb-2">{error}</div>
            <button 
              onClick={activeCategory === 'general' ? loadGeneralAgents : loadVerticalAgents}
              className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
            >
              重新加载
            </button>
          </div>
        )}
        
        {/* 智能体列表 */}
        {!loading && !error && (
          <div className="space-y-3">
            {filteredAgents.map((agent) => renderAgentItem(agent))}
            
            {/* 更多模型提示 */}
            <div className="text-center py-4 text-gray-500 text-sm">
              更多模型接入中
            </div>
          </div>
        )}
      </div>

      {/* 底部信息 */}
      <div className="p-4 border-t border-gray-100 bg-gray-50/80">
        <div className="text-xs text-gray-500 text-center">
          共 {filteredAgents.length} 个{activeCategory === 'general' ? '通用模型' : '垂类模型'}智能体
        </div>
      </div>
    </div>
  );
};

export default AgentLibrary;
