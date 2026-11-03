package com.hzmwmt.geo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.geo")
public class GeoProperties {

    private boolean monitorEnabled = false;

    private int monitorBatchSize = 20;

    private int monitorStaleHours = 24;

    private int workerBatchSize = 8;

    private int workerConcurrency = 3;

    private int platformConcurrency = 1;

    private long platformMinIntervalMs = 1200;

    private long workerPollMs = 15000;

    private int taskMaxAttempts = 2;

    private int taskRetryDelayMinutes = 10;

    private boolean autoOptimizationEnabled = true;

    private int feedbackContextLimit = 4000;

    private boolean probeEnabled = true;

    private int probeWorkerBatchSize = 4;

    private int probeWorkerConcurrency = 2;

    private long probePollMs = 10000;

    private int probeMaxRounds = 2;

    private boolean websiteMonitorEnabled = true;

    private int websiteWorkerBatchSize = 4;

    private int websiteWorkerConcurrency = 2;

    private long websiteWorkerPollMs = 15000;

    private int websiteConnectTimeoutMs = 3000;

    private int websiteReadTimeoutMs = 8000;

    private int websiteMaxBytes = 2097152;

    private int websiteMaxRedirects = 3;

    private List<String> officialDomains = new ArrayList<>();

    private List<EngineProperties> engines = new ArrayList<>();

    @Data
    public static class EngineProperties {

        private String name;

        /**
         * Supported values: dashscope, openai-compatible, qianfan-ai-search.
         */
        private String protocol = "openai-compatible";

        /**
         * OpenAI-compatible chat completions endpoint.
         */
        private String baseUrl;

        private String apiKey;

        private String model;

        private int timeoutSeconds = 60;

        private boolean enabled = true;

        private boolean enableSearch = true;

        private boolean enableSource = true;

        private boolean enableCitation = true;

        private String searchStrategy = "max";

        private int searchTopK = 10;

        private String searchRecencyFilter;

        private boolean safeSearch = true;

        private double temperature = 0.2;
    }
}
