package com.hzmwmt.domain.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.entity.function.TAdminDigitalModel;
import com.hzmwmt.domain.service.function.AdminDigitalModelService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class DigitalModelDataHandler {

    @Resource
    private AdminDigitalModelService adminDigitalModelService;

    // 解析JSON并保存到数据库
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void parseAndSave(String jsonData) throws Exception {
        if (jsonData == null || jsonData.length() > 10 * 1024 * 1024) throw new IllegalArgumentException("模型 JSON 过大");
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> jsonMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        List<Map<String, Object>> records = (List<Map<String, Object>>) ((Map<String, Object>) jsonMap.get("data")).get("records");
        if (records == null || records.isEmpty() || records.size() > 500) {
            throw new RuntimeException("JSON中无有效records数据");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<TAdminDigitalModel> modelList = records.stream().map(record -> {
            if (record.get("id") == null || !(record.get("speakerName") instanceof String name) || name.isBlank() || !(record.get("speakerId") instanceof String speakerId) || speakerId.isBlank()) throw new IllegalArgumentException("模型记录缺少 id、speakerName 或 speakerId");
            TAdminDigitalModel model = new TAdminDigitalModel();
            // 基础字段映射（speaker→model）
            model.setId((String) record.get("id"));
            model.setModelName((String) record.get("speakerName")); // 原speakerName → modelName
            model.setModelId((String) record.get("speakerId")); // 原speakerId → modelId
            model.setModelType((String) record.get("speakerType")); // 原speakerType → modelType
            model.setModelWidth(record.get("speakerWidth") != null ? ((Number) record.get("speakerWidth")).intValue() : null);
            model.setModelHeight(record.get("speakerHeight") != null ? ((Number) record.get("speakerHeight")).intValue() : null);
            model.setModelUrl((String) record.get("speakerUrl")); // 原speakerUrl → modelUrl
            model.setModelModelType((String) record.get("speakerModelType")); // 原speakerModelType → modelModelType
            model.setGender((String) record.get("gender"));
            model.setMagicAudioId((String) record.get("magicAudioId"));
            model.setHeadx(record.get("headx") != null ? ((Number) record.get("headx")).intValue() : null);
            model.setHeady(record.get("heady") != null ? ((Number) record.get("heady")).intValue() : null);
            model.setHeadr(record.get("headr") != null ? ((Number) record.get("headr")).intValue() : null);
            model.setHeadUrl((String) record.get("headUrl"));
            model.setHeadWidth(record.get("headWidth") != null ? ((Number) record.get("headWidth")).intValue() : null);
            model.setHeadHeight(record.get("headHeight") != null ? ((Number) record.get("headHeight")).intValue() : null);
            model.setCustomType((String) record.get("customType"));
            model.setCreated(record.get("created") != null ? LocalDateTime.parse((String) record.get("created"), formatter) : null);
            model.setStatus((String) record.get("status"));
            model.setLastUpdateTime(record.get("lastUpdateTime") != null ? LocalDateTime.parse((String) record.get("lastUpdateTime"), formatter) : null);
            // 补充字段映射
            model.setFigureUrl((String) record.get("figureUrl"));
            model.setRecommend(record.get("recommend") != null ? ((Number) record.get("recommend")).intValue() : null);
            model.setWwid((String) record.get("wwid"));
            model.setSortNum(record.get("sortNum") != null ? ((Number) record.get("sortNum")).intValue() : null);
            model.setPhone((String) record.get("phone"));
            model.setVipType((String) record.get("vipType"));
            model.setPublished(record.get("published") != null ? LocalDateTime.parse((String) record.get("published"), formatter) : null);
            model.setProduct((String) record.get("product"));
            model.setGifUrl((String) record.get("gifUrl"));
            model.setAlphaSourceOrigin((String) record.get("alphaSourceOrigin"));
            model.setModelDemoUrl((String) record.get("speakerDemoUrl")); // 原speakerDemoUrl → modelDemoUrl
            return model;
        }).toList();

        boolean success = adminDigitalModelService.batchSaveModels(modelList);
        if (!success) {
            throw new RuntimeException("数字人模型数据保存失败");
        }
    }

}
