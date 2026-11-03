package com.hzmwmt.infrastructure.adapter.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.hzmwmt.infrastructure.dao.DigitalWorkMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalWork;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the generated mapper SQL against the production digital-work column layout. */
class DigitalWorkProductionSchemaTest {
    private static final long USER_ID = 9007199254740993L;
    private SqlSession session;
    private DigitalWorkMapper mapper;
    private DigitalRepository repository;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:digital_work_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var db = new JdbcTemplate(source);
        // Production stores the display name as title; there is no name column.
        db.execute("""
                CREATE TABLE t_digital_work (
                  id INT AUTO_INCREMENT PRIMARY KEY, digital_work_Id BIGINT UNIQUE, user_id BIGINT,
                  title VARCHAR(100) NOT NULL, body TEXT NOT NULL, external_id VARCHAR(255),
                  create_time TIMESTAMP, update_time TIMESTAMP, url VARCHAR(512), duration INT,
                  bill DECIMAL(10,2), is_preview TINYINT, status TINYINT, failure_reason VARCHAR(1000),
                  quota_biz_key VARCHAR(128), quota_hidden TINYINT NOT NULL DEFAULT 0
                )
                """);
        db.update("""
                INSERT INTO t_digital_work(digital_work_Id,user_id,title,body,external_id,quota_biz_key)
                VALUES(11,?,'existing work','{}','pending_existing','digital:existing')
                """, USER_ID);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("digital-work-test", new JdbcTransactionFactory(), source));
        configuration.addMapper(DigitalWorkMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        mapper = session.getMapper(DigitalWorkMapper.class);
        repository = new DigitalRepository();
        ReflectionTestUtils.setField(repository, "digitalWorkMapper", mapper);
    }

    @AfterEach
    void close() {
        if (session != null) session.close();
    }

    @Test
    void newAndExistingCompositionQuotaRequestsReadProductionSchema() {
        assertNull(repository.findDigitalWorkByQuotaKey(USER_ID, "digital:new"));
        var existing = repository.findDigitalWorkByQuotaKey(USER_ID, "digital:existing");
        assertEquals("pending_existing", existing.getExternalId());
        assertEquals(USER_ID, existing.getUserId());
        assertNull(repository.findDigitalWorkByQuotaKey(USER_ID + 1, "digital:existing"));
    }

    @Test
    void providerNameDoesNotBecomeAColumnDuringWritesOrSubsequentReads() {
        var work = DigitalWork.builder().digitalWorkId(12L).userId(USER_ID).title("persisted title")
                .name("provider display name").body("{}").externalId("pending_new")
                .quotaBizKey("digital:new").build();
        assertEquals(1, mapper.insert(work));
        var stored = mapper.selectById(12L);
        assertEquals("persisted title", stored.getTitle());
        assertEquals("digital:new", stored.getQuotaBizKey());
        assertNull(stored.getName());
    }
}
