package com.hzmwmt.security;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.hzmwmt.domain.entity.user.TFeedback;
import com.hzmwmt.domain.mapper.user.AdminFeedbackMapper;
import com.hzmwmt.domain.service.user.impl.AdminFeedbackServiceImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
/** Executes the production MyBatis mapper and service against isolated H2 tables. */
class AdminFeedbackProcessingTest {
    private JdbcTemplate db; private SqlSession session; private AdminFeedbackServiceImpl service;
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:feedback_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(source);
        db.execute("CREATE TABLE t_feedback(id BIGINT PRIMARY KEY,feedback_id BIGINT,user_id BIGINT,type BIGINT,title VARCHAR(200),create_time TIMESTAMP,status INT DEFAULT 0,priority INT DEFAULT 1,reply VARCHAR(5000),version BIGINT DEFAULT 0,handler_user_id BIGINT,update_time TIMESTAMP)");
        db.update("INSERT INTO t_feedback(id,user_id,type,title,create_time) VALUES(1,9007199254740993,2,'login issue','2026-09-09 23:59:59')");
        db.update("INSERT INTO t_feedback(id,user_id,type,title,create_time) VALUES(2,9,1,'feature request','2026-09-10 00:00:00')");
        var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("feedback-test",new JdbcTransactionFactory(),source));
        var pagination=new MybatisPlusInterceptor(); pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2)); configuration.addInterceptor(pagination);
        configuration.addMapper(AdminFeedbackMapper.class);
        session=new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        service=new AdminFeedbackServiceImpl();ReflectionTestUtils.setField(service,"baseMapper",session.getMapper(AdminFeedbackMapper.class));
    }
    @AfterEach void close(){if(session!=null)session.close();}
    @Test void replyResolveAndClosePersistAcrossReads() {
        TFeedback reply=service.process(1L,0,1,"  recorded response  ",2,7L);
        assertEquals("recorded response",reply.getReply()); assertEquals(1L,reply.getVersion());assertEquals(7L,reply.getHandlerUserId());assertEquals(2,reply.getPriority());
        assertEquals(2,service.process(1L,1,2,null,null,8L).getStatus());
        assertEquals(3,service.process(1L,2,3,null,null,8L).getStatus());
        assertEquals("recorded response",db.queryForObject("SELECT reply FROM t_feedback WHERE id=1",String.class));
        assertEquals(3L,db.queryForObject("SELECT version FROM t_feedback WHERE id=1",Long.class));
    }
    @Test void staleReplyCannotOverwriteAnotherAdministratorsResolution() {
        service.process(1L,0,2,null,null,7L);
        ResponseStatusException failure=assertThrows(ResponseStatusException.class,()->service.process(1L,0,1,"stale",3,8L));
        assertEquals(409,failure.getStatusCode().value());
        assertEquals(2,db.queryForObject("SELECT status FROM t_feedback WHERE id=1",Integer.class));
        assertNull(db.queryForObject("SELECT reply FROM t_feedback WHERE id=1",String.class));
    }
    @Test void closedAndBlankReplyAreRejectedWithoutChangingVersion() {
        assertThrows(ResponseStatusException.class,()->service.process(1L,0,1," ",1,7L));
        service.process(1L,0,3,null,null,7L);
        assertThrows(ResponseStatusException.class,()->service.process(1L,1,1,"late",1,7L));
        assertEquals(1L,db.queryForObject("SELECT version FROM t_feedback WHERE id=1",Long.class));
    }
    @Test void filtersUseFullUserIdStatusPriorityAndInclusiveEndDay() {
        service.process(1L,0,1,"response",2,7L);
        var page=service.pageQuery(1,10,9007199254740993L,2L,"login",1,2,LocalDate.parse("2026-09-09"),LocalDate.parse("2026-09-09"));
        assertEquals(1,page.getTotal());assertEquals(1L,page.getRecords().get(0).getId());
        assertEquals(0,service.pageQuery(1,10,null,null,null,0,2,null,null).getTotal());
        assertEquals(1,service.pageQuery(1,10,null,null,null,null,null,null,LocalDate.parse("2026-09-09")).getTotal());
    }
    @Test void invalidDateRangeAndMissingRecordFailClearly() {
        assertThrows(ResponseStatusException.class,()->service.pageQuery(1,10,null,null,null,null,null,LocalDate.parse("2026-09-10"),LocalDate.parse("2026-09-09")));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.process(99L,0,2,null,null,7L)).getStatusCode().value());
    }
}
