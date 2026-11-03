package com.hzmwmt.security;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hzmwmt.domain.mapper.packages.TAiRechargePackMapper;
import com.hzmwmt.domain.service.packages.impl.AdminAiRechargePackServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Execute the actual mapper and pagination SQL against an isolated in-memory database. */
class AdminRechargePackFilterTest {
    private SqlSession session;
    private AdminAiRechargePackServiceImpl service;

    @BeforeEach void setup() throws Exception {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:pack_" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(datasource);
        jdbc.execute("""
                CREATE TABLE t_ai_recharge_pack (
                  id INT PRIMARY KEY, ai_recharge_pack_id BIGINT, pack_code VARCHAR(64), name VARCHAR(64),
                  price DECIMAL(12,2), ai_feature_template_id BIGINT, value DECIMAL(12,2), description VARCHAR(500),
                  is_active TINYINT, create_time TIMESTAMP, update_time TIMESTAMP
                )
                """);
        jdbc.update("INSERT INTO t_ai_recharge_pack(id,name,price,value,is_active,create_time) VALUES(1,'免费视频',0,6000,0,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO t_ai_recharge_pack(id,name,price,value,is_active,create_time) VALUES(2,'付费视频',10,6000,0,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO t_ai_recharge_pack(id,name,price,value,is_active,create_time) VALUES(3,'停用视频',10,6000,1,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO t_ai_recharge_pack(id,name,price,value,is_active,create_time) VALUES(4,'对话包',20,50,0,CURRENT_TIMESTAMP)");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(TAiRechargePackMapper.class);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource); factory.setConfiguration(configuration); factory.setPlugins(plugin);
        session = factory.getObject().openSession();
        service = new AdminAiRechargePackServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(TAiRechargePackMapper.class));
    }

    @AfterEach void close() { if (session != null) session.close(); }

    @Test void filtersIncludeZeroPriceAndDoNotChangePurchasedAmounts() {
        var result = service.pageWithCondition(1, 10, " 视频 ", 0, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().get(0).getId());
        assertEquals(0, new BigDecimal("6000").compareTo(result.getRecords().get(0).getValue()));
    }

    @Test void statusAndBothPriceBoundsAreAppliedBeforePagination() {
        var result = service.pageWithCondition(1, 1, "视频", 1, BigDecimal.TEN, BigDecimal.TEN);
        assertEquals(1, result.getTotal());
        assertEquals(3, result.getRecords().get(0).getId());
        assertTrue(service.pageWithCondition(2, 1, "视频", 1, BigDecimal.TEN, BigDecimal.TEN).getRecords().isEmpty());
    }

    @Test void omittedFiltersRestoreAllProducts() {
        var result = service.pageWithCondition(1, 10, null);
        assertEquals(4, result.getTotal());
        assertEquals(4, result.getRecords().size());
    }
}
