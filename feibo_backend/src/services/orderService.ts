import { authenticatedRequest } from './auth';

// 订单信息接口（适配后端实际返回字段）
export interface Order {
  id: string;
  // 显示字段（为页面做适配的派生字段）
  orderNo: string;               // 显示用订单号（映射后端 orderId）
  orderType?: string;            // 显示用订单类型（映射 productName）
  amount: number;                // 显示用金额（映射 totalAmount）
  status: number;
  packageId?: number;            // 显示用套餐ID（映射 productId）
  member?: string;               // 显示用会员名（映射 username）
  createTime: string;
  updateTime?: string;
  // 原始字段保留，便于后续扩展
  orderId?: string;
  userId?: string;
  username?: string;
  externalTransactionId?: string;
  url?: string;
  productType?: number;
  productId?: number;
  totalAmount?: number;
  productName?: string;
  payTime?: string;
  creatingLock?: number | null;
  validTime?: string;
}

// 分页查询参数
export interface OrderPageParams {
  page: number;
  size: number;
  keyword?: string; // 订单号或会员关键词
  status?: number;
}

// 通用分页响应
export interface PageResponse<T> {
  records: T[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

/**
 * 订单服务：分页查询、详情获取、状态更新与信息更新
 */
export class OrderService {
  private static instance: OrderService;

  private constructor() {}

  /**
   * 获取单例实例
   */
  public static getInstance(): OrderService {
    if (!OrderService.instance) {
      OrderService.instance = new OrderService();
    }
    return OrderService.instance;
  }

  /**
   * 分页查询订单列表
   * @param params 查询参数（页码、大小、关键词、状态、时间范围）
   * @returns 分页响应，包含订单记录与总数等
   */
  async getOrdersPage(params: OrderPageParams): Promise<PageResponse<Order>> {
    const queryParams = new URLSearchParams();
    queryParams.append('pageNum', params.page.toString());
    queryParams.append('pageSize', params.size.toString());
    const keyword = params.keyword?.trim();
    if (keyword) {
      if (/^\d+$/.test(keyword)) queryParams.append('orderId', keyword);
      else queryParams.append('username', keyword);
    }
    if (params.status !== undefined) queryParams.append('status', String(params.status));

    const response = await authenticatedRequest<PageResponse<any>>(
      `/api/admin/order/page?${queryParams.toString()}`,
      { method: 'GET' }
    );
    const data = response.data as PageResponse<any>;
    const clean = (u: any) => (typeof u === 'string' ? u.replace(/[`'\"]/g, '').trim() : undefined);

    if (Array.isArray(data.records)) {
      data.records = data.records.map((r: any) => {
        const orderIdStr = String(r.orderId ?? r.id ?? '');
        const amountNum = Number(r.totalAmount ?? r.amount ?? 0);
        const pkgIdNum = r.productId !== undefined ? Number(r.productId) : (r.packageId !== undefined ? Number(r.packageId) : undefined);
        return {
          id: String(r.id ?? r.orderId ?? orderIdStr),
          orderNo: orderIdStr,
          orderType: r.productName ?? r.orderType,
          amount: amountNum,
          status: Number(r.status),
          packageId: pkgIdNum,
          member: r.username ?? r.member,
          createTime: r.createTime,
          updateTime: r.updateTime,
          orderId: orderIdStr,
          userId: r.userId !== undefined ? String(r.userId) : undefined,
          username: r.username,
          externalTransactionId: r.externalTransactionId,
          url: clean(r.url),
          productType: r.productType !== undefined ? Number(r.productType) : undefined,
          productId: pkgIdNum,
          totalAmount: amountNum,
          productName: r.productName,
          payTime: r.payTime,
          creatingLock: r.creatingLock ?? null,
          validTime: r.validTime,
        } as Order;
      });
    }
    return data as PageResponse<Order>;
  }

  /**
   * 获取订单详情
   * @param id 订单ID
   * @returns 订单详情对象
   */
  async getOrderDetail(id: string): Promise<Order> {
    const response = await authenticatedRequest<any>(
      `/api/admin/order/${id}`,
      { method: 'GET' }
    );
    const r = response.data as any;
    const clean = (u: any) => (typeof u === 'string' ? u.replace(/[`'\"]/g, '').trim() : undefined);
    const orderIdStr = String(r.orderId ?? r.id ?? '');
    const amountNum = Number(r.totalAmount ?? r.amount ?? 0);
    const pkgIdNum = r.productId !== undefined ? Number(r.productId) : (r.packageId !== undefined ? Number(r.packageId) : undefined);
    return {
      id: String(r.id ?? r.orderId ?? orderIdStr),
      orderNo: orderIdStr,
      orderType: r.productName ?? r.orderType,
      amount: amountNum,
      status: Number(r.status),
      packageId: pkgIdNum,
      member: r.username ?? r.member,
      createTime: r.createTime,
      updateTime: r.updateTime,
      orderId: orderIdStr,
      userId: r.userId !== undefined ? String(r.userId) : undefined,
      username: r.username,
      externalTransactionId: r.externalTransactionId,
      url: clean(r.url),
      productType: r.productType !== undefined ? Number(r.productType) : undefined,
      productId: pkgIdNum,
      totalAmount: amountNum,
      productName: r.productName,
      payTime: r.payTime,
      creatingLock: r.creatingLock ?? null,
      validTime: r.validTime,
    } as Order;
  }

  /**
   * 更新订单状态
   * @param id 订单ID
   * @param status 新状态（数值枚举）
   * @returns 更新后的订单对象
   */
  async updateOrderStatus(orderId: string, status: 3 | 5): Promise<boolean> {
    const query = new URLSearchParams({ orderId, status: String(status) });
    const response = await authenticatedRequest<boolean>(
      `/api/admin/order/status?${query.toString()}`,
      { method: 'PUT' },
    );
    return response.data ?? false;
  }
}

// 默认导出单例，便于直接使用
export default OrderService.getInstance();
