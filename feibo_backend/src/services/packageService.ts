import { authenticatedRequest } from './auth';
import { buildRechargePackPayload, type RechargePackInput } from './packageContracts';

/**
 * 原始后端算力包数据结构（严格匹配最新返回字段）
 * 字段来源于用户提供的接口返回示例
 */
export interface RawPackage {
  id: number;                          // 记录主键ID
  aiRechargePackId: string | number;   // AI充值包ID（可能为大整数，已在请求层字符串化）
  packCode: string;                    // 包代码，如 PACK_DUR
  name: string;                        // 包名称
  price: number;                       // 价格
  aiFeatureTemplateId: string | number;// AI功能模板ID（可能为大整数）
  value: number;                       // 值（例如分钟或额度）
  description: string;                 // 描述
  isActive: number | boolean;          // 0=启用，1=禁用
  createTime: string;                  // 创建时间
  updateTime: string;                  // 更新时间
  // 兼容旧字段（若后端仍返回则接收，但不强制使用）
  sort?: number;
}

/**
 * 规范化后的前端套餐信息接口（供页面使用）
 * - 保持原有页面依赖的字段不变
 * - 兼容旧字段（改为可选）以避免页面类型错误
 * - 同时补充新的原始字段便于展示
 */
export interface Package {
  // 供现有页面使用的规范化字段
  id: string;            // 主键ID（字符串）
  name: string;          // 套餐名称（映射自 name）
  description: string;   // 套餐描述（前端自填）
  price: number;         // 套餐价格（数值，映射自 price）
  duration: number;      // 套餐时长（天，无该字段时为0）
  features: string[];    // 套餐功能列表
  status: number;        // 套餐状态：0-启用，1-禁用（由 isActive 反转派生：false=启用）
  createTime: string;    // 创建时间
  updateTime: string;    // 更新时间

  // 旧版原始字段（为兼容页面展示，全部改为可选）
  packageId?: number;
  packageName?: string;
  category?: string;
  version?: string;
  sort?: number;
  chargeType?: string;
  durationText?: string; // 旧版原始时长字符串
  amount?: number;       // 旧版原始金额字段

  // 新版原始字段（便于详情展示）
  computingPowerName?: string;
  deductionRecharge?: number;
  timeliness?: number;
  isDisplay?: number;
  isDeleted?: number;

  // 套餐计划新增字段（适配 aiMembershipPlan 返回）
  aiMembershipPlanId?: string; // AI会员计划ID（字符串化）
  planCode?: string;           // 计划代码（PLAN_ANNUAL/PLAN_MONTHLY/PLAN_FREE）
  isActive?: boolean;          // 是否激活

  // 算力包新增字段（适配 ai-recharge-pack 返回）
  aiRechargePackId?: string;   // 算力包ID（字符串化）
  packCode?: string;           // 包代码（如 PACK_DUR）
  aiFeatureTemplateId?: string;// 功能模板ID（字符串化）
  value?: number;              // 值（额度/分钟等）
}

// 分页查询参数
export interface PackagePageParams {
  page: number;
  size: number;
  name?: string;         // 套餐名称关键词
  status?: number;       // 套餐状态
  minPrice?: number;     // 最低价格
  maxPrice?: number;     // 最高价格
}

// 通用分页响应
export interface PageResponse<T> {
  records: T[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

// 创建/更新套餐参数（沿用前端规范化结构）
export type PackageParams = RechargePackInput;

/**
 * 套餐服务：分页查询、详情获取、新增、更新、删除
 */
export class PackageService {
  private static instance: PackageService;

  private constructor() {}

  /**
   * 获取单例实例
   */
  public static getInstance(): PackageService {
    if (!PackageService.instance) {
      PackageService.instance = new PackageService();
    }
    return PackageService.instance;
  }

  /**
   * 分页查询算力包列表
   * 将后端 RawPackage（computingPowerName 等字段）映射为前端规范化 Package
   */
  async getPackagesPage(params: PackagePageParams): Promise<PageResponse<Package>> {
    const queryParams = new URLSearchParams();
    // 适配新接口分页参数命名：pageNum/pageSize
    queryParams.append('pageNum', params.page.toString());
    queryParams.append('pageSize', params.size.toString());
    if (params.name) queryParams.append('name', params.name);
    if (params.status !== undefined) queryParams.append('status', params.status.toString());
    if (params.minPrice !== undefined) queryParams.append('minPrice', params.minPrice.toString());
    if (params.maxPrice !== undefined) queryParams.append('maxPrice', params.maxPrice.toString());

    const response = await authenticatedRequest<PageResponse<RawPackage>>(
      `/api/admin/function/ai-recharge-pack/page?${queryParams.toString()}`,
      { method: 'GET' }
    );
    const data = response.data as PageResponse<RawPackage>;

    // 适配返回字段到规范化结构
    const normalized: PageResponse<Package> = {
      records: (data.records || []).map((item: RawPackage): Package => {
        return {
          // 规范化字段
          id: String(item.id),
          name: item.name,
          description: item.description || '',
          price: Number(item.price),
          duration: 0, // 充值额度不是有效期；旧套餐共用类型保留该占位字段。
          features: [],
          // status 新逻辑：isActive=false 表示启用(0)，true 表示禁用(1)
          status: Number(item.isActive) === 0 ? 0 : 1,
          createTime: item.createTime,
          updateTime: item.updateTime,
          // 兼容旧字段（保留占位）
          sort: item.sort,
          // 新字段透传（用于详情与列展示）
          aiRechargePackId: item.aiRechargePackId?.toString?.() ?? String(item.aiRechargePackId),
          packCode: item.packCode,
          aiFeatureTemplateId: item.aiFeatureTemplateId?.toString?.() ?? String(item.aiFeatureTemplateId),
          value: item.value,
          isActive: Number(item.isActive) !== 0,
        };
      }),
      total: data.total,
      pages: data.pages,
      size: data.size,
      current: data.current,
    };

    return normalized;
  }

  /**
   * 根据ID查询算力包详情
   * 将 RawPackage（computingPowerName 等字段）映射为前端规范化 Package
   */
  async getPackageById(id: string): Promise<Package> {
    const response = await authenticatedRequest<RawPackage>(
      `/api/admin/function/ai-recharge-pack/${id}`,
      { method: 'GET' }
    );
    const item = response.data as RawPackage;
    return {
      // 规范化字段
      id: String(item.id),
      name: item.name,
      description: item.description || '',
      price: Number(item.price),
      duration: 0,
      features: [],
      status: Number(item.isActive) === 0 ? 0 : 1,
      createTime: item.createTime,
      updateTime: item.updateTime,
      // 新字段透传（用于详情展示）
      sort: item.sort,
      aiRechargePackId: item.aiRechargePackId?.toString?.() ?? String(item.aiRechargePackId),
      packCode: item.packCode,
      aiFeatureTemplateId: item.aiFeatureTemplateId?.toString?.() ?? String(item.aiFeatureTemplateId),
      value: item.value,
      isActive: Number(item.isActive) !== 0,
    };
  }

  /**
   * 新增套餐
   * 保持与前端表单一致，具体字段按后端实际需要调整
   */
  async createPackage(params: PackageParams): Promise<void> {
    const response = await authenticatedRequest<boolean>(
      '/api/admin/function/ai-recharge-pack',
      {
        method: 'POST',
        body: JSON.stringify(buildRechargePackPayload(params)),
      }
    );
    if (response.data !== true) throw new Error('后端未创建充值包');
  }

  /**
   * 更新套餐
   * 保持与前端表单一致，具体字段按后端实际需要调整
   */
  async updatePackage(params: PackageParams & { id: string }): Promise<void> {
    const response = await authenticatedRequest<boolean>(
      '/api/admin/function/ai-recharge-pack',
      {
        method: 'PUT',
        body: JSON.stringify(buildRechargePackPayload(params, params.id)),
      }
    );
    if (response.data !== true) throw new Error('后端未更新充值包');
  }

  /**
   * 删除套餐
   */
  async deletePackage(id: string): Promise<void> {
    const response = await authenticatedRequest<boolean>(
      `/api/admin/function/ai-recharge-pack/${id}`,
      { method: 'DELETE' }
    );
    if (response.data !== true) throw new Error('后端未删除充值包，请刷新后重试');
  }
}

// 默认导出单例
export default PackageService.getInstance();
