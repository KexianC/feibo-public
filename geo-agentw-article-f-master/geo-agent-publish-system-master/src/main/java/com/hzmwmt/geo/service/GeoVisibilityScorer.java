package com.hzmwmt.geo.service;

import com.hzmwmt.geo.model.dto.GeoEvidence;
import org.springframework.stereotype.Service;

@Service
public class GeoVisibilityScorer {

    public int score(GeoEvidence evidence) {
        if (evidence == null) {
            return 0;
        }
        int score = 0;
        if (!evidence.getMatchedTerms().isEmpty()) {
            score += 40;
        }
        if (!evidence.getCitedUrls().isEmpty()) {
            score += 20;
        }
        Integer rank = evidence.getRankPosition();
        if (rank != null) {
            if (rank <= 3) {
                score += 25;
            } else if (rank <= 10) {
                score += 15;
            } else {
                score += 5;
            }
        }
        if (evidence.getOfficialSourceCount() > 0) {
            score += 15;
        }
        score -= Math.min(15, evidence.getCompetitorMentions().size() * 5);
        return Math.max(0, Math.min(score, 100));
    }
}
