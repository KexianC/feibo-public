package com.hzmwmt.geo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceAuditResult {

    private boolean passed;

    private String level;

    @Builder.Default
    private List<Violation> violations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Violation {

        private String ruleType;

        private String severity;

        private String pattern;

        private String description;

        private String evidence;
    }
}
