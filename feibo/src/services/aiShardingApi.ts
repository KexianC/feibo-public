import { authenticatedRequest } from './authApi';

// AI模型配置数据类型
export interface AIModelConfig {
  aiModelConfigId: string;
  name: string;
  description: string;
  type: boolean | null; // true: 思考模型, false: 普通模型, null: 分类节点
  image?: string | null; // 模型图片URL
  childrenList: AIModelConfig[] | null;
}

// API响应数据类型
export interface AIModelResponse {
  code: string;
  info: string;
  data: AIModelConfig[];
}

// 兼容旧接口的AI分库配置类型
export interface AIShardingConfig {
  id: string;
  aiShardingConfigId: string;
  title: string;
  content: string;
  avatar: string | null;
}

// 游标分页响应类型（保留兼容性）
export interface ApiCursorPageData<T> {
  data: T[];
  hasNext: boolean;
  nextCursor?: string;
  total?: number;
}

// 查询参数类型（保留兼容性）
export interface QueryShardingConfigParams {
  lastCursorId?: string | null;
  size: number;
}

type UnknownRecord = Record<string, unknown>;

const MAX_MODEL_TREE_DEPTH = 32;

const isRecord = (value: unknown): value is UnknownRecord => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const normalizeModelBusinessId = (value: unknown, path: string): string => {
  if (typeof value === 'string') {
    const normalized = value.trim();
    if (/^[1-9]\d*$/.test(normalized)) {
      return normalized;
    }
  }

  // Legacy responses may still contain small numeric IDs. A JSON number is
  // accepted only while it is provably exact; a 19-digit numeric ID has
  // already lost precision by the time JavaScript sees it and must be rejected.
  if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) {
    return String(value);
  }

  throw new Error(`AI 模型数据格式错误：${path}.aiModelConfigId 必须是精确的正整数字符串`);
};

const normalizeModelNode = (value: unknown, path: string, depth: number): AIModelConfig => {
  if (depth > MAX_MODEL_TREE_DEPTH || !isRecord(value)) {
    throw new Error(`AI 模型数据格式错误：${path} 不是有效的模型节点`);
  }

  const name = typeof value.name === 'string' ? value.name.trim() : '';
  if (!name) {
    throw new Error(`AI 模型数据格式错误：${path}.name 不能为空`);
  }

  const description = value.description === undefined || value.description === null
    ? ''
    : value.description;
  if (typeof description !== 'string') {
    throw new Error(`AI 模型数据格式错误：${path}.description 必须是字符串`);
  }

  const rawType = value.type;
  if (rawType !== undefined && rawType !== null && typeof rawType !== 'boolean') {
    throw new Error(`AI 模型数据格式错误：${path}.type 必须是布尔值或 null`);
  }
  const type: boolean | null = typeof rawType === 'boolean' ? rawType : null;

  const rawImage = value.image;
  if (rawImage !== undefined && rawImage !== null && typeof rawImage !== 'string') {
    throw new Error(`AI 模型数据格式错误：${path}.image 必须是字符串或 null`);
  }
  const image: string | null = typeof rawImage === 'string' ? rawImage : null;

  const rawChildren = value.childrenList;
  if (rawChildren !== undefined && rawChildren !== null && !Array.isArray(rawChildren)) {
    throw new Error(`AI 模型数据格式错误：${path}.childrenList 必须是数组或 null`);
  }
  const childrenList = Array.isArray(rawChildren)
    ? rawChildren.map((child, index) => normalizeModelNode(child, `${path}.childrenList[${index}]`, depth + 1))
    : null;

  return {
    aiModelConfigId: normalizeModelBusinessId(value.aiModelConfigId, path),
    name,
    description,
    type,
    image,
    childrenList,
  };
};

/**
 * Validates and normalizes the complete model tree without changing server
 * ordering. Invalid or imprecise IDs reject the whole response (fail closed).
 */
export const normalizeAIModelTree = (value: unknown): AIModelConfig[] => {
  if (!Array.isArray(value)) {
    throw new Error('AI 模型数据格式错误：data 必须是数组');
  }
  return value.map((model, index) => normalizeModelNode(model, `data[${index}]`, 0));
};

// 获取AI模型配置列表
export const getAIModels = async (): Promise<AIModelConfig[]> => {
  try {
    const url = '/ai/model/index';
    
    const result = await authenticatedRequest<unknown>(url, {
      method: 'GET',
    });
    
    // 检查响应格式
    if (result.code === '0000') {
      return normalizeAIModelTree(result.data);
    } else {
      throw new Error(result.info || '获取AI模型失败');
    }
  } catch (error) {
    console.error('Failed to fetch AI models:', error);
    throw error;
  }
};

// 递归展开所有模型节点
const flattenModels = (models: AIModelConfig[]): AIModelConfig[] => {
  const result: AIModelConfig[] = [];
  
  for (const model of models) {
    // 如果有子节点，递归处理
    if (model.childrenList && model.childrenList.length > 0) {
      result.push(...flattenModels(model.childrenList));
    } else {
      // 叶子节点，添加到结果中
      result.push(model);
    }
  }
  
  return result;
};

// 兼容旧接口：查询AI分库配置列表
export const queryAIShardingConfigs = async (
  params: QueryShardingConfigParams
): Promise<ApiCursorPageData<AIShardingConfig>> => {
  try {
    // 使用新接口获取数据
    const models = await getAIModels();
    
    // 只获取分库模型（第二个分类）
    const verticalModels = models.find(category => category.name === '分库模型');
    const flatModels = verticalModels?.childrenList ? flattenModels([verticalModels]) : [];
    
    // 转换为旧格式
    const transformedData: AIShardingConfig[] = flatModels.map((model) => ({
      id: model.aiModelConfigId,
      aiShardingConfigId: model.aiModelConfigId,
      title: model.name,
      content: model.description,
      avatar: model.image || null
    }));
    
    // 简单分页处理（兼容旧接口）
    const { size = 50 } = params;
    const startIndex = 0; // 简化处理，不支持真正的分页
    const endIndex = Math.min(startIndex + size, transformedData.length);
    const paginatedData = transformedData.slice(startIndex, endIndex);
    
    return {
      data: paginatedData,
      hasNext: endIndex < transformedData.length,
      nextCursor: endIndex < transformedData.length ? endIndex.toString() : undefined,
      total: transformedData.length
    };
  } catch (error) {
    console.error('Failed to fetch AI sharding configs:', error);
    throw error;
  }
};

// 获取所有通用模型
export const getAllGeneralModels = async (): Promise<AIModelConfig[]> => {
  try {
    const models = await getAIModels();
    
    // 获取通用模型分类
    const generalModels = models.find(category => category.name === '通用模型');
    if (!generalModels?.childrenList) {
      return [];
    }
    
    // 展开所有通用模型
    return flattenModels(generalModels.childrenList);
  } catch (error) {
    console.error('Error fetching general models:', error);
    return [];
  }
};

// 获取所有分库模型
export const getAllVerticalModels = async (): Promise<AIModelConfig[]> => {
  try {
    const models = await getAIModels();
    
    // 获取分库模型分类
    const verticalModels = models.find(category => category.name === '分库模型');
    if (!verticalModels?.childrenList) {
      return [];
    }
    
    // 展开所有分库模型
    return flattenModels(verticalModels.childrenList);
  } catch (error) {
    console.error('Error fetching vertical models:', error);
    return [];
  }
};

// 兼容旧接口：获取所有AI分库配置（处理分页）
export const getAllAIShardingConfigs = async (): Promise<AIShardingConfig[]> => {
  try {
    // 使用新接口获取分库模型
    const verticalModels = await getAllVerticalModels();
    
    // 转换为旧格式
    return verticalModels.map((model) => ({
      id: model.aiModelConfigId,
      aiShardingConfigId: model.aiModelConfigId,
      title: model.name,
      content: model.description,
      avatar: model.image || null
    }));
  } catch (error) {
    console.error('Error fetching AI sharding configs:', error);
    return [];
  }
};

// 根据模型ID获取模型信息
export const getModelById = async (modelId: string): Promise<AIModelConfig | null> => {
  try {
    const models = await getAIModels();
    const allModels = flattenModels(models);
    
    return allModels.find(model => model.aiModelConfigId === modelId) || null;
  } catch (error) {
    console.error('Error fetching model by ID:', error);
    return null;
  }
};
