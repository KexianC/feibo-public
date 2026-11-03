package com.hzmwmt.integration.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.model.entity.DoctorInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoJacksonConfigTest {

    @Test
    void serializesGeoLongIdsAsStringsWithoutChangingHostDtos() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new GeoJacksonConfig().geoLongToStringModule());

        DoctorInfo doctor = new DoctorInfo();
        doctor.setId(9_007_199_254_740_993L);
        JsonNode geoJson = objectMapper.readTree(objectMapper.writeValueAsString(doctor));
        JsonNode hostJson = objectMapper.readTree(objectMapper.writeValueAsString(
                new HostDto(9_007_199_254_740_993L)
        ));

        assertTrue(geoJson.get("id").isTextual());
        assertEquals("9007199254740993", geoJson.get("id").asText());
        assertTrue(hostJson.get("id").isNumber());
    }

    static final class HostDto {
        private final Long id;

        HostDto(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
    }
}
