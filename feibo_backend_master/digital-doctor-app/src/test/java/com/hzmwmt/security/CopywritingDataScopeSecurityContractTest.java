package com.hzmwmt.security;

import com.hzmwmt.api.ICopywritingApi;
import com.hzmwmt.api.dto.ApprovalRequestDTO;
import com.hzmwmt.api.dto.ClaimTaskRequestDTO;
import com.hzmwmt.api.dto.SubmitCopywritingRequestDTO;
import com.hzmwmt.trigger.http.client.CopywritingController;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CopywritingDataScopeSecurityContractTest {

    @Test
    void approvalPoolMatrixIsFailClosedForOwnerExpertOrganizationAndGlobalAdmin() throws IOException {
        Configuration configuration = parse("mybatis/mapper/CopywritingApprovalFlowMapper.xml");

        String owner = approvalListSql(configuration, false, false, false, 1);
        assertTrue(owner.contains("copywriting.user_id = ?"));
        assertFalse(owner.contains("t_copywriting_expert_assignment"));
        assertFalse(owner.contains("t_copywriting_organization_admin_assignment"));

        String expert = approvalListSql(configuration, false, true, false, 1);
        assertTrue(expert.contains("t_copywriting_expert_assignment"));
        assertTrue(expert.contains("current_processor_id is null"));
        assertTrue(expert.contains("current_processor_id = ?"));
        assertTrue(expert.contains("upper(role.name) = 'expert'"));
        assertFalse(expert.contains("t_copywriting_organization_admin_assignment"));

        String wrongExpertStage = approvalListSql(configuration, false, true, false, 3);
        assertTrue(wrongExpertStage.contains("copywriting.user_id = ?"));
        assertFalse(wrongExpertStage.contains("t_copywriting_expert_assignment"));

        String organization = approvalListSql(configuration, false, false, true, 3);
        assertTrue(organization.contains("t_copywriting_organization_admin_assignment"));
        assertTrue(organization.contains("upper(role.name) = 'org_admin'"));
        assertFalse(organization.contains("t_copywriting_expert_assignment"));

        String wrongOrganizationStage = approvalListSql(configuration, false, false, true, 1);
        assertTrue(wrongOrganizationStage.contains("copywriting.user_id = ?"));
        assertFalse(wrongOrganizationStage.contains("t_copywriting_organization_admin_assignment"));

        String global = approvalListSql(configuration, true, false, false, 1);
        assertTrue(global.contains("scope_admin_role"));
        assertTrue(global.contains("super_admin"));
        assertTrue(global.contains("superadmin"));
        assertTrue(global.contains("coalesce(scope_admin.status, 0) = 0"));
        assertFalse(global.contains("copywriting.user_id = ?"));
        assertFalse(global.contains("t_copywriting_expert_assignment"));
    }

    @Test
    void finalContentFetchRepeatsABAndCrossOrganizationVisibilityCheck() throws IOException {
        Configuration configuration = parse("mybatis/mapper/CopywritingMapper.xml");
        String statement = "com.hzmwmt.infrastructure.dao.CopywritingMapper.selectVisibleCopywritingListByIds";

        String userA = render(configuration, statement, contentParams(false, false, false));
        assertTrue(userA.contains("copywriting.user_id = ?"));
        assertFalse(userA.contains("t_copywriting_expert_assignment"));

        String assignedExpert = render(configuration, statement, contentParams(false, true, false));
        assertTrue(assignedExpert.contains("scope_expert_assignment.account_user_id = copywriting.user_id"));
        assertTrue(assignedExpert.contains("scope_expert_assignment.expert_user_id = ?"));

        String assignedOrganization = render(configuration, statement, contentParams(false, false, true));
        assertTrue(assignedOrganization.contains("scope_org_assignment.account_user_id = copywriting.user_id"));
        assertTrue(assignedOrganization.contains("scope_org_assignment.admin_user_id = ?"));
        assertTrue(assignedOrganization.contains("upper(scope_org_role.name) = 'org_admin'"));

        String global = render(configuration, statement, contentParams(true, false, false));
        assertTrue(global.contains("upper(scope_admin_role.name) in ('admin', 'super_admin', 'superadmin')"));
        assertFalse(global.contains("scope_expert_assignment"));
        assertFalse(global.contains("scope_org_assignment"));
    }

    @Test
    void claimsAndTransitionsUseAtomicStateProcessorAssignmentAndCopywritingConditions() throws IOException {
        String flowXml = normalized(resource("/mybatis/mapper/CopywritingApprovalFlowMapper.xml"));
        String expertClaim = element(flowXml, "update", "updateApprovalFlowCurrentProcessorIdOnExpert");
        String organizationClaim = element(flowXml, "update", "updateApprovalFlowCurrentProcessorIdOnPlatForm");
        String aiApprove = element(flowXml, "update", "updateApprovalFlowStatus2Expert");
        String expertApprove = element(flowXml, "update", "updateApprovalFlowStatus2PlatForm");
        String organizationApprove = element(flowXml, "update", "updateApprovalFlowStatus2Complete");
        String reject = element(flowXml, "update", "updateApprovalFlowStatus2Rejected");

        assertAtomicClaim(expertClaim, "current_status = 1", "assignedExpertCondition");
        assertAtomicClaim(organizationClaim, "current_status = 3", "assignedOrganizationAdminCondition");
        assertTrue(aiApprove.contains("current_processor_id is null"));
        assertTrue(aiApprove.contains("#{scopeactorid} = 0"));
        assertTransition(expertApprove, "current_status = 1");
        assertTrue(expertApprove.contains("current_processor_id = #{currentprocessorid}"));
        assertTrue(expertApprove.contains("assignedexpertcondition"));
        assertTrue(expertApprove.contains("<otherwise>and 1 = 0</otherwise>"));
        assertTransition(organizationApprove, "current_status = 3");
        assertTrue(organizationApprove.contains("assignedorganizationadmincondition"));
        assertTransition(reject, "current_status = #{currentstatus}");
        assertTrue(reject.contains("assignedexpertcondition"));
        assertTrue(reject.contains("assignedorganizationadmincondition"));
        assertTrue(reject.contains("#{scopeactorid} = 0"));
        assertTrue(reject.contains("currentstatus != 2 and currentprocessorid == null"));

        String copywritingXml = normalized(resource("/mybatis/mapper/CopywritingMapper.xml"));
        assertTrue(element(copywritingXml, "update", "updateStatus2Complete").contains("and status = 1"));
        assertTrue(element(copywritingXml, "update", "updateStatus2Failure").contains("and status = 1"));
    }

    @Test
    void requestDtosCannotOverrideAuthenticatedActorAndDisabledDelegationIsClosedInBothLayers()
            throws NoSuchMethodException {
        for (Class<?> dto : Set.of(
                ApprovalRequestDTO.class,
                ClaimTaskRequestDTO.class,
                SubmitCopywritingRequestDTO.class)) {
            Set<String> fields = Arrays.stream(dto.getDeclaredFields())
                    .map(Field::getName)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            assertFalse(fields.contains("userid"), dto.getSimpleName());
            assertFalse(fields.contains("auditorid"), dto.getSimpleName());
            assertFalse(fields.contains("operatorid"), dto.getSimpleName());
        }

        Method ai = CopywritingController.class.getMethod("approvalByAI", long.class);
        assertEquals("hasRole('ADMIN')", ai.getAnnotation(PreAuthorize.class).value());
        Method delegate = ICopywritingApi.class.getMethod(
                "delegateToAssistant", com.hzmwmt.api.dto.DelegateToAssistantRequestDTO.class);
        assertEquals("denyAll()", delegate.getAnnotation(PreAuthorize.class).value());
        Method delegateImplementation = CopywritingController.class.getMethod(
                "delegateToAssistant", com.hzmwmt.api.dto.DelegateToAssistantRequestDTO.class);
        assertEquals("denyAll()", delegateImplementation.getAnnotation(PreAuthorize.class).value());
    }

    private void assertAtomicClaim(String sql, String expectedStatus, String assignmentFragment) {
        assertTrue(sql.contains("current_processor_id is null"));
        assertTrue(sql.contains(expectedStatus));
        assertTrue(sql.contains("copywriting_approval_flow_id = #{copywritingapprovalflowid}"));
        assertTrue(sql.contains(assignmentFragment.toLowerCase(Locale.ROOT)));
        assertTrue(sql.contains("globalWorkflowAdminCondition".toLowerCase(Locale.ROOT)));
    }

    private void assertTransition(String sql, String expectedStatus) {
        assertTrue(sql.contains("copywriting_approval_flow_id = #{copywritingapprovalflowid}"));
        assertTrue(sql.contains("copywriting_id = #{copywritingid}"));
        assertTrue(sql.contains(expectedStatus));
        assertTrue(sql.contains("globalWorkflowAdminCondition".toLowerCase(Locale.ROOT)));
    }

    private String approvalListSql(
            Configuration configuration,
            boolean allAccess,
            boolean expert,
            boolean organization,
            int status) {
        Map<String, Object> params = new HashMap<>();
        params.put("actorUserId", 200L);
        params.put("allAccess", allAccess);
        params.put("reviewExpert", expert);
        params.put("reviewOrganization", organization);
        params.put("approvalStatus", status);
        params.put("offset", 0L);
        params.put("size", 10);
        return render(configuration,
                "com.hzmwmt.infrastructure.dao.CopywritingApprovalFlowMapper.selectVisibleApprovalFlowsPage",
                params);
    }

    private Map<String, Object> contentParams(boolean allAccess, boolean expert, boolean organization) {
        Map<String, Object> params = new HashMap<>();
        params.put("copywritingIds", java.util.List.of(1001L, 1002L));
        params.put("actorUserId", 200L);
        params.put("allAccess", allAccess);
        params.put("reviewExpert", expert);
        params.put("reviewOrganization", organization);
        return params;
    }

    private Configuration parse(String resource) throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream stream = getClass().getResourceAsStream("/" + resource)) {
            assertNotNull(stream, resource);
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String render(Configuration configuration, String statement, Map<String, Object> parameters) {
        BoundSql boundSql = configuration.getMappedStatement(statement).getBoundSql(parameters);
        return normalized(boundSql.getSql());
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalized(String value) {
        return value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String element(String xml, String element, String id) {
        Matcher matcher = Pattern.compile(
                        "<" + element + "\\s+id=\"" + Pattern.quote(id.toLowerCase(Locale.ROOT))
                                + "\"[^>]*>(.*?)</" + element + ">",
                        Pattern.DOTALL)
                .matcher(xml);
        assertTrue(matcher.find(), element + "#" + id);
        return matcher.group(1);
    }
}
