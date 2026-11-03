package com.hzmwmt.job;

import com.hzmwmt.ai.rag.service.RagKnowledgeBaseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识库文档扫描定时任务。
 * 负责按固定时间触发 NAS 文档增量扫描与入库。
 */
@Slf4j
@Component
public class KnowledgeDocumentScanJob {

    @Resource
    private RagKnowledgeBaseService ragKnowledgeBaseService;

    /**
     * 每天凌晨两点执行一次文档扫描任务。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scanDocuments() {
        try {
            // 定时触发增量扫描，复用现有入库链路。
//            ragKnowledgeBaseService.ingestFileByScan();
            log.info("定时文档扫描任务执行完成");
        } catch (Exception exception) {
            log.error("定时文档扫描任务执行失败，exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
