package com.hzmwmt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author huihui
 */
@SpringBootApplication
@EnableScheduling
@Configurable
@MapperScan({"com.hzmwmt.domain.mapper", "com.hzmwmt.infrastructure.dao"})
@ComponentScan({"com.hzmwmt", "com.hzmwmt.domain", "com.hzmwmt.trigger", "com.hzmwmt.types", "com.hzmwmt.infrastructure"})
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
