import { authenticatedRequest } from './auth';

export interface FreePlanEntitlements {
  premiumChatDailyLimit: number;
  digitalHumanSeconds: number;
  aiVideoSeconds: number;
}

export interface EntitlementProduct extends FreePlanEntitlements {
  productId: string;
  name: string;
  price: number;
  durationDays: number;
  active: boolean;
  configured?: boolean;
  premiumChatCredits: number;
}

export type EntitlementProductInput = Omit<EntitlementProduct, 'productId' | 'configured'>;

export interface EntitlementModel {
  modelId: string;
  name: string;
  premium: boolean;
}

export interface EntitlementConfig {
  freePlan: FreePlanEntitlements;
  plans: EntitlementProduct[];
  rechargePacks: EntitlementProduct[];
  models: EntitlementModel[];
}

export interface EntitlementGrantInput {
  requestId: string;
  planId?: string;
  durationDays?: number;
  premiumChatDailyLimit?: number;
  digitalHumanSeconds?: number;
  aiVideoSeconds?: number;
  premiumChatCredits?: number;
  reason: string;
}

export interface EntitlementGrantRecord {
  requestId: string;
  reason: string;
  createdAt: string;
  expiresAt?: string;
  planName?: string;
  premiumChatDailyLimit?: number;
  premiumChatCredits?: number;
  digitalHumanSeconds?: number;
  aiVideoSeconds?: number;
}

export interface EntitlementAccount {
  userId: string;
  planName: string;
  unlimited: boolean;
  expiresAt?: string;
  premiumChatDailyLimit: number;
  premiumChatTodayUsed: number;
  premiumChatTodayRemaining: number;
  premiumChatCreditsRemaining: number;
  digitalHumanSecondsRemaining: number;
  aiVideoSecondsRemaining: number;
  premiumChatTodayReserved?: number;
  digitalHumanSecondsReserved?: number;
  aiVideoSecondsReserved?: number;
  grants: EntitlementGrantRecord[];
}

const BASE_URL = '/api/admin/entitlements';
let refreshSequence = 0;
const fresh = () => `?_t=${Date.now()}-${++refreshSequence}`;

export const EntitlementAdminService = {
  async getConfig(): Promise<EntitlementConfig> {
    return (await authenticatedRequest<EntitlementConfig>(`${BASE_URL}/config${fresh()}`)).data;
  },
  async saveFreePlan(input: FreePlanEntitlements): Promise<void> {
    await authenticatedRequest(`${BASE_URL}/free-plan`, { method: 'PUT', body: JSON.stringify(input) });
  },
  async saveProduct(kind: 'plans' | 'packs', input: EntitlementProductInput, productId?: string): Promise<void> {
    await authenticatedRequest(`${BASE_URL}/${kind}${productId ? `/${encodeURIComponent(productId)}` : ''}`, {
      method: productId ? 'PUT' : 'POST',
      body: JSON.stringify(input),
    });
  },
  async saveModel(modelId: string, premium: boolean): Promise<void> {
    await authenticatedRequest(`${BASE_URL}/models/${encodeURIComponent(modelId)}`, {
      method: 'PUT', body: JSON.stringify({ premium }),
    });
  },
  async getAccount(userId: string): Promise<EntitlementAccount> {
    return (await authenticatedRequest<EntitlementAccount>(`${BASE_URL}/accounts/${encodeURIComponent(userId)}${fresh()}`)).data;
  },
  async grant(userId: string, input: EntitlementGrantInput): Promise<void> {
    // Use the same UUID in the durable business request and authentication
    // retries. An uncertain response must never create a second grant.
    await authenticatedRequest(`${BASE_URL}/accounts/${encodeURIComponent(userId)}/grants`, {
      method: 'POST', body: JSON.stringify(input),
    }, 0, input.requestId);
  },
};
