package com.hzmwmt.domain.entity.user;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Append-only audit snapshot for a video-composition submission.
 *
 * <p>This entity intentionally has no MyBatis-Plus {@code BaseMapper}; the
 * corresponding mapper exposes only insert and read operations.</p>
 */
@Builder
@Data
public class TDigitalWorkComposeAudit {

    private Long auditId;

    /** Unique t_digital_work.digital_work_Id value. */
    private Long workRecordId;

    private String externalId;

    private Long userId;

    /** Business JSON only; never contains provider authentication headers. */
    private String composeParams;

    /** Client quota estimate in minutes. Historical backfill keeps this null. */
    private Integer videoTime;

    private String requestSha256;

    private String captureSource;

    private LocalDateTime submittedAt;
}
