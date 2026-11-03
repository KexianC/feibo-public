package com.hzmwmt.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class AdminAiModelApiKeyDTO {

    /** Blank means retain the existing database override or environment fallback. */
    @Size(max = 4096)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String apiKey;
}
