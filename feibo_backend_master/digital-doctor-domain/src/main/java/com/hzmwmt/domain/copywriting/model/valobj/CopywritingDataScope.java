package com.hzmwmt.domain.copywriting.model.valobj;

/**
 * Server-derived visibility for the copywriting workflow.  The actor identity
 * always comes from the authenticated principal; request DTOs must never build
 * this value.
 */
public record CopywritingDataScope(
        Long actorUserId,
        boolean allAccess,
        boolean expertReviewer,
        boolean organizationReviewer) {

    public CopywritingDataScope {
        if (actorUserId == null || actorUserId <= 0) {
            throw new IllegalArgumentException("A positive authenticated actor is required");
        }
    }

    public boolean canReviewExpertStage() {
        return allAccess || expertReviewer;
    }

    public boolean canReviewOrganizationStage() {
        return allAccess || organizationReviewer;
    }
}
