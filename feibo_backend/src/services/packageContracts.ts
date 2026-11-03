/** Explicit recharge amounts: validity belongs to durationDays in the entitlement API. */
export interface RechargePackInput {
  name: string;
  description?: string;
  price: number;
  value: number;
  status: number;
  aiFeatureTemplateId?: string;
}

export function buildRechargePackPayload(input: RechargePackInput, id?: string) {
  if (!Number.isFinite(input.value) || input.value < 0) throw new Error('请输入有效的非负算力额度');
  if (!Number.isFinite(input.price) || input.price < 0) throw new Error('请输入有效的非负价格');
  if (input.status !== 0 && input.status !== 1) throw new Error('请选择启用状态');
  const name = input.name?.trim();
  if (!name || name.length > 64) throw new Error('名称长度必须为 1 到 64 个字符');
  return {
    ...(id ? { id } : {}), name, description: input.description ?? '',
    price: input.price, value: input.value, isActive: input.status,
    ...(input.aiFeatureTemplateId ? { aiFeatureTemplateId: input.aiFeatureTemplateId } : {}),
  };
}

export interface ProductFilters {
  keyword?: string;
  active?: boolean;
  minPrice?: number;
  maxPrice?: number;
}

export function filterEntitlementProducts<T extends { name: string; productId: string; price: number; active: boolean }>(
  products: readonly T[], filters: ProductFilters,
): T[] {
  const keyword = filters.keyword?.trim().toLocaleLowerCase() ?? '';
  return products.filter(product => (
    (!keyword || product.name.toLocaleLowerCase().includes(keyword) || product.productId.includes(keyword))
    && (filters.active === undefined || product.active === filters.active)
    && (filters.minPrice === undefined || Number(product.price) >= filters.minPrice)
    && (filters.maxPrice === undefined || Number(product.price) <= filters.maxPrice)
  ));
}
