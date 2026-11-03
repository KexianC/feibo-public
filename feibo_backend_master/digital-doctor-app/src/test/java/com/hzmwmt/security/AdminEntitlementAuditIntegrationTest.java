package com.hzmwmt.security;

import com.hzmwmt.aop.OperationLogAspect;
import com.hzmwmt.config.MybatisPlusConfig;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.entitlement.*;
import com.hzmwmt.domain.entity.log.TOperationLogs;
import com.hzmwmt.domain.service.log.OperationLogsService;
import org.junit.jupiter.api.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real method-security, transaction and production audit advisors on the actual controller. */
class AdminEntitlementAuditIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate db;
    private AdminEntitlementController controller;
    private IEntitlementAdminService service;
    private OperationLogsService logs;

    @BeforeEach void setup() {
        var datasource=new DriverManagerDataSource("jdbc:h2:mem:audit_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(datasource);
        db.execute("CREATE TABLE test_entitlement_writes(id BIGINT AUTO_INCREMENT PRIMARY KEY)");
        db.execute("CREATE TABLE test_entitlement_audits(id BIGINT AUTO_INCREMENT PRIMARY KEY)");
        service=mock(IEntitlementAdminService.class);logs=mock(OperationLogsService.class);
        IEntitlementService entitlementService=mock(IEntitlementService.class);
        IAuthService auth=mock(IAuthService.class);AuthUserEntity user=AuthUserEntity.builder().userId(7L).build();
        when(auth.queryValidatedUser()).thenReturn(user);
        doAnswer(call->{businessWrite();return null;}).when(service).updateFreePlan(any());
        doAnswer(call->{businessWrite();return null;}).when(service).setModelPremium(anyLong(),anyBoolean());
        when(service.saveProduct(anyBoolean(),nullable(Long.class),any())).thenAnswer(call->{businessWrite();return Map.of("productId","55");});
        doAnswer(call->{businessWrite();return null;}).when(entitlementService).grant(anyLong(),any(),anyLong());
        when(entitlementService.snapshot(anyLong())).thenReturn(Map.of("userId","9"));
        when(logs.save(any(TOperationLogs.class))).thenAnswer(call->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            db.update("INSERT INTO test_entitlement_audits DEFAULT VALUES");return true;
        });
        context=new AnnotationConfigApplicationContext();
        context.register(MybatisPlusConfig.class,SecurityConfiguration.class,OperationLogAspect.class,AdminEntitlementController.class);
        context.registerBean("transactionManager",DataSourceTransactionManager.class,()->new DataSourceTransactionManager(datasource));
        context.registerBean(IEntitlementAdminService.class,()->service);
        context.registerBean(IEntitlementService.class,()->entitlementService);
        context.registerBean(IAuthService.class,()->auth);
        context.registerBean(OperationLogsService.class,()->logs);
        context.registerBean(ThreadPoolExecutor.class,()->mock(ThreadPoolExecutor.class));
        context.refresh();controller=context.getBean(AdminEntitlementController.class);
    }
    @AfterEach void tearDown(){SecurityContextHolder.clearContext();if(context!=null)context.close();}
    private void businessWrite(){assertTrue(TransactionSynchronizationManager.isActualTransactionActive());db.update("INSERT INTO test_entitlement_writes DEFAULT VALUES");}
    private void login(String... codes){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("operator","",Arrays.stream(codes).map(SimpleGrantedAuthority::new).toList()));}
    private long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}

    @Test void everySuperAdminWriteRunsBusinessAndCriticalAuditInOneTransaction() {
        login("ROLE_SUPER_ADMIN");assertTrue(AopUtils.isAopProxy(controller));
        controller.free(new FreeEntitlementPolicy());
        controller.newPlan(new EntitlementProductRequest());controller.plan(1L,new EntitlementProductRequest());
        controller.newPack(new EntitlementProductRequest());controller.pack(2L,new EntitlementProductRequest());
        controller.model(3L,new AdminEntitlementController.ModelTier(true));
        controller.grant(9L,new EntitlementGrantRequest());
        assertEquals(7,count("test_entitlement_writes"));assertEquals(7,count("test_entitlement_audits"));
    }
    @Test void administratorAndFinanceCapabilityCannotModifyEntitlements() {
        login("ROLE_ADMIN","finance.manage");
        assertThrows(AccessDeniedException.class,()->controller.free(new FreeEntitlementPolicy()));
        login("ROLE_ORG_ADMIN");
        assertThrows(AccessDeniedException.class,()->controller.newPlan(new EntitlementProductRequest()));
        verifyNoInteractions(service);assertEquals(0,count("test_entitlement_writes"));
    }
    @Test void failedCriticalAuditRollsBackTheActualEntitlementWrite() {
        login("ROLE_SUPER_ADMIN");
        doAnswer(call->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            db.update("INSERT INTO test_entitlement_audits DEFAULT VALUES");return false;
        }).when(logs).save(any(TOperationLogs.class));
        assertThrows(IllegalStateException.class,()->controller.free(new FreeEntitlementPolicy()));
        assertEquals(0,count("test_entitlement_writes"));assertEquals(0,count("test_entitlement_audits"));
        verify(service).updateFreePlan(any());
    }
    @Configuration(proxyBeanMethods=false)
    @EnableAspectJAutoProxy(proxyTargetClass=true)
    @EnableMethodSecurity
    static class SecurityConfiguration {}
}
