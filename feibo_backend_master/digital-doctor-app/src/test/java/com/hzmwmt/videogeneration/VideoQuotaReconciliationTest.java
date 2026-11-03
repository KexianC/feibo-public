package com.hzmwmt.videogeneration;

import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.hzmwmt.domain.security.AIModelApiKeyCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Base64;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoQuotaReconciliationTest {
    private JdbcTemplate db;
    private IEntitlementService quotas;
    private VideoQuotaReconciliationService reconciliation;
    private TransactionTemplate transaction;
    private DataSourceTransactionManager manager;
    private final VideoQuotaReconciliationService.ResolveFailure confirmed =
            new VideoQuotaReconciliationService.ResolveFailure("已核对供应商控制台，任务未受理",true);
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("db/video-generation.sql")).execute(source);
        db=new JdbcTemplate(source); manager=new DataSourceTransactionManager(source); transaction=new TransactionTemplate(manager);
        db.execute("CREATE TABLE t_entitlement_account(user_id BIGINT PRIMARY KEY)");
        db.execute("CREATE TABLE t_entitlement_reservation(user_id BIGINT,biz_key VARCHAR(128),status VARCHAR(16),PRIMARY KEY(user_id,biz_key))");
        db.execute("CREATE TABLE t_video_quota_reconciliation(kind VARCHAR(32),task_ref VARCHAR(128),user_id BIGINT,biz_key VARCHAR(128),operator_id BIGINT,reason VARCHAR(500),UNIQUE(kind,task_ref))");
        db.execute("CREATE TABLE t_digital_work(digital_work_id BIGINT PRIMARY KEY,user_id BIGINT,quota_biz_key VARCHAR(128),status INT,url VARCHAR(500),failure_reason VARCHAR(500),update_time TIMESTAMP)");
        quotas=mock(IEntitlementService.class);
        doAnswer(invocation -> {
            db.update("INSERT INTO t_entitlement_account(user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",invocation.<Long>getArgument(0));
            db.update("INSERT INTO t_entitlement_reservation VALUES (?,?,'RESERVED')",invocation.<Long>getArgument(0),invocation.<String>getArgument(3)); return null;
        }).when(quotas).reserve(anyLong(),anyString(),anyLong(),anyString());
        doAnswer(invocation -> {
            db.update("UPDATE t_entitlement_reservation SET status='RELEASED' WHERE user_id=? AND biz_key=?",invocation.<Long>getArgument(0),invocation.<String>getArgument(1)); return null;
        }).when(quotas).release(anyLong(),anyString());
        reconciliation=new VideoQuotaReconciliationService(db,quotas);
    }
    private void unknownTask() {
        quotas.reserve(42L,"AI_VIDEO_SECONDS",5,"ai-video:test");
        db.update("INSERT INTO ai_video_task(id,owner_id,request_id,provider,model,prompt,duration,resolution,ratio,status,quota_biz_key) VALUES ('test','42','request','seedance','model','prompt',5,'720P','16:9','unknown','ai-video:test')");
        clearInvocations(quotas);
    }
    @Test void confirmationAndReasonAreRequiredBeforeAnyRefund() {
        unknownTask();
        assertThatThrownBy(() -> reconciliation.resolveAi("test",new VideoQuotaReconciliationService.ResolveFailure("reason",false),1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reconciliation.resolveAi("test",new VideoQuotaReconciliationService.ResolveFailure(" ",true),1L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(quotas);
    }
    @Test void repeatedResolutionRefundsOnceAndPersistsTheReasonWithTheFailure() {
        unknownTask();
        transaction.executeWithoutResult(tx -> reconciliation.resolveAi("test",confirmed,1L));
        transaction.executeWithoutResult(tx -> reconciliation.resolveAi("test",confirmed,1L));
        verify(quotas).release(42L,"ai-video:test");
        assertThat(db.queryForObject("SELECT status FROM ai_video_task WHERE id='test'",String.class)).isEqualTo("failed");
        assertThat(db.queryForObject("SELECT reason FROM t_video_quota_reconciliation",String.class)).isEqualTo(confirmed.reason());
    }
    @Test void anAlreadyCommittedReservationCannotBeRefundedEvenWithAnOutdatedTaskStatus() {
        unknownTask();
        db.update("UPDATE t_entitlement_reservation SET status='COMMITTED'");
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> reconciliation.resolveAi("test",confirmed,1L))).hasMessageContaining("已结算");
        verify(quotas,never()).release(anyLong(),anyString());
    }
    @Test void aLateSuccessfulSubmitCannotResurrectAnAdministrativelyResolvedTask() {
        var gateway=mock(VideoProviderGateway.class);
        var service=new VideoGenerationService(db,new AIModelApiKeyCipher(Base64.getEncoder().encodeToString(new byte[32])),gateway,manager,quotas);
        service.save("seedance",new VideoGenerationService.Settings("Seedance","model",true,"test-key",100));
        when(gateway.submit(anyString(),anyString(),anyString(),any())).thenAnswer(call -> {
            String id=db.queryForObject("SELECT id FROM ai_video_task",String.class);
            transaction.executeWithoutResult(tx -> reconciliation.resolveAi(id,confirmed,1L));
            return "late-provider-task";
        });
        var task=service.create("42",new VideoGenerationService.Create(UUID.randomUUID().toString(),"seedance","prompt",5,"720P","16:9"));
        assertThat(task.status()).isEqualTo("failed");
        assertThat(service.refresh("42",task.id()).videoUrl()).isNull();
        verify(gateway,never()).query(anyString(),anyString(),anyString());
    }
    @Test void digitalResolutionPreservesTerminalFailureAndReturnsTheCorrectOwnersHold() {
        quotas.reserve(42L,"DIGITAL_HUMAN_SECONDS",30,"digital:test");
        db.update("INSERT INTO t_digital_work(digital_work_id,user_id,quota_biz_key) VALUES (999,42,'digital:test')");
        transaction.executeWithoutResult(tx -> reconciliation.resolveDigital(999L,confirmed,1L));
        verify(quotas).release(42L,"digital:test");
        assertThat(db.queryForObject("SELECT status FROM t_digital_work WHERE digital_work_id=999",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT status FROM t_entitlement_reservation WHERE biz_key='digital:test'",String.class)).isEqualTo("RELEASED");
    }
}
