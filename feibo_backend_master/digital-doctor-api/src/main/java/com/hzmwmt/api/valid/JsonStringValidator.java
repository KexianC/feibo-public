package com.hzmwmt.api.valid;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.io.IOException;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName JsonStringValidator
 * @Date: 2025/9/24 22:15
 */
public class JsonStringValidator implements ConstraintValidator<JsonString, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            MAPPER.readTree(value);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
