package com.hzmwmt.geo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.a_publish.model.entity.ArticleInfo;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.mapper.MedicalComplianceRuleMapper;
import com.hzmwmt.geo.model.dto.ComplianceAuditResult;
import com.hzmwmt.geo.model.entity.MedicalComplianceRule;
import com.hzmwmt.geo.service.MedicalComplianceService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class MedicalComplianceServiceImpl implements MedicalComplianceService {

    @Resource
    private MedicalComplianceRuleMapper ruleMapper;

    @Resource
    private ObjectMapper objectMapper;

    private static final List<RuleSpec> DEFAULT_RULES = List.of(
            new RuleSpec("effect_claim", "critical", "(根治|包治|彻底治愈|永不复发|百分之百有效|100%有效|保证治好|立刻见效)", "禁止疗效承诺"),
            new RuleSpec("personal_advice", "critical", "(你应该|你必须|立即停药|自行停药|不用去医院|按我说的治疗|直接用药|无需就医)", "禁止个人诊疗建议"),
            new RuleSpec("exaggeration", "high", "(最权威|全国第一|唯一|神医|特效|秘方|奇效|无任何副作用)", "禁止夸大宣传"),
            new RuleSpec("sensitive_word", "high", "(癌症克星|祖传秘方|偏方治大病|无效退款|治愈率)", "医疗敏感表达"),
            new RuleSpec("drug_recommendation", "critical", "((推荐|建议|可以|应该).{0,20}|(服用|使用|吃).{0,12})(阿莫西林|头孢|布洛芬|奥司他韦|抗生素|激素|降压药|降糖药|处方药)", "禁止药品推荐")
    );

    @Override
    public ComplianceAuditResult audit(String title, String content) {
        String text = ((title == null ? "" : title) + "\n" + (content == null ? "" : content)).trim();
        List<ComplianceAuditResult.Violation> violations = new ArrayList<>();
        for (RuleSpec rule : allRules()) {
            Matcher matcher = rule.pattern().matcher(text);
            if (matcher.find()) {
                violations.add(ComplianceAuditResult.Violation.builder()
                        .ruleType(rule.ruleType())
                        .severity(rule.severity())
                        .pattern(rule.rawPattern())
                        .description(rule.description())
                        .evidence(matcher.group())
                        .build());
            }
        }

        boolean passed = violations.stream()
                .noneMatch(v -> "high".equalsIgnoreCase(v.getSeverity()) || "critical".equalsIgnoreCase(v.getSeverity()));
        String level = violations.stream().anyMatch(v -> "critical".equalsIgnoreCase(v.getSeverity()))
                ? "critical"
                : violations.stream().anyMatch(v -> "high".equalsIgnoreCase(v.getSeverity())) ? "high" : "pass";
        return ComplianceAuditResult.builder()
                .passed(passed)
                .level(level)
                .violations(violations)
                .build();
    }

    @Override
    public String auditToJson(String title, String content) {
        try {
            return objectMapper.writeValueAsString(audit(title, content));
        } catch (JsonProcessingException e) {
            throw new AppException("COMPLIANCE_AUDIT_ERROR", "合规审核结果序列化失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void assertPublishable(ArticleInfo articleInfo) {
        if (articleInfo == null) {
            throw new AppException("ARTICLE_NOT_FOUND", "文章不存在", HttpStatus.NOT_FOUND);
        }
        ComplianceAuditResult result = audit(articleInfo.getTitle(), articleInfo.getContent());
        if (!result.isPassed()) {
            throw new AppException("MEDICAL_COMPLIANCE_BLOCKED", "文章未通过医疗合规审核，禁止审核通过或发布");
        }
    }

    private List<RuleSpec> allRules() {
        List<RuleSpec> rules = new ArrayList<>(DEFAULT_RULES);
        List<MedicalComplianceRule> dbRules;
        try {
            dbRules = ruleMapper.selectList(new LambdaQueryWrapper<MedicalComplianceRule>()
                    .eq(MedicalComplianceRule::getEnabled, 1));
        } catch (Exception ignored) {
            return rules;
        }
        for (MedicalComplianceRule dbRule : dbRules) {
            if (!StringUtils.hasText(dbRule.getPattern())) {
                continue;
            }
            try {
                rules.add(new RuleSpec(
                        dbRule.getRuleType(),
                        StringUtils.hasText(dbRule.getSeverity()) ? dbRule.getSeverity() : "high",
                        dbRule.getPattern(),
                        dbRule.getDescription()
                ));
            } catch (PatternSyntaxException ignored) {
                // Invalid custom rules are ignored instead of breaking article workflows.
            }
        }
        return rules;
    }

    private record RuleSpec(String ruleType, String severity, String rawPattern, String description, Pattern pattern) {
        private RuleSpec(String ruleType, String severity, String rawPattern, String description) {
            this(ruleType, severity, rawPattern, description, Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE));
        }
    }
}
