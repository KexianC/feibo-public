package com.geoagent.standalone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

@SpringBootApplication(scanBasePackages = "com.hzmwmt")
@EnableScheduling
public class GeoAgentApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(GeoAgentApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.config.import", "classpath:geo-agent-standalone.yml"
        ));
        application.run(args);
    }
}
