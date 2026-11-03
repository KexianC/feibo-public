package com.hzmwmt.ai.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.model.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}