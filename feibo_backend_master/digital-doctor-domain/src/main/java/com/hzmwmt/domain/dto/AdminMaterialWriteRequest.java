package com.hzmwmt.domain.dto;

import jakarta.validation.constraints.*;

/** Only fields editable in the material console; ownership/resource IDs stay server controlled. */
public final class AdminMaterialWriteRequest {
    private AdminMaterialWriteRequest() {}

    public record NameUpdate(@NotNull @Positive Long id, @NotBlank @Size(max = 200) String name) {}
    public record VideoUpdate(@NotNull @Positive Long id, @NotBlank @Size(max = 200) String title) {}
    public record ModelUpdate(@NotBlank String id, @NotBlank @Size(max = 200) String modelName,
                              @PositiveOrZero Integer sortNum, @Size(max = 60) String status) {}
    public record VoiceCreate(@NotBlank @Size(max = 200) String name, @NotNull @Positive Long digitalVoiceId,
                              @Size(max = 100) String styleName, @Min(0) @Max(2) Integer gender,
                              @NotNull Boolean isDisplay) {}
    public record VoiceUpdate(@NotNull @Positive Integer id, @NotBlank @Size(max = 200) String name,
                              @NotNull @Positive Long digitalVoiceId, @NotNull @Size(max = 100) String styleName,
                              @NotNull @Min(0) @Max(2) Integer gender, @NotNull Boolean isDisplay) {}
    public record CaseVideoUpdate(@NotNull @Positive Long id, @NotBlank @Size(max = 200) String title,
                                  @Size(max = 200) String scene, @Size(max = 200) String industry,
                                  @PositiveOrZero Integer sort, @Min(0) @Max(1) Integer isShow) {}
}
