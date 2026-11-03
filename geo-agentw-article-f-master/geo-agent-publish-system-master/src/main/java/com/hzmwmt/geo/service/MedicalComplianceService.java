package com.hzmwmt.geo.service;

import com.hzmwmt.a_publish.model.entity.ArticleInfo;
import com.hzmwmt.geo.model.dto.ComplianceAuditResult;

public interface MedicalComplianceService {

    ComplianceAuditResult audit(String title, String content);

    String auditToJson(String title, String content);

    void assertPublishable(ArticleInfo articleInfo);
}
