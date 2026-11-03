package com.hzmwmt.domain.digital.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * Result of an accepted provider video-composition submission.
 *
 * <p>The request body is the business JSON actually sent to the provider after
 * server-side normalization (for example, after adding {@code callbackUrl}).
 * Provider authentication headers are deliberately not part of this object.</p>
 */
@Builder
@Data
public class VideoCompositionSubmissionEntity {

    private String externalId;

    private String providerRequestBody;

    /** SHA-256 of the exact body sent to the provider before secrets are redacted. */
    private String providerRequestSha256;
}
