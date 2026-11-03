package com.hzmwmt.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName MybatisPlusConfig
 * @Date: 2025/8/4 0:17
 */
@Configuration
@Slf4j
// The transaction advisor must wrap the critical operation-log advisor
// (OperationLogAspect has order 10), so audit inserts participate in the same
// transaction as privileged mutations.
@EnableTransactionManagement(order = 0)
@Primary
public class MybatisPlusConfig implements MetaObjectHandler {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("start insert fill ....");
        fillDateTimeField("createTime", metaObject, true);
        fillDateTimeField("createdTime", metaObject, true);
        fillDateTimeField("updateTime", metaObject, true);
        fillDateTimeField("updatedTime", metaObject, true);
        this.setFieldValByName("isDelete", false, metaObject);

        // 为AI充值包生成唯一业务ID
        if (metaObject.hasSetter("aiRechargePackId") && metaObject.getValue("aiRechargePackId") == null) {
            this.setFieldValByName("aiRechargePackId", generateBusinessId(), metaObject);
        }

        // 为pack_code字段添加填充逻辑
        if (metaObject.hasSetter("packCode") && metaObject.getValue("packCode") == null) {
            this.setFieldValByName("packCode", generatePackCode(), metaObject);
        }
    }

    /**
     * 生成业务ID
     *
     * @return 唯一的业务ID
     */
    private Long generateBusinessId() {
        return IdWorker.getId();
    }

    private String generatePackCode() {
        return "PACK_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase(Locale.ROOT);
    }


    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("start update fill ....");
        fillDateTimeField("updateTime", metaObject, false);
        fillDateTimeField("updatedTime", metaObject, false);
    }

    /**
     * 根据字段类型填充正确的时间值
     */
    private void fillDateTimeField(String fieldName, MetaObject metaObject, boolean insert) {
        try {
            // 检查字段是否存在且可写入
            if (metaObject.hasSetter(fieldName)) {
                Class<?> fieldType = metaObject.getSetterType(fieldName);
                if (fieldType.equals(Date.class)) {
                    // 对于Date类型字段，使用Date
                    this.setFieldValByName(fieldName, new Date(), metaObject);
                } else if (fieldType.equals(LocalDateTime.class)) {
                    // 对于LocalDateTime类型字段，使用LocalDateTime
                    this.setFieldValByName(fieldName, LocalDateTime.now(), metaObject);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fill date time field: field={}, errorType={}",
                    fieldName, e.getClass().getSimpleName());
        }
    }

}
