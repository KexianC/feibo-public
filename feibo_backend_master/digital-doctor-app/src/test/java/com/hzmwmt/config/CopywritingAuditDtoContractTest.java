package com.hzmwmt.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.api.dto.CopywritingAuditRecordResponseDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopywritingAuditDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void allLongIdentifiersAreSerializedAsStrings() throws Exception {
        long largeId = 1968256800130580481L;
        CopywritingAuditRecordResponseDTO history = CopywritingAuditRecordResponseDTO.builder()
                .id(largeId)
                .copywritingId(largeId)
                .copywritingApprovalFlowId(largeId)
                .operatorId(largeId)
                .build();
        JsonNode historyJson = objectMapper.readTree(objectMapper.writeValueAsString(history));
        assertTrue(historyJson.get("id").isTextual());
        assertTrue(historyJson.get("copywritingId").isTextual());
        assertTrue(historyJson.get("copywritingApprovalFlowId").isTextual());
        assertTrue(historyJson.get("operatorId").isTextual());
        assertEquals(Long.toString(largeId), historyJson.get("id").textValue());
    }
}
