package com.hzmwmt.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GEO standalone MyBatis-Plus defaults. The unified Feibo runtime supplies its
 * own interceptor, so this configuration backs off when one already exists.
 */
@Configuration
@ConditionalOnMissingBean(MybatisPlusInterceptor.class)
public class GeoMybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor geoMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
