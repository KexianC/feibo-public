package com.hzmwmt.domain.entitlement;

import java.util.Map;

public interface IEntitlementAdminService {
    Map<String, Object> configuration(boolean publicCatalogue);
    void updateFreePlan(FreeEntitlementPolicy request);
    Map<String, Object> saveProduct(boolean recharge, Long productId, EntitlementProductRequest request);
    void setModelPremium(Long modelId, boolean premium);
}
