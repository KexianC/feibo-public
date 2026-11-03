package com.hzmwmt.infrastructure.adapter.repository;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.ai.adapter.port.IAIModelRuntimeManager;
import com.hzmwmt.domain.entitlement.*;
import com.hzmwmt.types.exception.AppException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * InnoDB account-row locks serialize all spending and grants for one account.
 * Buckets preserve expiry and provenance, and reservations protect async jobs.
 * No quota is held in process memory or granted from a client-supplied identity.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class EntitlementService implements IEntitlementService, IEntitlementAdminService {
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> RESOURCES = Set.of(PREMIUM_CHAT, DIGITAL_HUMAN_SECONDS, AI_VIDEO_SECONDS);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IAIModelRuntimeManager modelRuntime;
    private final Clock clock;

    @Autowired
    public EntitlementService(JdbcTemplate jdbc, ObjectMapper json, IAIModelRuntimeManager modelRuntime) {
        this(jdbc, json, modelRuntime, Clock.systemUTC());
    }
    EntitlementService(JdbcTemplate jdbc, ObjectMapper json, IAIModelRuntimeManager modelRuntime, Clock clock) {
        this.jdbc = jdbc; this.json = json; this.modelRuntime = modelRuntime; this.clock = clock;
    }
    // DATETIME values are explicitly UTC. Never depend on JDBC/session/JVM timezone.
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    private AppException invalid(String message) { return new AppException("ENTITLEMENT_INVALID", message); }
    private void lockAccount(Long userId) {
        lockAccount(userId,false);
    }
    private void lockAccount(Long userId,boolean requireActive) {
        if (userId == null || userId <= 0 || jdbc.queryForObject("SELECT COUNT(*) FROM t_user WHERE user_id=?"+(requireActive?" AND status=0":""), Long.class, userId) != 1)
            throw invalid("账号不存在或已停用");
        // A duplicate INSERT IGNORE takes a shared record lock in InnoDB. Concurrent
        // callers would then deadlock upgrading it to FOR UPDATE; acquire the
        // exclusive account lock directly for both existing and newly created rows.
        jdbc.update("INSERT INTO t_entitlement_account(user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", userId);
        jdbc.queryForObject("SELECT user_id FROM t_entitlement_account WHERE user_id=? FOR UPDATE", Long.class, userId);
    }
    @Override public boolean isUnlimited(Long userId) {
        if (userId == null) return false;
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_user_role_map m JOIN t_role r ON r.role_id=m.role_id
                JOIN t_user u ON u.user_id=m.user_id WHERE m.user_id=? AND u.status=0
                AND UPPER(TRIM(r.name)) IN ('ADMIN','SUPER_ADMIN','SUPERADMIN','ORG_ADMIN')
                """, Long.class, userId) > 0;
    }
    private Map<String,Object> policy() { return jdbc.queryForMap("SELECT * FROM t_entitlement_policy WHERE id=1"); }
    private long number(Map<String,Object> row, String key) { Object value = row.get(key); return value == null ? 0 : ((Number)value).longValue(); }
    private String string(Map<String,Object> row, String key) { Object value = row.get(key); return value == null ? null : value.toString(); }
    private boolean bool(Map<String,Object> row, String key) { Object value = row.get(key); return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()!=0; }
    private long dailyLimit(Long userId, Map<String,Object> policy) {
        long limit = number(policy,"premium_chat_daily_limit");
        for (Map<String,Object> grant : activeGrants(userId)) {
            long value = number(grant,"premium_chat_daily_limit");
            if (limit < 0 || value < 0) return -1;
            limit = Math.max(limit, value);
        }
        return limit;
    }
    private List<Map<String,Object>> activeGrants(Long userId) {
        return jdbc.queryForList("SELECT * FROM t_entitlement_grant WHERE user_id=? AND starts_at<=? AND (expires_at IS NULL OR expires_at>?) ORDER BY id DESC FOR UPDATE", userId, now(), now());
    }
    private void ensureBase(Long userId) {
        Map<String,Object> policy = policy();
        ZonedDateTime today = clock.instant().atZone(QUOTA_ZONE).toLocalDate().atStartOfDay(QUOTA_ZONE);
        upsertBase(userId, PREMIUM_CHAT, "daily:"+today.toLocalDate(), Math.max(0,dailyLimit(userId,policy)), LocalDateTime.ofInstant(today.toInstant(),ZoneOffset.UTC), LocalDateTime.ofInstant(today.plusDays(1).toInstant(),ZoneOffset.UTC));
        upsertBase(userId, DIGITAL_HUMAN_SECONDS, "free:lifetime", number(policy,"digital_human_seconds"), LocalDateTime.of(2000,1,1,0,0), null);
        upsertBase(userId, AI_VIDEO_SECONDS, "free:lifetime", number(policy,"ai_video_seconds"), LocalDateTime.of(2000,1,1,0,0), null);
    }
    private void upsertBase(Long userId, String resource, String source, long capacity, LocalDateTime start, LocalDateTime expiry) {
        jdbc.update("""
                INSERT INTO t_entitlement_bucket(user_id,resource,source_key,capacity,starts_at,expires_at)
                VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE capacity=VALUES(capacity)
                """,userId,resource,source,capacity,start,expiry);
    }
    private List<Map<String,Object>> buckets(Long userId, String resource) {
        return jdbc.queryForList("""
                SELECT * FROM t_entitlement_bucket WHERE user_id=? AND resource=? AND starts_at<=?
                AND (expires_at IS NULL OR expires_at>?)
                ORDER BY CASE WHEN source_key LIKE 'daily:%' THEN 0 ELSE 1 END,
                CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END,expires_at,id FOR UPDATE
                """,userId,resource,now(),now());
    }
    private Map<String,Object> reservation(Long userId, String key) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM t_entitlement_reservation WHERE user_id=? AND biz_key=? FOR UPDATE",userId,key);
        return rows.isEmpty()?null:rows.get(0);
    }
    private void checkKey(String key) {
        if (key==null || key.isBlank() || key.length()>128) throw invalid("请求标识无效");
    }
    private void checkUnits(long units) { if(units<=0 || units>1_000_000_000L) throw invalid("额度数量无效"); }
    @Override public void reserve(Long userId, String resource, long units, String bizKey) {
        checkKey(bizKey); checkUnits(units);
        if (!RESOURCES.contains(resource)) throw invalid("权益资源类型无效");
        lockAccount(userId,true);
        Map<String,Object> previous=reservation(userId,bizKey);
        if(previous!=null) {
            if(!resource.equals(string(previous,"resource")) || units!=number(previous,"units")) throw invalid("重复请求的额度参数不一致");
            if("RELEASED".equals(string(previous,"status"))) throw invalid("已取消请求不能再次提交，请创建新请求");
            return;
        }
        ensureBase(userId);
        boolean unlimited=isUnlimited(userId) || PREMIUM_CHAT.equals(resource) && dailyLimit(userId,policy())<0;
        jdbc.update("INSERT INTO t_entitlement_reservation(user_id,biz_key,resource,units,status,unlimited) VALUES (?,?,?,?,'RESERVED',?)",userId,bizKey,resource,units,unlimited);
        long reservationId=number(reservation(userId,bizKey),"id");
        if(!unlimited) allocate(userId,resource,reservationId,units);
    }
    private void allocate(Long userId,String resource,long reservationId,long units) {
        List<Map<String,Object>> buckets=buckets(userId,resource);
        long available=buckets.stream().mapToLong(row->Math.max(0,number(row,"capacity")-number(row,"used")-number(row,"reserved"))).sum();
        if(available<units) throw new AppException("QUOTA_EXCEEDED", switch(resource) {
            case PREMIUM_CHAT -> "今日高级模型对话额度不足，请明天再试或补充权益；普通模型对话不限次数";
            case DIGITAL_HUMAN_SECONDS -> "数字人视频合成剩余额度不足，请充值或联系管理员发放权益";
            default -> "AI 视频生成剩余额度不足，请充值或联系管理员发放权益";
        });
        long remaining=units;
        for(Map<String,Object> bucket:buckets) {
            long take=Math.min(remaining,Math.max(0,number(bucket,"capacity")-number(bucket,"used")-number(bucket,"reserved")));
            if(take<=0) continue;
            long id=number(bucket,"id");
            jdbc.update("UPDATE t_entitlement_bucket SET reserved=reserved+? WHERE id=?",take,id);
            jdbc.update("INSERT INTO t_entitlement_allocation(reservation_id,bucket_id,units) VALUES (?,?,?) ON DUPLICATE KEY UPDATE units=units+VALUES(units)",reservationId,id,take);
            remaining-=take;
            if(remaining==0) break;
        }
    }
    @Override public void resize(Long userId,String bizKey,long actualUnits) {
        checkKey(bizKey); checkUnits(actualUnits); lockAccount(userId);
        Map<String,Object> row=reservation(userId,bizKey);
        if(row==null) throw invalid("未找到额度预占记录");
        if("COMMITTED".equals(string(row,"status"))) return;
        if(!"RESERVED".equals(string(row,"status"))) throw invalid("额度预占已释放");
        long delta=actualUnits-number(row,"units"),id=number(row,"id");
        if(!bool(row,"unlimited")) {
            if(delta>0) { ensureBase(userId); allocate(userId,string(row,"resource"),id,delta); }
            if(delta<0) reduceAllocation(id,-delta);
        }
        jdbc.update("UPDATE t_entitlement_reservation SET units=? WHERE id=?",actualUnits,id);
    }
    private void reduceAllocation(long reservationId,long reduction) {
        for(Map<String,Object> allocation:jdbc.queryForList("SELECT * FROM t_entitlement_allocation WHERE reservation_id=? ORDER BY bucket_id DESC FOR UPDATE",reservationId)) {
            long take=Math.min(reduction,number(allocation,"units"));
            long bucketId=number(allocation,"bucket_id");
            jdbc.update("UPDATE t_entitlement_bucket SET reserved=reserved-? WHERE id=?",take,bucketId);
            jdbc.update("UPDATE t_entitlement_allocation SET units=units-? WHERE reservation_id=? AND bucket_id=?",take,reservationId,bucketId);
            reduction-=take; if(reduction==0) break;
        }
    }
    @Override public void commit(Long userId,String bizKey) { settle(userId,bizKey,true); }
    @Override public void release(Long userId,String bizKey) { settle(userId,bizKey,false); }
    private void settle(Long userId,String key,boolean commit) {
        checkKey(key); lockAccount(userId);
        Map<String,Object> row=reservation(userId,key);
        if(row==null) { if(!commit) return; throw invalid("未找到额度预占记录"); }
        String state=string(row,"status");
        if("COMMITTED".equals(state)) return;
        if("RELEASED".equals(state)) {
            if(commit) throw invalid("额度预占已释放，不能结算或交付该任务");
            return;
        }
        long id=number(row,"id");
        for(Map<String,Object> allocation:jdbc.queryForList("SELECT * FROM t_entitlement_allocation WHERE reservation_id=? FOR UPDATE",id)) {
            long units=number(allocation,"units");
            jdbc.update("UPDATE t_entitlement_bucket SET reserved=reserved-?,used=used+? WHERE id=?",units,commit?units:0,number(allocation,"bucket_id"));
        }
        jdbc.update("UPDATE t_entitlement_reservation SET status=? WHERE id=?",commit?"COMMITTED":"RELEASED",id);
    }
    @Override public Map<String,Object> snapshot(Long userId) {
        lockAccount(userId); ensureBase(userId);
        boolean unlimited=isUnlimited(userId);
        List<Map<String,Object>> active=activeGrants(userId);
        Map<String,Object> latest=active.stream().filter(g->"MEMBERSHIP".equals(string(g,"product_type"))).findFirst().orElse(null);
        long daily=dailyLimit(userId,policy());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("userId",userId.toString()); result.put("planName",unlimited?"管理员":latest==null?"免费版":latest.get("plan_name"));
        result.put("unlimited",unlimited); result.put("expiresAt",latest==null?null:iso(latest.get("expires_at")));
        result.put("premiumChatDailyLimit",unlimited?-1:daily);
        long todayUsed=0,todayReserved=0,credits=0;
        for(Map<String,Object> bucket:buckets(userId,PREMIUM_CHAT)) {
            if(string(bucket,"source_key").startsWith("daily:")) { todayUsed=number(bucket,"used"); todayReserved=number(bucket,"reserved"); }
            else credits+=Math.max(0,number(bucket,"capacity")-number(bucket,"used")-number(bucket,"reserved"));
        }
        result.put("premiumChatTodayUsed",todayUsed); result.put("premiumChatTodayReserved",todayReserved);
        result.put("premiumChatTodayRemaining",unlimited||daily<0?-1:Math.max(0,daily-todayUsed-todayReserved));
        result.put("premiumChatCreditsRemaining",unlimited?-1:credits);
        summaryResource(result,userId,DIGITAL_HUMAN_SECONDS,"digitalHumanSeconds",unlimited);
        summaryResource(result,userId,AI_VIDEO_SECONDS,"aiVideoSeconds",unlimited);
        result.put("grants",jdbc.queryForList("SELECT * FROM t_entitlement_grant WHERE user_id=? ORDER BY id DESC LIMIT 100 FOR UPDATE",userId).stream().map(this::grantView).toList());
        result.put("timezone",QUOTA_ZONE.getId()); result.put("videoQuotaPeriod","LIFETIME");
        return result;
    }
    private Object iso(Object value) {
        if(value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().atOffset(ZoneOffset.UTC).toString();
        if(value instanceof LocalDateTime local) return local.atOffset(ZoneOffset.UTC).toString();
        return value==null?null:value.toString();
    }
    private void summaryResource(Map<String,Object> result,Long userId,String resource,String prefix,boolean unlimited) {
        List<Map<String,Object>> buckets=buckets(userId,resource);
        result.put(prefix+"Remaining",unlimited?-1:buckets.stream().mapToLong(b->Math.max(0,number(b,"capacity")-number(b,"used")-number(b,"reserved"))).sum());
        result.put(prefix+"Used",buckets.stream().mapToLong(b->number(b,"used")).sum());
        result.put(prefix+"Reserved",buckets.stream().mapToLong(b->number(b,"reserved")).sum());
    }
    private Map<String,Object> grantView(Map<String,Object> row) {
        Map<String,Object> view=new LinkedHashMap<>();
        view.put("requestId",row.get("source_key"));view.put("reason",row.get("reason"));view.put("planName",row.get("plan_name"));
        view.put("createdAt",iso(row.get("created_at")));view.put("expiresAt",iso(row.get("expires_at")));view.put("startsAt",iso(row.get("starts_at")));
        view.put("premiumChatDailyLimit",row.get("premium_chat_daily_limit"));view.put("premiumChatCredits",row.get("premium_chat_credits"));
        view.put("digitalHumanSeconds",row.get("digital_human_seconds"));view.put("aiVideoSeconds",row.get("ai_video_seconds"));
        return view;
    }

    @Override public Map<String,Object> configuration(boolean publicCatalogue) {
        Map<String,Object> p=policy();
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("freePlan",Map.of("premiumChatDailyLimit",p.get("premium_chat_daily_limit"),"digitalHumanSeconds",p.get("digital_human_seconds"),"aiVideoSeconds",p.get("ai_video_seconds")));
        result.put("plans",products(false).stream().filter(row->!publicCatalogue || isPurchasable(row)).toList());
        result.put("rechargePacks",products(true).stream().filter(row->!publicCatalogue || isPurchasable(row)).toList());
        if(!publicCatalogue) result.put("models",jdbc.queryForList("SELECT ai_model_config_id,name,display_name,type FROM t_ai_model_config WHERE is_leaf=1 AND COALESCE(is_deleted,0)=0 ORDER BY sort,id").stream().map(row->Map.of("modelId",string(row,"ai_model_config_id"),"name",row.get("display_name")==null?row.get("name"):row.get("display_name"),"premium",bool(row,"type"))).toList());
        return result;
    }
    private List<Map<String,Object>> products(boolean recharge) {
        String type=recharge?"RECHARGE":"MEMBERSHIP";
        String table=recharge?"t_ai_recharge_pack":"t_ai_membership_plan";
        String id=recharge?"ai_recharge_pack_id":"ai_membership_plan_id";
        return jdbc.queryForList("SELECT p."+id+" AS product_id,p.name,p.price,p.is_active,c.product_id AS configured_id,c.duration_days,c.premium_chat_daily_limit,c.digital_human_seconds,c.ai_video_seconds,c.premium_chat_credits FROM "+table+" p LEFT JOIN t_entitlement_product_config c ON c.product_type=? AND c.product_id=p."+id+(recharge?"":" WHERE p.plan_code<>'PLAN_FREE'")+" ORDER BY p.id",type).stream().map(row->{
            Map<String,Object> product=new LinkedHashMap<>();
            product.put("productId",string(row,"product_id")); product.put("name",row.get("name"));product.put("price",row.get("price"));
            product.put("active",recharge?!bool(row,"is_active"):bool(row,"is_active"));product.put("configured",row.get("configured_id")!=null);
            product.put("durationDays",row.get("duration_days")==null?-1:row.get("duration_days"));
            product.put("premiumChatDailyLimit",number(row,"premium_chat_daily_limit"));product.put("premiumChatCredits",number(row,"premium_chat_credits"));
            product.put("digitalHumanSeconds",number(row,"digital_human_seconds"));product.put("aiVideoSeconds",number(row,"ai_video_seconds"));
            return product;
        }).toList();
    }
    @Override public void updateFreePlan(FreeEntitlementPolicy request) {
        jdbc.update("UPDATE t_entitlement_policy SET premium_chat_daily_limit=?,digital_human_seconds=?,ai_video_seconds=? WHERE id=1",request.getPremiumChatDailyLimit(),request.getDigitalHumanSeconds(),request.getAiVideoSeconds());
    }
    @Override public Map<String,Object> saveProduct(boolean recharge,Long productId,EntitlementProductRequest request) {
        if(request.getDurationDays()==0) throw invalid("有效期须为正整数天或 -1（永久）");
        if(request.getPrice().scale()>2) throw invalid("价格最多支持两位小数");
        if(recharge && request.getPremiumChatCredits()==0 && request.getDigitalHumanSeconds()==0 && request.getAiVideoSeconds()==0) throw invalid("充值包必须包含至少一项非零权益");
        Long id=productId==null?IdWorker.getId():productId;
        if(productId==null) {
            if(recharge) jdbc.update("INSERT INTO t_ai_recharge_pack(ai_recharge_pack_id,pack_code,name,price,ai_feature_template_id,`value`,description,is_active,create_time,update_time) VALUES (?,?,?,?,(SELECT ai_feature_template_id FROM t_ai_feature_template WHERE template_code='PLATFORM_ENTITLEMENT' LIMIT 1),0,'平台权益充值包',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,"QUOTA_"+id,request.getName().trim(),request.getPrice(),request.getActive()?0:1);
            else jdbc.update("INSERT INTO t_ai_membership_plan(ai_membership_plan_id,plan_code,name,price,duration_days,is_active,create_time,update_time) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,"PLAN_"+id,request.getName().trim(),request.getPrice(),request.getDurationDays(),request.getActive());
        } else {
            int updated=recharge?jdbc.update("UPDATE t_ai_recharge_pack SET name=?,price=?,is_active=?,update_time=CURRENT_TIMESTAMP WHERE ai_recharge_pack_id=?",request.getName().trim(),request.getPrice(),request.getActive()?0:1,id):jdbc.update("UPDATE t_ai_membership_plan SET name=?,price=?,duration_days=?,is_active=?,update_time=CURRENT_TIMESTAMP WHERE ai_membership_plan_id=?",request.getName().trim(),request.getPrice(),request.getDurationDays(),request.getActive(),id);
            if(updated!=1) throw invalid("商品不存在");
        }
        jdbc.update("""
                INSERT INTO t_entitlement_product_config(product_type,product_id,duration_days,premium_chat_daily_limit,digital_human_seconds,ai_video_seconds,premium_chat_credits)
                VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE duration_days=VALUES(duration_days),premium_chat_daily_limit=VALUES(premium_chat_daily_limit),digital_human_seconds=VALUES(digital_human_seconds),ai_video_seconds=VALUES(ai_video_seconds),premium_chat_credits=VALUES(premium_chat_credits)
                """,recharge?"RECHARGE":"MEMBERSHIP",id,request.getDurationDays(),recharge?0:request.getPremiumChatDailyLimit(),request.getDigitalHumanSeconds(),request.getAiVideoSeconds(),request.getPremiumChatCredits());
        return products(recharge).stream().filter(p->id.toString().equals(p.get("productId"))).findFirst().orElseThrow();
    }
    @Override public void setModelPremium(Long modelId,boolean premium) {
        if(jdbc.update("UPDATE t_ai_model_config SET type=?,update_time=CURRENT_TIMESTAMP WHERE ai_model_config_id=? AND is_leaf=1 AND COALESCE(is_deleted,0)=0",premium,modelId)!=1) throw invalid("模型不存在或已删除");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { modelRuntime.invalidate(modelId); }
        });
    }
    private boolean isPurchasable(Map<String,Object> product) {
        return Boolean.TRUE.equals(product.get("active")) && Boolean.TRUE.equals(product.get("configured")) && new BigDecimal(product.get("price").toString()).signum()>0;
    }
    private Map<String,Object> productSnapshot(boolean recharge,Long productId,boolean purchase) {
        Map<String,Object> product=products(recharge).stream().filter(p->productId.toString().equals(p.get("productId"))).findFirst().orElseThrow(()->invalid("商品不存在"));
        if(!Boolean.TRUE.equals(product.get("active")) || !Boolean.TRUE.equals(product.get("configured")) || purchase && !isPurchasable(product)) throw invalid("商品未启用或尚未配置可购买权益");
        Map<String,Object> snapshot=new LinkedHashMap<>(product);
        snapshot.put("version",1);snapshot.put("productType",recharge?"RECHARGE":"MEMBERSHIP");snapshot.put("planName",product.get("name"));
        return snapshot;
    }
    @Override public String snapshotProduct(boolean recharge,Long productId) { return writeJson(productSnapshot(recharge,productId,true)); }
    private String writeJson(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw invalid("无法保存权益快照"); } }
    private Map<String,Object> readJson(String value) { try { return json.readValue(value,new TypeReference<>(){}); } catch(Exception e) { throw invalid("订单权益快照无效"); } }
    @Override public void grantPaidOrder(Long userId,Long orderId,String snapshot) {
        if(orderId==null || orderId<=0) throw invalid("订单编号无效");
        Map<String,Object> data=readJson(snapshot);
        grantSnapshot(userId,"order:"+orderId,data,"订单支付到账",null,snapshot);
    }
    @Override public void grant(Long userId,EntitlementGrantRequest request,Long operatorId) {
        if(operatorId==null) throw invalid("操作人无效");
        String source="admin:"+request.getRequestId();checkKey(source);lockAccount(userId);
        Map<String,Object> intent=readJson(writeJson(request));
        List<String> previous=jdbc.queryForList("SELECT snapshot FROM t_entitlement_grant WHERE user_id=? AND source_key=? FOR UPDATE",String.class,userId,source);
        if(!previous.isEmpty()) {
            if(!intent.equals(readJson(previous.get(0)).get("adminRequest"))) throw invalid("请求编号已用于另一组权益，不能重复发放");
            return;
        }
        Map<String,Object> data=request.getPlanId()==null?new LinkedHashMap<>():productSnapshot(false,Long.valueOf(request.getPlanId()),false);
        data.put("version",1);data.put("productType",request.getPlanId()==null?"GRANT":"MEMBERSHIP");
        data.putIfAbsent("planName",request.getPremiumChatDailyLimit()!=null?"定向会员权益":"定向充值权益");
        if(request.getDurationDays()!=null) data.put("durationDays",request.getDurationDays());data.putIfAbsent("durationDays",-1);
        if(request.getPremiumChatDailyLimit()!=null) data.put("premiumChatDailyLimit",request.getPremiumChatDailyLimit());data.putIfAbsent("premiumChatDailyLimit",0);
        if(request.getDigitalHumanSeconds()!=null) data.put("digitalHumanSeconds",request.getDigitalHumanSeconds());data.putIfAbsent("digitalHumanSeconds",0);
        if(request.getAiVideoSeconds()!=null) data.put("aiVideoSeconds",request.getAiVideoSeconds());data.putIfAbsent("aiVideoSeconds",0);
        if(request.getPremiumChatCredits()!=null) data.put("premiumChatCredits",request.getPremiumChatCredits());data.putIfAbsent("premiumChatCredits",0);
        data.put("adminRequest",intent);
        grantSnapshot(userId,source,data,request.getReason(),operatorId,writeJson(data));
    }
    private void grantSnapshot(Long userId,String source,Map<String,Object> data,String reason,Long operatorId,String rawSnapshot) {
        checkKey(source);lockAccount(userId);
        List<String> previous=jdbc.queryForList("SELECT snapshot FROM t_entitlement_grant WHERE user_id=? AND source_key=? FOR UPDATE",String.class,userId,source);
        if(!previous.isEmpty()) {
            if(!readJson(previous.get(0)).equals(readJson(rawSnapshot))) throw invalid("请求编号已用于另一组权益，不能重复发放");
            return;
        }
        if(number(data,"version")!=1) throw invalid("订单权益版本无效");
        long days=number(data,"durationDays"),daily=number(data,"premiumChatDailyLimit"),digital=number(data,"digitalHumanSeconds"),video=number(data,"aiVideoSeconds"),credits=number(data,"premiumChatCredits");
        if(days==0 || days< -1 || days>36500 || daily< -1 || daily>1000000 || digital<0 || video<0 || credits<0 || digital>1000000000L || video>1000000000L || credits>1000000000L) throw invalid("权益快照数量无效");
        String type=string(data,"productType"),name=string(data,"planName");
        if(!Set.of("MEMBERSHIP","RECHARGE","GRANT").contains(type) || name==null || name.isBlank() || name.length()>128) throw invalid("权益快照类型无效");
        LocalDateTime starts=now(),expires=days<0?null:now().plusDays(days);
        Long productId=data.get("productId")==null?null:Long.valueOf(data.get("productId").toString());
        // Renewals of the same plan extend the latest subscription's end date;
        // their new consumable quota starts now and expires with that extension.
        if("MEMBERSHIP".equals(type) && productId!=null && days>0) {
            List<LocalDateTime> current=jdbc.query("SELECT expires_at FROM t_entitlement_grant WHERE user_id=? AND product_id=? AND product_type='MEMBERSHIP' AND (expires_at IS NULL OR expires_at>?) ORDER BY expires_at DESC FOR UPDATE",(rs,index)->rs.getObject("expires_at",LocalDateTime.class),userId,productId,now());
            if(current.stream().anyMatch(Objects::isNull)) expires=null;
            else if(!current.isEmpty()) expires=current.get(0).plusDays(days);
        }
        jdbc.update("""
                INSERT INTO t_entitlement_grant(user_id,source_key,product_type,product_id,plan_name,premium_chat_daily_limit,digital_human_seconds,ai_video_seconds,premium_chat_credits,starts_at,expires_at,reason,operator_id,snapshot,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,userId,source,type,productId,name,daily,digital,video,credits,starts,expires,reason,operatorId,rawSnapshot,starts);
        grantBucket(userId,source,DIGITAL_HUMAN_SECONDS,digital,starts,expires);
        grantBucket(userId,source,AI_VIDEO_SECONDS,video,starts,expires);
        grantBucket(userId,source,PREMIUM_CHAT,credits,starts,expires);
    }
    private void grantBucket(Long userId,String source,String resource,long capacity,LocalDateTime start,LocalDateTime expires) {
        if(capacity>0) jdbc.update("INSERT INTO t_entitlement_bucket(user_id,resource,source_key,capacity,starts_at,expires_at) VALUES (?,?,?,?,?,?)",userId,resource,source,capacity,start,expires);
    }
}
