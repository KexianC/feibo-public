package com.hzmwmt.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.entity.user.TUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TUserSerializationSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passwordHashIsNeverSerializedButLegitimateProfileFieldsRemainVisible() throws Exception {
        TUser user = new TUser();
        user.setId(1L);
        user.setUserId(100L);
        user.setPhoneNumber("13800138000");
        user.setPassword("$2a$10$sensitive-password-hash");

        String json = objectMapper.writeValueAsString(user);

        assertThat(json)
                .contains("\"id\":1")
                .contains("\"userId\":100")
                .contains("\"phoneNumber\":\"13800138000\"")
                .doesNotContain("password")
                .doesNotContain("sensitive-password-hash");
    }
}
