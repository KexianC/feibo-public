package com.hzmwmt.ai.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAgentScope {

    /**
     * 1: hospital, 2: department, 3: doctor.
     */
    private Integer agentType;

    private Long hospitalId;

    private String hospitalName;

    private Long departmentId;

    private String departmentName;

    private Long doctorId;

    private String doctorName;
}
