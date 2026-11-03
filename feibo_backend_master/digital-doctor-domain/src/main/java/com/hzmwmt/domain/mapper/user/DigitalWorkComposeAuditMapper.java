package com.hzmwmt.domain.mapper.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.entity.user.TDigitalWorkComposeAudit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Map;

/**
 * Append-only persistence API for digital-work composition audits.
 *
 * <p>No update or delete operation is exposed by design.</p>
 */
@Mapper
public interface DigitalWorkComposeAuditMapper {

    @Insert("""
            INSERT INTO t_digital_work_compose_audit
                (work_record_id, external_id, user_id, compose_params, video_time,
                 request_sha256, capture_source, submitted_at)
            VALUES
                (#{workRecordId}, #{externalId}, #{userId}, #{composeParams}, #{videoTime},
                 #{requestSha256}, #{captureSource}, CURRENT_TIMESTAMP(3))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "auditId", keyColumn = "audit_id")
    int insert(TDigitalWorkComposeAudit audit);

    @Select("""
            <script>
            SELECT
                audit.audit_id AS audit_id,
                CAST(audit.work_record_id AS CHAR) AS work_record_id,
                audit.external_id AS external_id,
                CAST(audit.user_id AS CHAR) AS user_id,
                COALESCE(user.nick_name, '未知用户') AS username,
                COALESCE(user.phone_number, '-') AS phone,
                COALESCE(work.title, CONCAT('已删除作品#', audit.work_record_id)) AS title,
                work.url AS url,
                work.duration AS duration,
                work.bill AS bill,
                work.failure_reason AS failure_reason,
                CASE
                    WHEN work.digital_work_Id IS NULL THEN '已归档'
                    WHEN work.status = 1 THEN '成功'
                    WHEN work.status = 0 THEN '失败'
                    ELSE '处理中'
                END AS status,
                COALESCE(work.create_time, audit.submitted_at) AS create_time,
                audit.compose_params AS compose_params,
                audit.video_time AS video_time,
                audit.request_sha256 AS request_sha256,
                audit.capture_source AS capture_source,
                audit.submitted_at AS submitted_at,
                CASE WHEN work.digital_work_Id IS NULL THEN 1 ELSE 0 END AS archived_only
            FROM t_digital_work_compose_audit audit
            LEFT JOIN t_digital_work work
                ON work.digital_work_Id = audit.work_record_id
            LEFT JOIN t_user user
                ON user.user_id = audit.user_id
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (
                        audit.external_id LIKE CONCAT('%', #{keyword}, '%')
                        OR CAST(audit.work_record_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')
                        OR work.title LIKE CONCAT('%', #{keyword}, '%')
                        OR user.nick_name LIKE CONCAT('%', #{keyword}, '%')
                        OR user.phone_number LIKE CONCAT('%', #{keyword}, '%')
                    )
                </if>
            </where>
            ORDER BY audit.submitted_at DESC, audit.audit_id DESC
            </script>
            """)
    IPage<Map<String, Object>> selectAuditPage(
            Page<Map<String, Object>> page,
            @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT
                COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN work.status = 1 THEN 1 ELSE 0 END), 0) AS success_count,
                COALESCE(SUM(CASE WHEN work.status = 0 THEN 1 ELSE 0 END), 0) AS failed_count,
                COALESCE(SUM(CASE
                    WHEN work.digital_work_Id IS NULL
                      OR work.status IS NULL
                      OR work.status NOT IN (0, 1)
                    THEN 1 ELSE 0 END), 0) AS processing_count,
                COALESCE(SUM(COALESCE(work.duration, 0)), 0) AS total_duration
            FROM t_digital_work_compose_audit audit
            LEFT JOIN t_digital_work work
                ON work.digital_work_Id = audit.work_record_id
            <where>
                <if test="startDate != null">
                    AND COALESCE(work.create_time, audit.submitted_at) &gt;= #{startDate}
                </if>
                <if test="endDate != null">
                    AND COALESCE(work.create_time, audit.submitted_at)
                        &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
                </if>
            </where>
            </script>
            """)
    Map<String, Object> selectAuditStatistics(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
