package com.hzmwmt.ai.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.ai.rag.config.RagProperties;
import com.hzmwmt.ai.rag.document.DocumentParseStrategy;
import com.hzmwmt.ai.rag.document.DocumentParseStrategyFactory;
import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import com.hzmwmt.ai.rag.model.dto.KnowledgeIngestResponse;
import com.hzmwmt.ai.rag.model.dto.KnowledgeSearchHit;
import com.hzmwmt.ai.rag.model.entity.KnowledgeChunk;
import com.hzmwmt.ai.rag.model.entity.QdrantPoint;
import com.hzmwmt.ai.rag.model.entity.QdrantSearchHit;
import com.hzmwmt.ai.rag.service.KnowledgeChunker;
import com.hzmwmt.ai.rag.service.KnowledgeAgentScopeResolver;
import com.hzmwmt.ai.rag.service.QdrantRestClient;
import com.hzmwmt.ai.rag.service.RagKnowledgeBaseService;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.constant.KnowledgeDocumentConstants;
import com.hzmwmt.ai.rag.mapper.KnowledgeDocumentMapper;
import com.hzmwmt.mapper.DoctorManagementMapper;
import com.hzmwmt.mapper.KnowledgeVectorConversionMapper;
import com.hzmwmt.model.entity.DoctorInfo;
import com.hzmwmt.model.entity.KnowledgeDocument;
import com.hzmwmt.model.entity.KnowledgeVectorConversion;
import com.hzmwmt.util.FileSecurityUtils;
import com.hzmwmt.util.LocalFileMultipartFile;
import com.hzmwmt.util.SnowflakeIdGenerator;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class RagKnowledgeBaseServiceImpl implements RagKnowledgeBaseService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    @Value("${langchain4j.community.dashscope.embedding-model.model-name}")
    private String embeddingModelName;

    @Value("${storage-path}")
    private String uploadDir;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private KnowledgeChunker knowledgeChunker;

    @Resource
    private DocumentParseStrategyFactory documentParseStrategyFactory;

    @Resource
    private QdrantRestClient qdrantRestClient;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private KnowledgeVectorConversionMapper vectorConversionMapper;

    @Resource
    private DoctorManagementMapper doctorManagementMapper;

    @Resource
    private KnowledgeAgentScopeResolver scopeResolver;

    // ==================== 公共方法实现 ====================

    @Override
    public KnowledgeIngestResponse convertDocument(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new AppException("PARAM_INVALID", "文档ID不合法");
        }

        KnowledgeDocument knowledgeDocument = knowledgeDocumentMapper.selectById(documentId);
        if (knowledgeDocument == null) {
            throw new AppException("DOCUMENT_NOT_FOUND", "文档不存在");
        }

        // 正在处理中 直接返回
        if(KnowledgeDocumentConstants.STATUS_PROCESSING == knowledgeDocument.getStatus()){
            throw new AppException("CONVERT_PROCESSING", "文件转化进行中，请勿重复提交");
        }

        // 已成功转化，直接返回已有信息
        if (KnowledgeDocumentConstants.STATUS_SUCCESS == knowledgeDocument.getStatus()) {
            log.info("文档已成功转化，跳过重复处理，documentId={}", documentId);
            return new KnowledgeIngestResponse(
                    knowledgeDocument.getId(),
                    knowledgeDocument.getFileName(),
                    qdrantRestClient.collectionName(),
                    knowledgeDocument.getChunkCount()
            );
        }
        KnowledgeVectorConversion conversion = new KnowledgeVectorConversion();
        conversion.setId(snowflakeIdGenerator.nextId());
        conversion.setDocumentId(knowledgeDocument.getId());
        conversion.setFileName(knowledgeDocument.getFileName());
        conversion.setStatus(KnowledgeDocumentConstants.STATUS_PROCESSING);
        conversion.setModel(embeddingModelName);
        conversion.setAgentType(knowledgeDocument.getAgentType());
        conversion.setHospitalId(knowledgeDocument.getHospitalId());
        conversion.setHospitalName(knowledgeDocument.getHospitalName());
        conversion.setDepartmentId(knowledgeDocument.getDepartmentId());
        conversion.setDepartmentName(knowledgeDocument.getDepartmentName());
        conversion.setDoctorId(knowledgeDocument.getDoctorId());
        conversion.setDoctorName(knowledgeDocument.getDoctorName());
        conversion.setConvertTime(LocalDateTime.now());
        vectorConversionMapper.insert(conversion);

        String filePath = knowledgeDocument.getFilePath();
        if (!StringUtils.hasText(filePath)) {
            markDocumentFailed(knowledgeDocument, conversion,"文件路径为空");
            throw new AppException("FILE_PATH_EMPTY", "文档文件路径为空");
        }

        Path path = Paths.get(filePath);
        try {
            FileSecurityUtils.assertPathInsideBase(uploadDir, path);
        } catch (AppException e) {
            markDocumentFailed(knowledgeDocument, conversion, e.getMessage());
            throw e;
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            markDocumentFailed(knowledgeDocument, conversion, "文件不存在");
            throw new AppException("FILE_NOT_FOUND", "文件不存在");
        }

        MultipartFile file = new LocalFileMultipartFile(path);
        String filename = knowledgeDocument.getFileName();
        if (!StringUtils.hasText(filename)) {
            filename = file.getOriginalFilename();
        }

        try {
            // 更新为“处理中”状态，避免并发重复提交
            markDocumentPending(knowledgeDocument);

            DocumentParseStrategy strategy = documentParseStrategyFactory.getStrategy(filename);
            Document document = strategy.parse(file);

            // 补充元数据
            enrichDocumentMetadata(document, filename, buildFileMetadata(file, filename));
            return ingestDocument(knowledgeDocument, conversion, filename, document);
        } catch (Exception e) {
            markDocumentFailed(knowledgeDocument, conversion, e.getMessage());
            log.error("文档转化失败，documentId={}, exceptionType={}",
                    documentId, e.getClass().getSimpleName());
            throw new AppException("DOCUMENT_CONVERT_ERROR", "文档转化失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void deleteDocumentVector(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new AppException("PARAM_INVALID", "文档ID不合法");
        }
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc == null) {
            throw new RuntimeException("文档不存在");
        }

        // 删除物理文件
        if (doc.getFilePath() != null) {
            try {
                Path filePath = Paths.get(doc.getFilePath());
                FileSecurityUtils.assertPathInsideBase(uploadDir, filePath);
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.error("删除物理文件失败，documentId={}, exceptionType={}",
                        documentId, e.getClass().getSimpleName());
                // 视业务需求决定是否抛出异常
            }
        }
        qdrantRestClient.deleteByFileId(documentId);
        knowledgeDocumentMapper.deleteById(documentId);
        if (doc.getDoctorId() != null && !hasSuccessfulDoctorDocument(doc.getDoctorId())) {
            DoctorInfo up = new DoctorInfo();
            up.setId(doc.getDoctorId());
            up.setKnowledgeStatus(KnowledgeDocumentConstants.STATUS_FAILED);
            doctorManagementMapper.updateById(up);
        }
        log.info("删除文档向量及记录，documentId={}", documentId);
    }

    @Override
    public List<KnowledgeSearchHit> search(String query, Integer limit, Long doctorId) {
        KnowledgeAgentScope scope = scopeResolver.resolve(null, null, null, doctorId);
        return search(query, limit, scope);
    }

    @Override
    public List<KnowledgeSearchHit> search(String query, Integer limit, KnowledgeAgentScope scope) {
        if (!StringUtils.hasText(query)) {
            throw new AppException("PARAM_INVALID", "检索内容不能为空");
        }

        Embedding queryEmbedding = embeddingModel.embed(query.trim()).content();
        qdrantRestClient.ensureCollection(queryEmbedding.dimension());

        int topK = Math.min(limit == null || limit <= 0 ? ragProperties.getTopK() : limit, 50);
        List<QdrantSearchHit> matches = qdrantRestClient.search(
                scope,
                queryEmbedding.vectorAsList(),
                topK,
                ragProperties.getScoreThreshold()
        );

        List<KnowledgeSearchHit> hits = new ArrayList<>(matches.size());
        for (QdrantSearchHit match : matches) {
            Map<String, Object> payload = match.getPayload() == null ? Map.of() : match.getPayload();
            hits.add(new KnowledgeSearchHit(
                    match.getId(),
                    convertToLong(payload.get("fileId")),
                    convertToInt(payload.get("agentType")),
                    convertToNullableLong(payload.get("hospitalId")),
                    convertToString(payload.get("hospitalName")),
                    convertToNullableLong(payload.get("departmentId")),
                    convertToString(payload.get("departmentName")),
                    convertToNullableLong(payload.get("doctorId")),
                    convertToString(payload.get("doctorName")),
                    match.getScore(),
                    convertToString(payload.get("title")),
                    convertToInt(payload.get("index")),
                    convertToString(payload.get("text")),
                    stripReservedMetadata(payload)
            ));
        }
        return hits;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 文档切块、向量化并写入 Qdrant，完成后更新文档状态。
     */
    private KnowledgeIngestResponse ingestDocument(KnowledgeDocument knowledgeDocument, KnowledgeVectorConversion conversion, String title, Document document) {
        long fileId = knowledgeDocument.getId();
        List<KnowledgeChunk> chunks = knowledgeChunker.split(fileId, document);
        if (chunks.isEmpty()) {
            throw new AppException("PARAM_INVALID", "知识库内容不能为空");
        }

        List<QdrantPoint> points = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            Embedding embedding = embeddingModel.embed(chunk.getText()).content();
            List<Float> vector = embedding.vectorAsList();
            qdrantRestClient.ensureCollection(vector.size());
            points.add(new QdrantPoint(
                    snowflakeIdGenerator.nextId(),
                    vector,
                    buildPayload(title, chunk, knowledgeDocument)
            ));
        }

        qdrantRestClient.upsert(points);
        markDocumentSuccess(knowledgeDocument, conversion,points.size());

        log.info("知识导入完成，fileId={}, chunkCount={}", fileId, points.size());
        return new KnowledgeIngestResponse(fileId, title, qdrantRestClient.collectionName(), points.size());
    }

    private void enrichDocumentMetadata(Document document, String title, Map<String, Object> extraMetadata) {
        if (document == null) {
            return;
        }
        Metadata metadata = document.metadata();
        if (StringUtils.hasText(title)) {
            metadata.put("title", title);
        }
        if (extraMetadata == null || extraMetadata.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : extraMetadata.entrySet()) {
            putMetadata(metadata, entry.getKey(), entry.getValue());
        }
    }

    private Map<String, Object> buildFileMetadata(MultipartFile file, String filename) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fileName", filename);
        metadata.put("fileSize", file.getSize());
        if (StringUtils.hasText(file.getContentType())) {
            metadata.put("contentType", file.getContentType());
        }
        return metadata;
    }

    private Map<String, Object> buildPayload(String title, KnowledgeChunk chunk, KnowledgeDocument knowledgeDocument) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (chunk.getMetadata() != null && !chunk.getMetadata().isEmpty()) {
            chunk.getMetadata().forEach((key, value) -> payload.put(key, sanitizePayloadValue(value)));
        }
        payload.put("fileId", chunk.getFileId());
        payload.put("title", title);
        payload.put("index", chunk.getIndex());
        payload.put("text", chunk.getText());
        payload.put("agentType", knowledgeDocument.getAgentType());
        putIfNotNull(payload, "hospitalId", knowledgeDocument.getHospitalId());
        putIfText(payload, "hospitalName", knowledgeDocument.getHospitalName());
        putIfNotNull(payload, "departmentId", knowledgeDocument.getDepartmentId());
        putIfText(payload, "departmentName", knowledgeDocument.getDepartmentName());
        putIfNotNull(payload, "doctorId", knowledgeDocument.getDoctorId());
        putIfText(payload, "doctorName", knowledgeDocument.getDoctorName());
        return payload;
    }

    private void markDocumentPending(KnowledgeDocument knowledgeDocument) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(knowledgeDocument.getId());
        update.setStatus(KnowledgeDocumentConstants.STATUS_PROCESSING);
        knowledgeDocumentMapper.updateById(update);
    }

    private void markDocumentSuccess(KnowledgeDocument knowledgeDocument, KnowledgeVectorConversion conversion, int chunkCount) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(knowledgeDocument.getId());
        update.setChunkCount(chunkCount);
        update.setStatus(KnowledgeDocumentConstants.STATUS_SUCCESS);
        update.setErrorMessage("");
        knowledgeDocumentMapper.updateById(update);

        KnowledgeVectorConversion up = new KnowledgeVectorConversion();
        up.setId(conversion.getId());
        up.setVectorCount(chunkCount);
        up.setStatus(KnowledgeDocumentConstants.STATUS_SUCCESS);
        up.setErrorMessage("");
        vectorConversionMapper.updateById(up);

        if (knowledgeDocument.getDoctorId() != null) {
            DoctorInfo doctorUp = new DoctorInfo();
            doctorUp.setId(knowledgeDocument.getDoctorId());
            doctorUp.setKnowledgeStatus(KnowledgeDocumentConstants.STATUS_SUCCESS);
            doctorManagementMapper.updateById(doctorUp);
        }
    }

    private void markDocumentFailed(KnowledgeDocument knowledgeDocument, KnowledgeVectorConversion conversion, String errorMessage) {
        if (knowledgeDocument == null || knowledgeDocument.getId() == null) {
            return;
        }
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(knowledgeDocument.getId());
        update.setStatus(KnowledgeDocumentConstants.STATUS_FAILED);
        update.setErrorMessage(trimErrorMessage(errorMessage));
        knowledgeDocumentMapper.updateById(update);

        if(conversion == null || conversion.getId() == null){
            return;
        }
        KnowledgeVectorConversion up = new KnowledgeVectorConversion();
        up.setId(conversion.getId());
        up.setStatus(KnowledgeDocumentConstants.STATUS_FAILED);
        up.setErrorMessage(trimErrorMessage(errorMessage));
        vectorConversionMapper.updateById(up);
    }

    private String trimErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return null;
        }
        if (errorMessage.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }

    private void putMetadata(Metadata metadata, String key, Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }
        if (value instanceof String str) {
            metadata.put(key, str);
        } else if (value instanceof Integer i) {
            metadata.put(key, i);
        } else if (value instanceof Long l) {
            metadata.put(key, l);
        } else if (value instanceof Float f) {
            metadata.put(key, f);
        } else if (value instanceof Double d) {
            metadata.put(key, d);
        } else {
            metadata.put(key, String.valueOf(value));
        }
    }

    private Object sanitizePayloadValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    private Map<String, Object> stripReservedMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(metadata);
        result.remove("fileId");
        result.remove("title");
        result.remove("index");
        result.remove("text");
        result.remove("filePath");
        result.remove("agentType");
        result.remove("hospitalId");
        result.remove("hospitalName");
        result.remove("departmentId");
        result.remove("departmentName");
        result.remove("doctorId");
        result.remove("doctorName");
        return result;
    }

    private boolean hasSuccessfulDoctorDocument(Long doctorId) {
        Long count = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDoctorId, doctorId)
                .eq(KnowledgeDocument::getStatus, KnowledgeDocumentConstants.STATUS_SUCCESS));
        return count != null && count > 0;
    }

    private void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private void putIfText(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private long convertToLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("无法将值转换为 Long，exceptionType={}", e.getClass().getSimpleName());
            return 0L;
        }
    }

    private Long convertToNullableLong(Object value) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return null;
        }
        return convertToLong(value);
    }

    private int convertToInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Long l) {
            return l.intValue();
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("无法将值转换为 Integer，exceptionType={}", e.getClass().getSimpleName());
            return 0;
        }
    }

    private String convertToString(Object value) {
        return value == null ? "" : value.toString();
    }
}
