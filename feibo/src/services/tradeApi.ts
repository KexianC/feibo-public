// 支付交易API服务
// 根据接口文档实现商品列表查询、订单锁定等功能

import { getClientFingerprint, authenticatedRequest } from './authApi';
import { API_CONFIG } from '../config/api';
import type {
  MembershipPlan,
  RechargePack,
  ProductListResponse,
  LockOrderRequest,
  LockOrderResponse
} from '../types';

// 基础URL配置
const BASE_URL = API_CONFIG.API_BASE_URL;

export const fetchOrderStatus = async (orderId: string) =>
  (await authenticatedRequest<{ orderId: string; status: number; fulfilled: boolean }>(
    `/trade/orders/${encodeURIComponent(orderId)}`, { method: 'GET' }
  )).data;

// 通用响应结构
interface ApiResponse<T = any> {
  code: string;
  info: string;
  data: T;
}

// 使用统一的认证请求函数（已包含401自动刷新Token机制和大整数精度处理）

// 查询商品列表
export const fetchProductList = async (): Promise<ProductListResponse> => {
  const response = await authenticatedRequest<ProductListResponse>('/trade/index', {
    method: 'GET',
  });
  
  // ID字段已在JSON解析时转换为字符串，无需额外处理
  const data = response.data;
  
  return data;
};

// 锁定交易订单
export const lockTradeOrder = async (data: LockOrderRequest, idempotencyToken?: string, uuid?: string): Promise<LockOrderResponse> => {
  // 确保productId为字符串格式
  const requestData = {
    ...data,
    productId: String(data.productId)
  };
  
  // 构建请求选项，包含幂等性令牌
  const requestOptions: any = {
    method: 'POST',
    body: JSON.stringify(requestData),
  };
  
  // 如果提供了幂等性令牌，添加到请求头
  if (idempotencyToken) {
    requestOptions.headers = {
      'Idempotency-Token': idempotencyToken
    };
  }
  
  const response = await authenticatedRequest<LockOrderResponse>('/trade/lock_trade_pay_order', requestOptions, 0, uuid);
  
  return response.data;
};

// 获取商品详情（根据类型和ID）
export const getProductById = async (
  productId: string,
  productType: boolean
): Promise<MembershipPlan | RechargePack | null> => {
  const productList = await fetchProductList();
  
  if (productType) {
    // 充值包
    return productList.rechargePacks.find(pack => pack.aiRechargePackId === productId) || null;
  } else {
    // 会员套餐
    return productList.membershipPlans.find(plan => plan.aiMembershipPlanId === productId) || null;
  }
};

// 创建支付订单的完整流程
export const createPaymentOrder = async (
  productId: string,
  productType: boolean,
  uuid?: string
): Promise<{
  product: MembershipPlan | RechargePack;
  order: LockOrderResponse;
}> => {
  // 1. 获取商品详情
  const product = await getProductById(productId, productType);
  if (!product) {
    throw new Error('商品不存在');
  }
  
  // 2. 锁定订单
  const order = await lockTradeOrder({ productId, productType }, undefined, uuid);
  
  return { product, order };
};

// 跳转到支付页面
export const redirectToPayment = (paymentUrl: string): void => {
  window.open(paymentUrl, '_blank');
};

// 格式化价格显示（支持字符串输入）
export const formatPrice = (price: string | number): string => {
  const numPrice = typeof price === 'string' ? parseFloat(price) : price;
  if (isNaN(numPrice)) {
    return '¥0.00';
  }
  return `¥${numPrice.toFixed(2)}`;
};

// 格式化套餐时长
export const formatDuration = (days: number): string => {
  // -1表示永久有效
  if (days === -1) {
    return '永久有效';
  }
  if (days >= 365) {
    const years = Math.floor(days / 365);
    const remainingDays = days % 365;
    if (remainingDays === 0) {
      return `${years}年`;
    } else {
      return `${years}年${remainingDays}天`;
    }
  } else if (days >= 30) {
    const months = Math.floor(days / 30);
    const remainingDays = days % 30;
    if (remainingDays === 0) {
      return `${months}个月`;
    } else {
      return `${months}个月${remainingDays}天`;
    }
  } else {
    return `${days}天`;
  }
};

// 格式化数值显示（如Token数量，支持字符串输入）
export const formatValue = (value: string | number): string => {
  const numValue = typeof value === 'string' ? parseFloat(value) : value;
  if (isNaN(numValue)) {
    return '0';
  }
  // -1表示永久
  if (numValue === -1) {
    return '永久';
  }
  if (numValue >= 10000) {
    return `${(numValue / 10000).toFixed(1)}万`;
  } else if (numValue >= 1000) {
    return `${(numValue / 1000).toFixed(1)}k`;
  } else {
    return numValue.toString();
  }
};

// 数值字符串验证函数
export const isValidNumberString = (value: string): boolean => {
  return !isNaN(parseFloat(value)) && isFinite(parseFloat(value));
};

// 安全的数值字符串转换
export const safeParseFloat = (value: string | number, defaultValue: number = 0): number => {
  if (typeof value === 'number') {
    return isNaN(value) ? defaultValue : value;
  }
  const parsed = parseFloat(value);
  return isNaN(parsed) ? defaultValue : parsed;
};

// 价格比较函数（支持字符串）
export const comparePrices = (price1: string | number, price2: string | number): number => {
  const num1 = safeParseFloat(price1);
  const num2 = safeParseFloat(price2);
  return num1 - num2;
};
