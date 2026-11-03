package com.hzmwmt.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCatalogItemDTO {
    /** Stable business identifier used by role-permission mappings. */
    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean reserved;
}
