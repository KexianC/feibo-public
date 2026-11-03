package com.hzmwmt.geo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.geo.model.dto.GeoEngineAnswer;
import com.hzmwmt.geo.model.dto.GeoEvidence;
import com.hzmwmt.geo.model.entity.GeoQuery;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeoEvidenceParser {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\])}>\"'，。；、]+", Pattern.CASE_INSENSITIVE);

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private GeoOfficialDomainRegistry officialDomainRegistry;

    public GeoEvidence parse(GeoQuery query, GeoEngineAnswer answer, long measuredLatencyMs) {
        List<String> citedUrls = CollectionUtils.isEmpty(answer.getCitedUrls())
                ? extractUrls(answer.getAnswerText())
                : answer.getCitedUrls();
        List<Map<String, Object>> sourceDetails = parseSourceDetails(answer.getSourceDetails());
        List<String> expectedTerms = expectedTerms(query);
        List<String> matchedTerms = matchedTerms(answer.getAnswerText(), sourceDetails, expectedTerms);
        Integer rankPosition = firstMatchingRank(sourceDetails, matchedTerms);
        if (rankPosition == null) {
            rankPosition = answer.getRankPosition();
        }
        int sourceCount = answer.getSourceCount() == null ? sourceDetails.size() : answer.getSourceCount();
        if (sourceCount == 0) {
            sourceCount = citedUrls.size();
        }
        int officialSourceCount = officialSourceCount(citedUrls, sourceDetails);
        return GeoEvidence.builder()
                .answerText(answer.getAnswerText())
                .citedUrls(citedUrls)
                .matchedTerms(matchedTerms)
                .sourceDetails(sourceDetails)
                .rankPosition(rankPosition)
                .sourceCount(sourceCount)
                .officialSourceCount(officialSourceCount)
                .competitorMentions(competitorMentions(sourceDetails, query))
                .rawPayload(firstNonBlank(answer.getRawSearchInfo(), answer.getSourceDetails()))
                .latencyMs(answer.getLatencyMs() == null ? measuredLatencyMs : answer.getLatencyMs())
                .build();
    }

    private List<Map<String, Object>> parseSourceDetails(String sourceDetails) {
        if (!StringUtils.hasText(sourceDetails)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(sourceDetails, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> expectedTerms(GeoQuery query) {
        Set<String> terms = new LinkedHashSet<>();
        if (query == null) {
            return List.of();
        }
        if (StringUtils.hasText(query.getExpectedTerms())) {
            try {
                terms.addAll(objectMapper.readValue(query.getExpectedTerms(), new TypeReference<List<String>>() {}));
            } catch (Exception ignored) {
                terms.addAll(splitTerms(query.getExpectedTerms()));
            }
        }
        addIfPresent(terms, query.getHospitalName());
        addIfPresent(terms, query.getDepartmentName());
        addIfPresent(terms, query.getDoctorName());
        return terms.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private List<String> splitTerms(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        String[] parts = value.split("[,，\\n]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private void addIfPresent(Set<String> terms, String value) {
        if (StringUtils.hasText(value)) {
            terms.add(value.trim());
        }
    }

    private List<String> matchedTerms(String answer, List<Map<String, Object>> sourceDetails, List<String> expectedTerms) {
        if (expectedTerms.isEmpty()) {
            return List.of();
        }
        StringBuilder haystack = new StringBuilder(answer == null ? "" : answer);
        for (Map<String, Object> source : sourceDetails) {
            haystack.append(' ')
                    .append(stringValue(source.get("title")))
                    .append(' ')
                    .append(stringValue(source.get("snippet")))
                    .append(' ')
                    .append(stringValue(source.get("url")));
        }
        String normalized = haystack.toString().toLowerCase(Locale.ROOT);
        return expectedTerms.stream()
                .filter(StringUtils::hasText)
                .filter(term -> normalized.contains(term.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    private Integer firstMatchingRank(List<Map<String, Object>> sourceDetails, List<String> matchedTerms) {
        if (sourceDetails.isEmpty() || matchedTerms.isEmpty()) {
            return null;
        }
        for (int i = 0; i < sourceDetails.size(); i++) {
            Map<String, Object> source = sourceDetails.get(i);
            String haystack = (stringValue(source.get("title")) + " " + stringValue(source.get("snippet")) + " " + stringValue(source.get("url")))
                    .toLowerCase(Locale.ROOT);
            boolean matched = matchedTerms.stream()
                    .filter(StringUtils::hasText)
                    .anyMatch(term -> haystack.contains(term.toLowerCase(Locale.ROOT)));
            if (matched) {
                return intValue(source.get("rank"), i + 1);
            }
        }
        return null;
    }

    private int officialSourceCount(List<String> citedUrls, List<Map<String, Object>> sourceDetails) {
        Set<String> urls = new LinkedHashSet<>(citedUrls == null ? Collections.emptyList() : citedUrls);
        sourceDetails.stream()
                .map(source -> stringValue(source.get("url")))
                .filter(StringUtils::hasText)
                .forEach(urls::add);
        int count = 0;
        for (String url : urls) {
            if (officialDomainRegistry.isOfficialUrl(url)) {
                count++;
            }
        }
        return count;
    }

    private List<String> competitorMentions(List<Map<String, Object>> sourceDetails, GeoQuery query) {
        if (sourceDetails.isEmpty()) {
            return List.of();
        }
        String hospital = query == null || query.getHospitalName() == null ? "" : query.getHospitalName();
        List<String> competitors = new ArrayList<>();
        for (Map<String, Object> source : sourceDetails) {
            String title = stringValue(source.get("title"));
            String snippet = stringValue(source.get("snippet"));
            String url = stringValue(source.get("url"));
            String text = title + " " + snippet;
            if (!StringUtils.hasText(text) || text.contains(hospital) || officialSourceCount(List.of(url), List.of(source)) > 0) {
                continue;
            }
            if (text.contains("医院") || text.contains("科室") || text.contains("门诊") || text.contains("专家")) {
                competitors.add(title);
            }
            if (competitors.size() >= 5) {
                break;
            }
        }
        return competitors.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private List<String> extractUrls(String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(answer);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return new ArrayList<>(urls);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

}
