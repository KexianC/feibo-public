package com.hzmwmt.security;

import com.hzmwmt.domain.mapper.dashboard.DashboardStatisticsMapper;
import com.hzmwmt.domain.mapper.user.AdminDigitalWorkMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class DashboardAggregationTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 9, 0, 0);
    private static final LocalDateTime END = START.plusDays(1);

    private SqlSession session() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:dashboard_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.addMapper(DashboardStatisticsMapper.class);
        config.addMapper(AdminDigitalWorkMapper.class);
        SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("CREATE TABLE t_login_log(login_user VARCHAR(64), login_status VARCHAR(64), login_time TIMESTAMP)");
            sql.execute("CREATE TABLE t_copywriting(id BIGINT, copywriting_id BIGINT, create_time TIMESTAMP)");
            sql.execute("CREATE TABLE t_copywriting_approval_flow(id BIGINT, copywriting_id BIGINT,current_status INT)");
            sql.execute("CREATE TABLE t_ai_conversation(id BIGINT,create_time TIMESTAMP)");
            sql.execute("CREATE TABLE t_user(user_id BIGINT,nick_name VARCHAR(64))");
            sql.execute("CREATE TABLE t_digital_work(user_id BIGINT,duration INT,create_time TIMESTAMP)");
        }
        return session;
    }

    @Test void loginRankingAggregatesEveryMatchingRowBeforeLimitingAndExcludesNextDay() throws Exception {
        try (SqlSession session = session(); Statement sql = session.getConnection().createStatement()) {
            for (int i = 0; i < 80; i++) sql.execute("INSERT INTO t_login_log VALUES ('frequent','登录成功','2026-09-09 01:00:00')");
            for (int i = 0; i < 60; i++) sql.execute("INSERT INTO t_login_log VALUES ('recent','登录失败','2026-09-09 23:00:00')");
            sql.execute("INSERT INTO t_login_log VALUES ('outside','登录失败','2026-09-10 00:00:00')");
            var mapper = session.getMapper(DashboardStatisticsMapper.class);
            assertThat(number(mapper.loginTotals(START, END), "total")).isEqualTo(140);
            assertThat(number(mapper.loginTotals(START, END), "failed")).isEqualTo(60);
            var ranking = mapper.loginRanking(START, END);
            assertThat(ranking).hasSize(2);
            assertThat(ranking.get(0).get("name")).isEqualTo("frequent");
            assertThat(number(ranking.get(0), "total")).isEqualTo(80);
        }
    }

    @Test void approvalTotalsUseFinalWorkflowCodesAndDoNotDoubleCountMultipleFlows() throws Exception {
        try (SqlSession session = session(); Statement sql = session.getConnection().createStatement()) {
            for (int i = 1; i <= 80; i++) {
                sql.execute("INSERT INTO t_copywriting VALUES (" + i + "," + i + ",'2026-09-09 12:00:00')");
                if (i <= 60) sql.execute("INSERT INTO t_copywriting_approval_flow VALUES (" + i + "," + i + "," + (i <= 40 ? 4 : 5) + ")");
            }
            sql.execute("INSERT INTO t_copywriting_approval_flow VALUES (0,1,5)");
            sql.execute("INSERT INTO t_copywriting VALUES (90,90,'2026-09-08 23:59:59')");
            var result = session.getMapper(DashboardStatisticsMapper.class).auditTotals(START, END);
            assertThat(number(result, "total")).isEqualTo(80);
            assertThat(number(result, "passed")).isEqualTo(40);
            assertThat(number(result, "rejected")).isEqualTo(20);
            assertThat(number(result, "pending")).isEqualTo(20);
        }
    }

    @Test void selectedDatesApplyToConversationCountAndVideoRanking() throws Exception {
        try (SqlSession session = session(); Statement sql = session.getConnection().createStatement()) {
            sql.execute("INSERT INTO t_ai_conversation VALUES (1,'2026-09-09 00:00:00'),(2,'2026-09-10 00:00:00')");
            sql.execute("INSERT INTO t_user VALUES (1,'A'),(2,'B')");
            sql.execute("INSERT INTO t_digital_work VALUES (1,2000,'2026-09-08 12:00:00'),(1,10,'2026-09-09 12:00:00'),(2,20,'2026-09-09 12:00:00'),(2,30,'2026-09-09 18:00:00')");
            assertThat(number(session.getMapper(DashboardStatisticsMapper.class).conversationTotals(START, END), "total")).isEqualTo(1);
            var ranking = session.getMapper(AdminDigitalWorkMapper.class).getTopUser(6, START, END);
            assertThat((Object) ranking.get(0).get("nick_name")).isEqualTo("B");
            assertThat(((Number) (Object) ranking.get(0).get("total_duration")).longValue()).isEqualTo(50);
        }
    }

    private long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
}
