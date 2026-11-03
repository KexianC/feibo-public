import { authenticatedRequest } from './authApi';

export interface EntitlementSnapshot {
  userId: string;
  planName: string;
  unlimited: boolean;
  expiresAt: string | null;
  premiumChatDailyLimit: number;
  premiumChatTodayUsed: number;
  premiumChatTodayRemaining: number;
  premiumChatCreditsRemaining: number;
  digitalHumanSecondsRemaining: number;
  aiVideoSecondsRemaining: number;
}

export interface EntitlementTerms {
  premiumChatDailyLimit: number;
  digitalHumanSeconds: number;
  aiVideoSeconds: number;
  premiumChatCredits?: number;
}

export interface EntitlementProduct extends EntitlementTerms {
  productId: string;
  name: string;
  price: string | number;
  durationDays: number;
  active: boolean;
}

export interface EntitlementCatalogue {
  freePlan: EntitlementTerms;
  plans: EntitlementProduct[];
  rechargePacks: EntitlementProduct[];
}

export const fetchEntitlements = async () =>
  (await authenticatedRequest<EntitlementSnapshot>('/entitlements/me', { method: 'GET' })).data;

export const fetchEntitlementCatalogue = async () =>
  (await authenticatedRequest<EntitlementCatalogue>('/entitlements/catalogue', { method: 'GET' })).data;

export const formatQuotaSeconds = (value: number) => {
  if (value < 0) return '无限制';
  const seconds = Math.max(0, Math.floor(Number(value) || 0));
  const minutes = Math.floor(seconds / 60);
  return `${minutes > 0 ? `${minutes} 分钟` : ''}${seconds % 60 > 0 || minutes === 0 ? `${minutes > 0 ? ' ' : ''}${seconds % 60} 秒` : ''}`;
};

export const refreshEntitlements = () => window.dispatchEvent(new Event('entitlements-updated'));
