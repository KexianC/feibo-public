package com.hzmwmt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.model.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeDocumentService {
    /**
     * 上传文件并保存记录
     */
    KnowledgeDocument uploadDocument(MultipartFile file, Integer agentType, Long hospitalId, Long departmentId, Long doctorId, String remark);

    /**
     * 删除文档（包含物理文件）
     */
    void deleteDocument(Long id);

    /**
     * 分页查询文档列表（可按医生ID、状态等筛选）
     */
    IPage<KnowledgeDocument> page(Page<KnowledgeDocument> page, Integer agentType, Long hospitalId, Long departmentId,
                                  Long doctorId, String fileName, Integer status);
}
