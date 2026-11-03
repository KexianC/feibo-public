package com.hzmwmt.types.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * 自定义反序列化器，用于将Boolean值转换为Byte类型
 * 解决JSON解析时Boolean值无法直接转换为Byte类型的问题
 */
public class ByteBooleanDeserializer extends JsonDeserializer<Byte> {

    @Override
    public Byte deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        // 获取JSON中的值
        Object value = jsonParser.getCurrentValue();

        // 如果是Boolean类型，true转换为0（启用），false转换为1（禁用）
        if (value instanceof Boolean) {
            return ((Boolean) value) ? (byte) 0 : (byte) 1;
        }

        // 如果是String类型，尝试转换
        if (value instanceof String) {
            String stringValue = (String) value;
            if ("true".equalsIgnoreCase(stringValue)) {
                return (byte) 0;
            } else if ("false".equalsIgnoreCase(stringValue)) {
                return (byte) 1;
            }
        }

        // 使用默认的反序列化行为处理其他类型
        return jsonParser.getByteValue();
    }
}