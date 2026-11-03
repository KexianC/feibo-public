package com.hzmwmt.videogeneration;

import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.hzmwmt.types.annotations.OperationLog;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class VideoQuotaReconciliationService {
    public record ResolveFailure(@NotBlank @Size(max=500) String reason,
                                 @AssertTrue boolean upstreamConfirmedFailed) {}
    private final JdbcTemplate db;
    private final IEntitlementService quotas;
    public VideoQuotaReconciliationService(JdbcTemplate db, IEntitlementService quotas) { this.db=db; this.quotas=quotas; }

    public List<Map<String,Object>> digitalPending() {
        // Legacy works and entitlement reservations can use different MySQL collations.
        // Business keys are opaque identifiers, so compare their exact bytes.
        return db.queryForList("""
                SELECT CAST(w.digital_work_id AS CHAR) AS work_record_id,w.external_id,
                       CAST(w.user_id AS CHAR) AS user_id,w.title,w.create_time AS created_at,'处理中' AS status
                FROM t_digital_work w JOIN t_entitlement_reservation r
                  ON r.user_id=w.user_id AND CAST(r.biz_key AS BINARY)=CAST(w.quota_biz_key AS BINARY)
                WHERE w.status IS NULL AND r.status='RESERVED'
                ORDER BY w.create_time ASC LIMIT 100
                """);
    }

    @Transactional(rollbackFor=Exception.class)
    @OperationLog(operationType="核销",description="确认 AI 视频供应商失败并退还预占额度",recordParams=false,recordResult=false,critical=true)
    public void resolveAi(String id, ResolveFailure request, Long operatorId) {
        validate(request);
        List<Map<String,Object>> rows = db.queryForList("SELECT * FROM ai_video_task WHERE id=? FOR UPDATE",id);
        if (rows.isEmpty()) throw new IllegalArgumentException("任务不存在");
        Map<String,Object> task=rows.get(0);
        if ("failed".equals(task.get("status"))) return;
        if (!List.of("unknown","submitting").contains(String.valueOf(task.get("status"))))
            throw new IllegalArgumentException("仅提交状态不确定的任务允许人工核销");
        Long owner=Long.valueOf(String.valueOf(task.get("owner_id")));
        String key=requiredReservation(owner,task.get("quota_biz_key"));
        quotas.release(owner,key);
        db.update("UPDATE ai_video_task SET status='failed',video_url=NULL,error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                "管理员确认供应商失败，额度已退还",id);
        audit("AI_VIDEO",id,owner,key,operatorId,request.reason().trim());
    }

    @Transactional(rollbackFor=Exception.class)
    @OperationLog(operationType="核销",description="确认数字人供应商失败并退还预占额度",recordParams=false,recordResult=false,critical=true,targetIdParameterIndex=0)
    public void resolveDigital(Long workId, ResolveFailure request, Long operatorId) {
        validate(request);
        List<Map<String,Object>> rows = db.queryForList("SELECT * FROM t_digital_work WHERE digital_work_id=? FOR UPDATE",workId);
        if (rows.isEmpty()) throw new IllegalArgumentException("数字人作品不存在");
        Map<String,Object> task=rows.get(0);
        if (task.get("status") != null) {
            if (Boolean.FALSE.equals(task.get("status"))
                    || task.get("status") instanceof Number number && number.intValue()==0) return;
            throw new IllegalArgumentException("已完成的作品不能人工核销");
        }
        Long owner=((Number)task.get("user_id")).longValue();
        String key=requiredReservation(owner,task.get("quota_biz_key"));
        quotas.release(owner,key);
        db.update("UPDATE t_digital_work SET status=0,url=NULL,failure_reason=?,update_time=CURRENT_TIMESTAMP WHERE digital_work_id=?",
                "管理员确认供应商失败，额度已退还",workId);
        audit("DIGITAL_HUMAN",workId.toString(),owner,key,operatorId,request.reason().trim());
    }

    private String requiredReservation(Long owner,Object rawKey) {
        if (rawKey==null) throw new IllegalArgumentException("该历史任务没有可退还的会员预占额度");
        db.queryForObject("SELECT user_id FROM t_entitlement_account WHERE user_id=? FOR UPDATE",Long.class,owner);
        String key=rawKey.toString();
        List<String> states=db.queryForList("SELECT status FROM t_entitlement_reservation WHERE user_id=? AND biz_key=?",String.class,owner,key);
        if (states.isEmpty() || !"RESERVED".equals(states.get(0)))
            throw new IllegalArgumentException("额度已结算或已退还，请刷新任务状态；不能重复核销");
        return key;
    }
    private void audit(String kind,String task,Long owner,String key,Long operator,String reason) {
        db.update("INSERT INTO t_video_quota_reconciliation(kind,task_ref,user_id,biz_key,operator_id,reason) VALUES (?,?,?,?,?,?)",
                kind,task,owner,key,operator,reason);
    }
    private static void validate(ResolveFailure request) {
        if (request==null || !request.upstreamConfirmedFailed() || request.reason()==null
                || request.reason().isBlank() || request.reason().length()>500)
            throw new IllegalArgumentException("必须确认供应商任务已失败并填写核销原因（最多500字）");
    }
}
