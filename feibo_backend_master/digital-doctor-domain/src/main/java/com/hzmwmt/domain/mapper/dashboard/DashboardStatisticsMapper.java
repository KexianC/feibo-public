package com.hzmwmt.domain.mapper.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardStatisticsMapper {
    String LOGIN_RANGE = "<where><if test='start != null'>login_time &gt;= #{start}</if>"
            + "<if test='end != null'> AND login_time &lt; #{end}</if></where>";

    @Select("<script>SELECT COUNT(*) AS total, COALESCE(SUM(login_status LIKE '%失败%'),0) AS failed "
            + "FROM t_login_log " + LOGIN_RANGE + "</script>")
    Map<String, Object> loginTotals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("<script>SELECT COALESCE(NULLIF(login_user,''),'未知账号') AS name, COUNT(*) AS total, "
            + "SUM(login_status LIKE '%失败%') AS failed, SUM(login_status LIKE '%成功%') AS success "
            + "FROM t_login_log " + LOGIN_RANGE
            + " GROUP BY login_user ORDER BY total DESC, login_user ASC LIMIT 6</script>")
    List<Map<String, Object>> loginRanking(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // One latest flow per copywriting prevents duplicate workflow rows inflating totals.
    @Select("""
            <script>
            SELECT COUNT(*) AS total,
              COALESCE(SUM(CASE WHEN f.current_status=4 THEN 1 ELSE 0 END),0) AS passed,
              COALESCE(SUM(CASE WHEN f.current_status=5 THEN 1 ELSE 0 END),0) AS rejected,
              COALESCE(SUM(CASE WHEN f.current_status IS NULL OR f.current_status NOT IN (4,5) THEN 1 ELSE 0 END),0) AS pending
            FROM t_copywriting c
            LEFT JOIN t_copywriting_approval_flow f ON f.copywriting_id=c.copywriting_id
              AND f.id=(SELECT MAX(f2.id) FROM t_copywriting_approval_flow f2 WHERE f2.copywriting_id=c.copywriting_id)
            <where>
              <if test='start != null'>c.create_time &gt;= #{start}</if>
              <if test='end != null'>AND c.create_time &lt; #{end}</if>
            </where>
            </script>
            """)
    Map<String, Object> auditTotals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("<script>SELECT COUNT(*) AS total FROM t_ai_conversation <where>"
            + "<if test='start != null'>create_time &gt;= #{start}</if>"
            + "<if test='end != null'>AND create_time &lt; #{end}</if></where></script>")
    Map<String, Object> conversationTotals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
