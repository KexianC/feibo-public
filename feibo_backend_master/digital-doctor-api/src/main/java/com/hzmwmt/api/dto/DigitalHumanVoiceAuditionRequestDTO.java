package com.hzmwmt.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Safe, user-controlled inputs for a digital-human voice audition.
 * Provider authentication fields are deliberately not part of this contract.
 */
@Data
public class DigitalHumanVoiceAuditionRequestDTO {

    @NotBlank(message = "Audition text must not be blank")
    @Size(max = 500, message = "Audition text must not exceed 500 characters")
    private String text;

    @NotBlank(message = "Speaker must not be blank")
    @Size(max = 128, message = "Speaker must not exceed 128 characters")
    // Cloned voices carry model segments separated by the provider's literal "||||".
    @Pattern(regexp = "^[A-Za-z0-9._-]+(?:[|]{4}[A-Za-z0-9._-]+)*$", message = "Speaker format is invalid")
    private String speaker;

    /** Normalize before validation; legacy model records can repeat the clone engine prefix. */
    public void setSpeaker(String speaker) {
        this.speaker = speaker == null ? null : speaker.trim().replaceFirst(
                "^(galaxy_fastv[0-9]+)(?:[|]{4}\\1)+[|]{4}([A-Fa-f0-9]{32})$",
                "$1||||$2");
    }

    @NotBlank(message = "Audio type must not be blank")
    @Pattern(regexp = "^(?i:mp3|wav)$", message = "Audio type must be mp3 or wav")
    private String audioType = "mp3";

    @NotNull(message = "Speed must not be null")
    @DecimalMin(value = "0.5", message = "Speed must be at least 0.5")
    @DecimalMax(value = "2.0", message = "Speed must be at most 2.0")
    private Double speed = 1.0;

    @NotNull(message = "Volume must not be null")
    @DecimalMin(value = "0.1", message = "Volume must be at least 0.1")
    @DecimalMax(value = "1.0", message = "Volume must be at most 1.0")
    private Double volume = 1.0;

    @NotNull(message = "Pitch must not be null")
    @DecimalMin(value = "-10", inclusive = false, message = "Pitch must be greater than -10")
    @DecimalMax(value = "10", inclusive = false, message = "Pitch must be less than 10")
    private Double pitch = 0.0;

    @JsonIgnore
    private boolean unsupportedFields;

    /** Reject rather than silently accept authentication or other unknown fields. */
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        this.unsupportedFields = true;
    }
}
