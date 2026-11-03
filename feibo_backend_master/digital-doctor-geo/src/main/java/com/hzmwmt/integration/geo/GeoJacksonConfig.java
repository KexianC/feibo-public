package com.hzmwmt.integration.geo;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
@ConditionalOnProperty(prefix = "app.geo-integration", name = "enabled", havingValue = "true")
public class GeoJacksonConfig {

    private static final Set<String> GEO_MODEL_PACKAGES = Set.of(
            "com.hzmwmt.model",
            "com.hzmwmt.geo",
            "com.hzmwmt.a_publish",
            "com.hzmwmt.ai.rag"
    );

    @Bean
    public Module geoLongToStringModule() {
        SimpleModule module = new SimpleModule("geo-long-to-string");
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(
                    SerializationConfig config,
                    BeanDescription beanDescription,
                    List<BeanPropertyWriter> beanProperties) {
                if (!isGeoModel(beanDescription.getBeanClass())) {
                    return beanProperties;
                }
                for (BeanPropertyWriter property : beanProperties) {
                    Class<?> rawType = property.getType().getRawClass();
                    if (rawType == Long.class || rawType == Long.TYPE) {
                        @SuppressWarnings("unchecked")
                        JsonSerializer<Object> serializer = (JsonSerializer<Object>) (JsonSerializer<?>) ToStringSerializer.instance;
                        property.assignSerializer(serializer);
                    }
                }
                return beanProperties;
            }
        });
        return module;
    }

    private boolean isGeoModel(Class<?> type) {
        String packageName = type.getPackageName();
        return GEO_MODEL_PACKAGES.stream().anyMatch(packageName::startsWith);
    }
}
