import { authenticatedRequest } from './auth';

const AI_MODEL_ADMIN_BASE_URL = '/api/v1/admin/ai-models';

export interface AiModelAdminItem {
  id: string;
  name: string;
  modelName: string;
  modelCode?: string;
  providerId?: string;
  providerName?: string;
  providerOrder?: number;
  description: string;
  visible: boolean;
  apiKeyConfigured: boolean;
  order?: number;
  createTime?: string;
  updateTime?: string;
}

export interface CreateAiModelInput {
  name: string;
  modelCode: string;
  modelName: string;
  description: string;
  visible: boolean;
  order?: number;
  apiKey?: string;
}

export interface UpdateAiModelInput {
  name: string;
  modelName: string;
  description: string;
  order?: number;
}

export interface SupportedModelCodeOption {
  value: string;
  label: string;
}

export interface AiModelAdminListResult {
  models: AiModelAdminItem[];
  supportedModelCodes: SupportedModelCodeOption[];
}

export const DEFAULT_SUPPORTED_MODEL_CODES: SupportedModelCodeOption[] = [
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'qwen', label: '通义千问（Qwen）' },
  { value: 'doubao', label: '豆包（Doubao）' },
  { value: 'baichuan', label: '百川（Baichuan）' },
  { value: 'ant', label: '蚂蚁安诊儿' },
  { value: 'wisediag', label: '智诊' },
  { value: 'glm52', label: '智谱 GLM' },
  { value: 'kimik3', label: 'Kimi K3' },
  { value: 'hy3', label: 'HY3' },
  { value: 'szdoctor', label: '木吾木同Medtalent' },
  { value: 'qianfan', label: '百度千帆' },
];

type UnknownRecord = Record<string, unknown>;

const isRecord = (value: unknown): value is UnknownRecord => (
  Boolean(value) && typeof value === 'object' && !Array.isArray(value)
);

const firstDefined = (...values: unknown[]): unknown => values.find(value => value !== undefined && value !== null);

const toBusinessId = (value: unknown): string | undefined => {
  if (typeof value === 'string') {
    const normalized = value.trim();
    return /^[1-9]\d*$/.test(normalized) ? normalized : undefined;
  }
  if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) {
    return String(value);
  }
  return undefined;
};

const toBoolean = (value: unknown, fallback = false): boolean => {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (['true', '1', 'yes', 'show', 'visible', 'enabled', 'configured'].includes(normalized)) return true;
    if (['false', '0', 'no', 'hide', 'hidden', 'disabled', 'unconfigured'].includes(normalized)) return false;
  }
  return fallback;
};

const toOrder = (value: unknown): number | undefined => {
  const numericValue = typeof value === 'number'
    ? value
    : (typeof value === 'string' && /^\d{1,5}$/.test(value.trim()) ? +value.trim() : undefined);
  return typeof numericValue === 'number'
    && numericValue % 1 === 0
    && numericValue >= 0
    && numericValue <= 10000
    ? numericValue
    : undefined;
};

const normalizeModel = (rawValue: unknown): AiModelAdminItem => {
  const raw = isRecord(rawValue) ? rawValue : {};
  const id = toBusinessId(firstDefined(raw.id, raw.aiModelConfigId, raw.modelId));
  const configuredValue = firstDefined(raw.apiKeyConfigured, raw.hasApiKey);
  // Deliberately do not copy apiKey/apiKeyMasked into the UI model. The page
  // only needs configuration state and must never be able to echo a secret.
  const apiKeyConfigured = configuredValue === undefined ? false : toBoolean(configuredValue);

  return {
    id: id ?? '',
    name: String(firstDefined(raw.name, raw.displayName, raw.modelName, raw.title) ?? ''),
    modelName: String(firstDefined(raw.modelName, raw.name, raw.title) ?? ''),
    modelCode: typeof firstDefined(raw.modelCode, raw.code) === 'string'
      ? String(firstDefined(raw.modelCode, raw.code))
      : undefined,
    providerId: toBusinessId(firstDefined(raw.providerId, raw.parentId, raw.providerBusinessId)),
    providerName: typeof firstDefined(raw.providerName, raw.providerDisplayName) === 'string'
      ? String(firstDefined(raw.providerName, raw.providerDisplayName))
      : undefined,
    providerOrder: toOrder(firstDefined(raw.providerOrder, raw.providerSort, raw.categoryOrder)),
    description: String(firstDefined(raw.description, raw.introduction, raw.summary) ?? ''),
    visible: toBoolean(firstDefined(raw.visible, raw.isVisible, raw.isShow), false),
    apiKeyConfigured,
    order: toOrder(firstDefined(raw.order, raw.sort, raw.sortOrder, raw.sortNum, raw.displayOrder)),
    createTime: typeof firstDefined(raw.createTime, raw.createdAt) === 'string'
      ? String(firstDefined(raw.createTime, raw.createdAt))
      : undefined,
    updateTime: typeof firstDefined(raw.updateTime, raw.updatedAt) === 'string'
      ? String(firstDefined(raw.updateTime, raw.updatedAt))
      : undefined,
  };
};

const extractRecords = (data: unknown): unknown[] => {
  if (Array.isArray(data)) return data;
  if (!isRecord(data)) return [];
  const candidate = firstDefined(data.records, data.list, data.models, data.content);
  return Array.isArray(candidate) ? candidate : [];
};

const extractSupportedModelCodes = (data: unknown): SupportedModelCodeOption[] => {
  if (!isRecord(data) || !Array.isArray(data.supportedModelCodes)) {
    return DEFAULT_SUPPORTED_MODEL_CODES;
  }
  const defaultLabels = new Map(DEFAULT_SUPPORTED_MODEL_CODES.map(option => [option.value, option.label]));
  const options = data.supportedModelCodes.flatMap((rawOption): SupportedModelCodeOption[] => {
    if (typeof rawOption === 'string') {
      const value = rawOption.trim().toLowerCase();
      return /^[a-z0-9][a-z0-9_-]{0,31}$/.test(value)
        ? [{ value, label: defaultLabels.get(value) ?? value }]
        : [];
    }
    if (!isRecord(rawOption)) return [];
    const rawValue = firstDefined(rawOption.modelCode, rawOption.code, rawOption.value);
    if (typeof rawValue !== 'string') return [];
    const value = rawValue.trim().toLowerCase();
    if (!/^[a-z0-9][a-z0-9_-]{0,31}$/.test(value)) return [];
    const rawLabel = firstDefined(rawOption.label, rawOption.name, rawOption.displayName);
    return [{ value, label: typeof rawLabel === 'string' && rawLabel.trim() ? rawLabel.trim() : (defaultLabels.get(value) ?? value) }];
  });
  if (options.length === 0) return DEFAULT_SUPPORTED_MODEL_CODES;
  return Array.from(new Map(options.map(option => [option.value, option])).values());
};

export class AiModelAdminService {
  static async list(): Promise<AiModelAdminListResult> {
    const response = await authenticatedRequest<unknown>(AI_MODEL_ADMIN_BASE_URL, { method: 'GET' });
    const models = extractRecords(response.data)
      .map(normalizeModel)
      .filter(model => model.id && model.name && model.modelName);
    return {
      models,
      supportedModelCodes: extractSupportedModelCodes(response.data),
    };
  }

  static async create(input: CreateAiModelInput): Promise<void> {
    await authenticatedRequest(AI_MODEL_ADMIN_BASE_URL, {
      method: 'POST',
      body: JSON.stringify(input),
    });
  }

  static async update(id: string, input: UpdateAiModelInput): Promise<void> {
    await authenticatedRequest(`${AI_MODEL_ADMIN_BASE_URL}/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(input),
    });
  }

  static async deleteModel(id: string): Promise<void> {
    const validatedModelId = toBusinessId(id);
    if (!validatedModelId) {
      throw new Error('模型 ID 无效，已拒绝删除');
    }
    await authenticatedRequest(`${AI_MODEL_ADMIN_BASE_URL}/${encodeURIComponent(validatedModelId)}`, {
      method: 'DELETE',
    });
  }

  static async updateProviderOrder(providerId: string, order: number): Promise<void> {
    const validatedProviderId = toBusinessId(providerId);
    if (!validatedProviderId) {
      throw new Error('供应商 ID 无效，已拒绝更新排序');
    }
    if (!Number.isInteger(order) || order < 1 || order > 10000) {
      throw new Error('供应商目标位置必须是 1–10000 的整数');
    }
    await authenticatedRequest(`${AI_MODEL_ADMIN_BASE_URL}/providers/${encodeURIComponent(validatedProviderId)}/order`, {
      method: 'PUT',
      body: JSON.stringify({ order }),
    });
  }

  static async updateVisibility(id: string, visible: boolean): Promise<void> {
    await authenticatedRequest(`${AI_MODEL_ADMIN_BASE_URL}/${encodeURIComponent(id)}/visibility`, {
      method: 'PUT',
      body: JSON.stringify({ visible }),
    });
  }

  static async updateApiKey(id: string, apiKey: string): Promise<void> {
    await authenticatedRequest(`${AI_MODEL_ADMIN_BASE_URL}/${encodeURIComponent(id)}/api-key`, {
      method: 'PUT',
      body: JSON.stringify({ apiKey }),
    });
  }
}

export { AI_MODEL_ADMIN_BASE_URL };
