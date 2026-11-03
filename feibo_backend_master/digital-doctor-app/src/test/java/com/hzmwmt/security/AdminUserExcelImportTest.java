package com.hzmwmt.security;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.function.AdminDigitalModelMapper;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.function.DigitalModelService;
import com.hzmwmt.domain.service.function.impl.AdminDigitalModelServiceImpl;
import com.hzmwmt.domain.service.user.impl.AdminUserServiceImpl;
import com.hzmwmt.domain.user.model.entity.UserExcelModel;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real XLSX parsing, production model query and import method, with isolated transactional storage. */
class AdminUserExcelImportTest {
    private JdbcTemplate db;
    private SqlSession modelSession;
    private ImportStorage target;
    private AdminUserServiceImpl service;
    private DigitalModelService bindings;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:import_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(source);
        db.execute("CREATE TABLE t_admin_digital_model(id VARCHAR(30) PRIMARY KEY,model_name VARCHAR(100),is_deleted INT DEFAULT 0)");
        db.execute("CREATE TABLE imported_users(user_id BIGINT PRIMARY KEY,nickname VARCHAR(100),phone_number VARCHAR(30) UNIQUE)");
        db.execute("CREATE TABLE imported_bindings(user_id BIGINT,model_id BIGINT)");
        db.update("INSERT INTO t_admin_digital_model(id,model_name) VALUES('101','模型甲'),('102','模型乙'),('201','重名模型'),('202','重名模型')");
        db.update("INSERT INTO t_admin_digital_model(id,model_name,is_deleted) VALUES('301','已删除模型',1)");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("import-model-test", new JdbcTransactionFactory(), source));
        configuration.addMapper(AdminDigitalModelMapper.class);
        modelSession = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        var models = new AdminDigitalModelServiceImpl();
        ReflectionTestUtils.setField(models, "baseMapper", modelSession.getMapper(AdminDigitalModelMapper.class));
        bindings = mock(DigitalModelService.class);
        when(bindings.updateUserModelBindings(anyLong(), anyList())).thenAnswer(call -> {
            Long userId = call.getArgument(0);
            List<Long> ids = call.getArgument(1);
            for (Long id : ids) db.update("INSERT INTO imported_bindings(user_id,model_id) VALUES(?,?)", userId, id);
            return true;
        });
        target = new ImportStorage(db);
        ReflectionTestUtils.setField(target, "adminDigitalModelService", models);
        ReflectionTestUtils.setField(target, "digitalModelService", bindings);
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        service = (AdminUserServiceImpl) factory.getProxy();
    }

    @AfterEach void close() { if (modelSession != null) modelSession.close(); }

    @Test void emptyModelCellCreatesUserWithoutBindings() throws Exception {
        assertTrue(service.createUserByExcel(excel(List.of(row("13800000001", null)))).isEmpty());
        assertEquals(1, count("imported_users"));
        assertEquals(0, count("imported_bindings"));
        verify(bindings).updateUserModelBindings(anyLong(), eq(List.of()));
    }

    @Test void englishAndChineseCommasTrimAndDeduplicateNames() throws Exception {
        var errors = service.createUserByExcel(excel(List.of(row("13800000001", " 模型甲, 模型乙，模型甲 , 模型乙 "))));
        assertTrue(errors.isEmpty());
        assertEquals(List.of(101L, 102L), db.queryForList("SELECT model_id FROM imported_bindings ORDER BY model_id", Long.class));
    }

    @Test void unknownAndAmbiguousRowsAreSkippedWhileValidRowsPersist() throws Exception {
        var errors = service.createUserByExcel(excel(List.of(
                row("13800000001", "模型甲"), row("13800000002", "不存在"),
                row("13800000003", "重名模型"), row("13800000002", null))));
        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("3行模型不存在：不存在"));
        assertTrue(errors.get(1).contains("4行模型名称重名"));
        assertEquals(List.of("13800000001", "13800000002"), db.queryForList("SELECT phone_number FROM imported_users ORDER BY phone_number", String.class));
        assertEquals(1, count("imported_bindings"));
    }

    @Test void allRejectedRowsReturnErrorsWithoutCreatingUsers() throws Exception {
        var errors = service.createUserByExcel(excel(List.of(row("13800000001", "已删除模型"), row("13800000002", "重名模型"))));
        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("模型不存在"));
        assertEquals(0, count("imported_users"));
        verifyNoInteractions(bindings);
    }

    @Test void failedSaveRollsBackUsersAndThrowsInsteadOfReportingSuccess() throws Exception {
        target.rejectSave = true;
        var file = excel(List.of(row("13800000001", "模型甲")));
        assertThrows(RuntimeException.class, () -> service.createUserByExcel(file));
        assertEquals(0, count("imported_users"));
        assertEquals(0, count("imported_bindings"));
        verifyNoInteractions(bindings);
    }

    @Test void failedBindingRollsBackAlreadySavedUsers() throws Exception {
        when(bindings.updateUserModelBindings(anyLong(), anyList())).thenThrow(new IllegalStateException("binding write failed"));
        var file = excel(List.of(row("13800000001", "模型甲"), row("13800000002", null)));
        assertThrows(RuntimeException.class, () -> service.createUserByExcel(file));
        assertEquals(0, count("imported_users"));
        assertEquals(0, count("imported_bindings"));
    }

    private int count(String table) { return db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private static UserExcelModel row(String phone, String models) {
        var value = new UserExcelModel();
        value.setNickName("测试用户"); value.setPhoneNumber(phone); value.setModels(models);
        return value;
    }
    private static MockMultipartFile excel(List<UserExcelModel> rows) {
        var output = new ByteArrayOutputStream();
        EasyExcel.write(output, UserExcelModel.class).sheet("用户导入").doWrite(rows);
        return new MockMultipartFile("file", "users.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
    }

    // Keep the production import method intact; only user storage is adapted to the small test schema.
    public static class ImportStorage extends AdminUserServiceImpl {
        private final JdbcTemplate db;
        boolean rejectSave;
        ImportStorage(JdbcTemplate db) { super(mock(AdminUserMapper.class)); this.db = db; }
        @Override public List<TUser> list(Wrapper<TUser> ignored) {
            return db.query("SELECT phone_number FROM imported_users", (rs, row) -> {
                var user = new TUser(); user.setPhoneNumber(rs.getString(1)); return user;
            });
        }
        @Override public boolean saveBatch(Collection<TUser> users) {
            for (TUser user : users) db.update("INSERT INTO imported_users(user_id,nickname,phone_number) VALUES(?,?,?)", user.getUserId(), user.getNickName(), user.getPhoneNumber());
            return !rejectSave;
        }
    }
}
