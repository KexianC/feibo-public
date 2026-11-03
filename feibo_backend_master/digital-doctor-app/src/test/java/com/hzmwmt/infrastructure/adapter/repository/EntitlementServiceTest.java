package com.hzmwmt.infrastructure.adapter.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.ai.adapter.port.IAIModelRuntimeManager;
import com.hzmwmt.domain.entitlement.*;
import com.hzmwmt.types.exception.AppException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hzmwmt.domain.entitlement.IEntitlementService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real SQL and transactions in H2 MySQL mode, including concurrent requests. */
class EntitlementServiceTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private EntitlementService service;
    private MutableClock clock;

    @BeforeEach void setUp() {
        JdbcDataSource datasource=new JdbcDataSource();
        datasource.setURL("jdbc:h2:mem:entitlements_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000");
        jdbc=new JdbcTemplate(datasource);
        jdbc.execute("CREATE TABLE t_user(user_id BIGINT PRIMARY KEY,status INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE t_role(role_id BIGINT PRIMARY KEY,name VARCHAR(64))");
        jdbc.execute("CREATE TABLE t_user_role_map(user_id BIGINT,role_id BIGINT)");
        jdbc.execute("CREATE TABLE t_ai_feature_template(ai_feature_template_id BIGINT PRIMARY KEY,template_code VARCHAR(64),name VARCHAR(64),description VARCHAR(500),create_time TIMESTAMP,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE t_ai_model_config(id BIGINT AUTO_INCREMENT PRIMARY KEY,ai_model_config_id BIGINT,name VARCHAR(100),display_name VARCHAR(100),type TINYINT,is_leaf TINYINT DEFAULT 1,is_deleted TINYINT DEFAULT 0,sort INT DEFAULT 1,update_time TIMESTAMP)");
        jdbc.update("INSERT INTO t_ai_model_config(ai_model_config_id,name,type) VALUES (100,'测试模型',TRUE)");
        jdbc.execute("CREATE TABLE t_ai_membership_plan(id BIGINT AUTO_INCREMENT PRIMARY KEY,ai_membership_plan_id BIGINT UNIQUE,plan_code VARCHAR(64),name VARCHAR(64),price DECIMAL(12,2),duration_days INT,is_active BOOLEAN,create_time TIMESTAMP,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE t_ai_recharge_pack(id BIGINT AUTO_INCREMENT PRIMARY KEY,ai_recharge_pack_id BIGINT UNIQUE,pack_code VARCHAR(64),name VARCHAR(64),price DECIMAL(12,2),ai_feature_template_id BIGINT NOT NULL,`value` DECIMAL(12,2),description VARCHAR(500),is_active TINYINT,create_time TIMESTAMP,update_time TIMESTAMP)");
        new ResourceDatabasePopulator(new FileSystemResource("../docs/sql/platform_entitlements_20260909.sql")).execute(datasource);
        jdbc.update("INSERT INTO t_user(user_id) VALUES (1),(2),(3),(4),(5)");
        transaction=new TransactionTemplate(new DataSourceTransactionManager(datasource));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        clock=new MutableClock(Instant.parse("2026-09-09T15:59:00Z"));
        service=new EntitlementService(jdbc,new ObjectMapper(),mock(IAIModelRuntimeManager.class),clock);
    }
    private void run(Runnable action) { transaction.executeWithoutResult(status->action.run()); }
    private Map<String,Object> account(long id) { return transaction.execute(status->service.snapshot(id)); }
    private long value(Map<String,Object> snapshot,String key) { return ((Number)snapshot.get(key)).longValue(); }
    private void spend(long user,String resource,long units,String key) { run(()->{service.reserve(user,resource,units,key);service.commit(user,key);}); }

    @Test void everyAccountHasFreeQuotaAndOnlyPremiumChatResetsAtShanghaiMidnight() {
        assertEquals(10,value(account(1),"premiumChatTodayRemaining"));
        assertEquals(300,value(account(1),"digitalHumanSecondsRemaining"));
        assertEquals(60,value(account(1),"aiVideoSecondsRemaining"));
        spend(1,PREMIUM_CHAT,10,"today");
        spend(1,DIGITAL_HUMAN_SECONDS,300,"digital");
        spend(1,AI_VIDEO_SECONDS,60,"video");
        AppException denied=assertThrows(AppException.class,()->spend(1,PREMIUM_CHAT,1,"too-much"));
        assertEquals("QUOTA_EXCEEDED",denied.getCode());
        clock.advance(Duration.ofMinutes(2));
        assertEquals(10,value(account(1),"premiumChatTodayRemaining"));
        assertEquals(0,value(account(1),"digitalHumanSecondsRemaining"));
        assertEquals(0,value(account(1),"aiVideoSecondsRemaining"));
        assertEquals(300,value(account(2),"digitalHumanSecondsRemaining"));
    }
    @Test void concurrentRequestsNeverSpendAboveDailyLimit() throws Exception {
        account(1);AtomicInteger accepted=new AtomicInteger(),denied=new AtomicInteger();
        parallel(30,index->{
            try { spend(1,PREMIUM_CHAT,1,"parallel:"+index);accepted.incrementAndGet(); }
            catch(AppException exception) { assertEquals("QUOTA_EXCEEDED",exception.getCode());denied.incrementAndGet(); }
        });
        assertEquals(10,accepted.get());assertEquals(20,denied.get());
        assertEquals(10,value(account(1),"premiumChatTodayUsed"));
        assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM t_entitlement_reservation",Long.class));
    }
    @Test void concurrentSeparateReserveAndCommitTransactionsCompleteWithoutRetry() throws Exception {
        account(1);AtomicInteger accepted=new AtomicInteger(),denied=new AtomicInteger();
        // Match the real asynchronous entry points: reservation and settlement acquire
        // the same existing account in separate transactions, with no retry wrapper.
        parallel(30,index->{
            String key="separate:"+index;
            try {
                run(()->service.reserve(1L,PREMIUM_CHAT,1,key));
                run(()->service.commit(1L,key));
                accepted.incrementAndGet();
            } catch(AppException exception) {
                assertEquals("QUOTA_EXCEEDED",exception.getCode());denied.incrementAndGet();
            }
        });
        assertEquals(10,accepted.get());assertEquals(20,denied.get());
        assertEquals(10,value(account(1),"premiumChatTodayUsed"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM t_entitlement_reservation WHERE status='RESERVED'",Long.class));
    }
    @Test void resizeReleaseAndDuplicateCallbacksPreserveExactlyOnceAccounting() {
        run(()->service.reserve(1L,AI_VIDEO_SECONDS,40,"job"));
        assertEquals(20,value(account(1),"aiVideoSecondsRemaining"));
        assertThrows(AppException.class,()->run(()->service.resize(1L,"job",61)));
        assertEquals(40,value(account(1),"aiVideoSecondsReserved"));
        run(()->service.resize(1L,"job",20));run(()->service.commit(1L,"job"));
        run(()->service.commit(1L,"job"));run(()->service.release(1L,"job"));
        run(()->service.resize(1L,"job",59));
        assertEquals(20,value(account(1),"aiVideoSecondsUsed"));
        assertEquals(40,value(account(1),"aiVideoSecondsRemaining"));
        run(()->service.reserve(1L,AI_VIDEO_SECONDS,40,"failure"));
        run(()->service.release(1L,"failure"));run(()->service.release(1L,"failure"));
        assertThrows(AppException.class,()->run(()->service.commit(1L,"failure")));
        assertEquals(40,value(account(1),"aiVideoSecondsRemaining"));
        assertThrows(AppException.class,()->run(()->service.reserve(1L,AI_VIDEO_SECONDS,40,"failure")));
    }
    @Test void concurrentPaidCallbacksGrantOnceAndRollbackWithOrderTransaction() throws Exception {
        String snapshot=paidSnapshot(120,1); account(1);
        parallel(20,index->run(()->service.grantPaidOrder(1L,9001L,snapshot)));
        assertEquals(180,value(account(1),"aiVideoSecondsRemaining"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_entitlement_grant",Long.class));
        assertThrows(IllegalStateException.class,()->run(()->{
            service.grantPaidOrder(1L,9002L,snapshot);throw new IllegalStateException("order rollback");
        }));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_entitlement_grant",Long.class));
        assertThrows(AppException.class,()->run(()->service.grantPaidOrder(1L,9001L,paidSnapshot(999,1))));
    }
    @Test void expiringPurchasedCreditsAreUsedAfterDailyAllowanceAndDoNotReturnAfterExpiry() {
        EntitlementGrantRequest grant=grantRequest("bonus",1,5L);
        grant.setAiVideoSeconds(90L);run(()->service.grant(1L,grant,2L));
        spend(1,PREMIUM_CHAT,10,"free-first");
        assertEquals(5,value(account(1),"premiumChatCreditsRemaining"));
        spend(1,PREMIUM_CHAT,2,"extra");
        assertEquals(3,value(account(1),"premiumChatCreditsRemaining"));
        // The expiring 90-second bucket is consumed before the lifetime free bucket.
        spend(1,AI_VIDEO_SECONDS,50,"expiring-video");
        assertEquals(100,value(account(1),"aiVideoSecondsRemaining"));
        clock.advance(Duration.ofDays(2));
        assertEquals(0,value(account(1),"premiumChatCreditsRemaining"));
        assertEquals(60,value(account(1),"aiVideoSecondsRemaining"));
    }
    @Test void quotaEditsAdjustRemainingWithoutResettingAlreadyConsumedBalances() {
        spend(1,DIGITAL_HUMAN_SECONDS,200,"existing");
        FreeEntitlementPolicy policy=new FreeEntitlementPolicy();policy.setPremiumChatDailyLimit(10L);policy.setDigitalHumanSeconds(100L);policy.setAiVideoSeconds(60L);
        run(()->service.updateFreePlan(policy));assertEquals(0,value(account(1),"digitalHumanSecondsRemaining"));
        policy.setDigitalHumanSeconds(400L);run(()->service.updateFreePlan(policy));
        assertEquals(200,value(account(1),"digitalHumanSecondsRemaining"));
    }
    @Test void onlyEnabledAdministratorRolesHaveUnlimitedQuotaAndTheyDoNotSpendBuckets() {
        jdbc.update("INSERT INTO t_role(role_id,name) VALUES(10,'ADMIN'),(11,'SUPERADMIN'),(12,'ORG_ADMIN'),(13,'EXPERT')");
        jdbc.update("INSERT INTO t_user_role_map(user_id,role_id) VALUES(1,10),(2,11),(3,12),(4,13)");
        for(long user:List.of(1L,2L,3L)) {
            assertTrue(service.isUnlimited(user));spend(user,AI_VIDEO_SECONDS,9999,"admin-job");
            assertEquals(-1,value(account(user),"aiVideoSecondsRemaining"));
            assertEquals(0,value(account(user),"aiVideoSecondsUsed"));
        }
        assertFalse(service.isUnlimited(4L));assertFalse(service.isUnlimited(5L));
        jdbc.update("UPDATE t_user SET status=1 WHERE user_id=1");assertFalse(service.isUnlimited(1L));
        assertFalse((Boolean)account(1).get("unlimited"));
        assertThrows(AppException.class,()->spend(1,AI_VIDEO_SECONDS,1,"disabled"));
    }
    @Test void freeGiftPlansAreGrantableButNotPurchasableAndPaidSnapshotIsImmutable() {
        EntitlementProductRequest request=product("赠送会员",BigDecimal.ZERO,20L,120L);
        Map<String,Object> created=transaction.execute(status->service.saveProduct(false,null,request));
        Long id=Long.valueOf(created.get("productId").toString());
        assertThrows(AppException.class,()->service.snapshotProduct(false,id));
        assertTrue(((List<?>)service.configuration(true).get("plans")).isEmpty());
        EntitlementGrantRequest gift=grantRequest("gift",30,0L);gift.setPlanId(id.toString());
        run(()->service.grant(1L,gift,2L));assertEquals(20,value(account(1),"premiumChatDailyLimit"));
        request.setPrice(new BigDecimal("19.99"));run(()->service.saveProduct(false,id,request));
        String snapshot=service.snapshotProduct(false,id);
        request.setAiVideoSeconds(999L);request.setActive(false);run(()->service.saveProduct(false,id,request));
        run(()->service.grant(1L,gift,2L)); // An accepted grant stays idempotent after product changes.
        run(()->service.grantPaidOrder(2L,800L,snapshot));
        assertEquals(180,value(account(2),"aiVideoSecondsRemaining"));
        assertTrue(((List<?>)service.configuration(true).get("plans")).isEmpty());
    }
    @Test void membershipRenewalExtendsExpiryAndPremiumUnlimitedDoesNotGrantUnlimitedVideo() {
        EntitlementProductRequest request=product("高级会员",new BigDecimal("99"),-1L,120L);
        Map<String,Object> created=transaction.execute(status->service.saveProduct(false,null,request));
        Long id=Long.valueOf(created.get("productId").toString());String snapshot=service.snapshotProduct(false,id);
        run(()->service.grantPaidOrder(1L,701L,snapshot));run(()->service.grantPaidOrder(1L,702L,snapshot));
        spend(1,PREMIUM_CHAT,999,"unlimited");assertEquals(-1,value(account(1),"premiumChatTodayRemaining"));
        assertEquals(300,value(account(1),"aiVideoSecondsRemaining"));
        clock.advance(Duration.ofDays(31));
        assertEquals(-1,value(account(1),"premiumChatTodayRemaining"));
        assertEquals(180,value(account(1),"aiVideoSecondsRemaining"));
        clock.advance(Duration.ofDays(30));
        assertEquals(10,value(account(1),"premiumChatTodayRemaining"));
        assertEquals(60,value(account(1),"aiVideoSecondsRemaining"));
    }
    @Test void resourcesAndBusinessKeysCannotBeReusedWithDifferentUnitsOrIdentity() {
        run(()->service.reserve(1L,AI_VIDEO_SECONDS,5,"same"));
        assertThrows(AppException.class,()->run(()->service.reserve(1L,DIGITAL_HUMAN_SECONDS,5,"same")));
        assertThrows(AppException.class,()->run(()->service.reserve(1L,AI_VIDEO_SECONDS,6,"same")));
        spend(2,AI_VIDEO_SECONDS,6,"same");assertEquals(54,value(account(2),"aiVideoSecondsRemaining"));
        assertThrows(AppException.class,()->run(()->service.reserve(1L,"FAKE",1,"new")));
        assertThrows(AppException.class,()->run(()->service.reserve(1L,AI_VIDEO_SECONDS,-1,"new")));
    }
    @Test void firstMigrationMakesModelsOrdinaryAndRerunPreservesAdminClassification() {
        assertFalse(jdbc.queryForObject("SELECT type FROM t_ai_model_config WHERE ai_model_config_id=100",Boolean.class));
        run(()->service.setModelPremium(100L,true));
        new ResourceDatabasePopulator(new FileSystemResource("../docs/sql/platform_entitlements_20260909.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
        assertTrue(jdbc.queryForObject("SELECT type FROM t_ai_model_config WHERE ai_model_config_id=100",Boolean.class));
        assertEquals(10,value(account(1),"premiumChatDailyLimit"));
    }
    @Test void rechargeProductUsesExistingProductIdentityAndGrantsConfiguredSeconds() {
        EntitlementProductRequest request=product("视频时长包",new BigDecimal("10.00"),0,90);
        request.setDurationDays(-1);request.setDigitalHumanSeconds(0L);request.setPremiumChatCredits(5L);
        Map<String,Object> created=transaction.execute(status->service.saveProduct(true,null,request));
        Long productId=Long.valueOf(created.get("productId").toString());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_recharge_pack WHERE ai_recharge_pack_id=? AND ai_feature_template_id IS NOT NULL",Long.class,productId));
        String snapshot=service.snapshotProduct(true,productId);
        run(()->service.grantPaidOrder(1L,808L,snapshot));
        assertEquals(150,value(account(1),"aiVideoSecondsRemaining"));assertEquals(5,value(account(1),"premiumChatCreditsRemaining"));
        request.setActive(false);run(()->service.saveProduct(true,productId,request));
        assertTrue(((List<?>)service.configuration(true).get("rechargePacks")).isEmpty());
    }
    private EntitlementGrantRequest grantRequest(String key,int days,long credits) {
        EntitlementGrantRequest request=new EntitlementGrantRequest();request.setRequestId(key);request.setDurationDays(days);request.setPremiumChatCredits(credits);request.setReason("测试发放");return request;
    }
    private EntitlementProductRequest product(String name,BigDecimal price,long daily,long video) {
        EntitlementProductRequest request=new EntitlementProductRequest();request.setName(name);request.setPrice(price);request.setDurationDays(30);request.setActive(true);request.setPremiumChatDailyLimit(daily);request.setAiVideoSeconds(video);request.setDigitalHumanSeconds(300L);request.setPremiumChatCredits(0L);return request;
    }
    private String paidSnapshot(long video,int days) {
        return "{\"version\":1,\"productType\":\"RECHARGE\",\"productId\":\"99\",\"planName\":\"视频充值\",\"price\":19.99,\"durationDays\":"+days+",\"premiumChatDailyLimit\":0,\"digitalHumanSeconds\":0,\"aiVideoSeconds\":"+video+",\"premiumChatCredits\":0}";
    }
    private void parallel(int count,java.util.function.IntConsumer action) throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(10);
        try {
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<count;i++){int index=i;futures.add(executor.submit(()->action.accept(index)));}
            for(Future<?> future:futures) future.get(30,TimeUnit.SECONDS);
        } finally {executor.shutdownNow();}
    }
    private static final class MutableClock extends Clock {
        private Instant value;
        MutableClock(Instant value){this.value=value;}
        void advance(Duration duration){value=value.plus(duration);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return value;}
    }
}
