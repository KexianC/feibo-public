package com.hzmwmt.config;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopywritingAuditMapperContractTest {

    @Test
    void visibilityMustBeBoundToServerScopeAndRevalidatedDatabaseRole() throws IOException {
        String xml = resource("/mybatis/mapper/CopywritingApprovalHistoryMapper.xml");
        String visibility = element(xml, "sql", "visibleCopywritingCondition");

        assertTrue(visibility.contains("copywriting.user_id = #{viewerUserId}"), "author can only see own content");
        assertTrue(visibility.contains("allAccess"));
        assertTrue(visibility.contains("upper(global_admin_role.name) in ('ADMIN', 'SUPER_ADMIN', 'SUPERADMIN')"));
        assertTrue(visibility.contains("coalesce(global_admin.status, 0) = 0"));
        assertTrue(visibility.contains("t_copywriting_expert_assignment"));
        assertTrue(visibility.contains("expert_assignment.account_user_id = copywriting.user_id"));
        assertTrue(visibility.contains("upper(expert_role.name) = 'EXPERT'"));
        assertTrue(visibility.contains("t_copywriting_organization_admin_assignment"));
        assertTrue(visibility.contains("organization_assignment.account_user_id = copywriting.user_id"));
        assertTrue(visibility.contains("upper(organization_role.name) = 'ORG_ADMIN'"));
        assertTrue(visibility.contains("coalesce(expert_user.status, 0) = 0"));
        assertTrue(visibility.contains("coalesce(organization_user.status, 0) = 0"));

        assertFalse(visibility.contains("current_processor_id"),
                "stale current processor must not retain access after assignment removal");
        assertFalse(visibility.contains("history.operator_id"),
                "a historical action must not retain full-document access after assignment removal");
    }

    @Test
    void auditListAndCountMustSharePredicateAndExcludeOrphanRows() throws IOException {
        String xml = resource("/mybatis/mapper/CopywritingApprovalHistoryMapper.xml");
        String joins = element(xml, "sql", "auditRecordJoins");
        String count = element(xml, "select", "countVisibleAuditRecords");
        String list = element(xml, "select", "selectVisibleAuditRecords");

        assertTrue(joins.contains("inner join t_copywriting_approval_flow"));
        assertTrue(joins.contains("inner join t_copywriting copywriting"));
        assertTrue(joins.contains("left join t_user operator_user"),
                "deleted operators must not remove otherwise valid history rows");
        assertTrue(count.contains("<include refid=\"auditRecordFilters\"/>"));
        assertTrue(list.contains("<include refid=\"auditRecordFilters\"/>"));
        String filters = element(xml, "sql", "auditRecordFilters");
        assertTrue(filters.contains("history.operator_id = 0"),
                "legacy AI rows with operator_id=0 must remain filterable as AI");
    }

    @Test
    void legacyHistoryQueryMustReturnStableIdentifiersAndOrder() throws IOException {
        String xml = resource("/mybatis/mapper/CopywritingApprovalHistoryMapper.xml");
        String query = element(xml, "select", "selectApprovalHistoryListByCopywritingIds");

        assertTrue(query.contains("copywriting_approval_history_id"));
        assertTrue(query.contains("copywriting_approval_flow_id"));
        assertTrue(query.contains("order by create_time asc, id asc"));
    }

    @Test
    void detailMustSelectOneDeterministicLatestFlow() throws IOException {
        String xml = resource("/mybatis/mapper/CopywritingApprovalFlowMapper.xml");
        String query = element(xml, "select", "selectLatestByCopywritingId");
        assertTrue(query.contains("order by id desc"));
        assertTrue(query.contains("limit 1"));
    }

    @Test
    void detailContentAndVisibilityCheckMustBeOneAtomicSelect() throws IOException {
        String xml = resource("/mybatis/mapper/CopywritingApprovalHistoryMapper.xml");
        String query = element(xml, "select", "selectVisibleCopywritingById");
        assertTrue(query.contains("copywriting.content"));
        assertTrue(query.contains("<include refid=\"visibleCopywritingCondition\"/>"));
        assertFalse(xml.contains("id=\"countVisibleCopywriting\""));
    }

    @Test
    void detailAndPagedAuditRecordsMustCoverTheCompleteCopywritingLifecycle() throws IOException {
        String xml = resource("/mybatis/mapper/CopywritingApprovalHistoryMapper.xml");
        String detailHistory = element(xml, "select", "selectVisibleAuditRecordsByCopywritingId");
        String pagedHistory = element(xml, "select", "selectVisibleAuditRecords");

        assertTrue(detailHistory.contains("copywriting.copywriting_id = #{copywritingId}"));
        assertFalse(detailHistory.contains("copywritingApprovalFlowId"),
                "detail history must include earlier review rounds for the same copywriting");
        assertTrue(detailHistory.contains("approval_flow.create_time asc"));
        assertFalse(pagedHistory.contains("copywritingApprovalFlowId"),
                "paged audit records intentionally cover the complete copywriting lifecycle");
    }

    @Test
    void mybatisCanParseAndRenderBothExpertAndOrganizationVisibilityBranches() throws IOException {
        Configuration configuration = new Configuration();
        String resource = "mybatis/mapper/CopywritingApprovalHistoryMapper.xml";
        try (InputStream stream = getClass().getResourceAsStream("/" + resource)) {
            assertNotNull(stream);
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("viewerUserId", 202L);
        parameters.put("allAccess", false);
        parameters.put("reviewExpert", true);
        parameters.put("reviewOrganization", true);
        parameters.put("copywritingId", null);
        parameters.put("auditorStage", null);
        parameters.put("approved", null);
        parameters.put("offset", 0L);
        parameters.put("size", 10);
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.hzmwmt.infrastructure.dao.CopywritingApprovalHistoryMapper.selectVisibleAuditRecords")
                .getBoundSql(parameters);
        String sql = boundSql.getSql().replaceAll("\\s+", " ");

        assertTrue(sql.contains("t_copywriting_expert_assignment"));
        assertTrue(sql.contains("t_copywriting_organization_admin_assignment"));
        assertTrue(sql.contains("limit ?, ?"));
        assertFalse(sql.contains("<include"));
    }

    @Test
    void roleMatrixNeverRendersAnAssignmentBranchWithoutThatServerRoleFlag() throws IOException {
        Configuration configuration = new Configuration();
        String resource = "mybatis/mapper/CopywritingApprovalHistoryMapper.xml";
        try (InputStream stream = getClass().getResourceAsStream("/" + resource)) {
            assertNotNull(stream);
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String ownerOnly = renderedDetailSql(configuration, false, false, false);
        assertFalse(ownerOnly.contains("t_copywriting_expert_assignment"));
        assertFalse(ownerOnly.contains("t_copywriting_organization_admin_assignment"));

        String expert = renderedDetailSql(configuration, false, true, false);
        assertTrue(expert.contains("t_copywriting_expert_assignment"));
        assertFalse(expert.contains("t_copywriting_organization_admin_assignment"));

        String organizationOrMappedAdmin = renderedDetailSql(configuration, false, false, true);
        assertFalse(organizationOrMappedAdmin.contains("t_copywriting_expert_assignment"));
        assertTrue(organizationOrMappedAdmin.contains("t_copywriting_organization_admin_assignment"));
        assertTrue(organizationOrMappedAdmin.contains("assignment.admin_user_id"));

        String globalAdmin = renderedDetailSql(configuration, true, false, false);
        assertTrue(globalAdmin.contains("global_admin_role"));
        assertTrue(globalAdmin.contains("SUPER_ADMIN"));
        assertFalse(globalAdmin.contains("t_copywriting_expert_assignment"));
        assertFalse(globalAdmin.contains("t_copywriting_organization_admin_assignment"));
    }

    private String renderedDetailSql(
            Configuration configuration,
            boolean allAccess,
            boolean expert,
            boolean organization) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("copywritingId", 101L);
        parameters.put("viewerUserId", 202L);
        parameters.put("allAccess", allAccess);
        parameters.put("reviewExpert", expert);
        parameters.put("reviewOrganization", organization);
        return configuration.getMappedStatement(
                        "com.hzmwmt.infrastructure.dao.CopywritingApprovalHistoryMapper.selectVisibleCopywritingById")
                .getBoundSql(parameters)
                .getSql()
                .replaceAll("\\s+", " ");
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path + " must be available on the classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .trim();
        }
    }

    private String element(String xml, String element, String id) {
        Pattern pattern = Pattern.compile(
                "<" + element + "\\s+id=\"" + Pattern.quote(id) + "\"[^>]*>(.*?)</" + element + ">",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        assertTrue(matcher.find(), element + "#" + id + " must exist");
        return matcher.group(1);
    }
}
