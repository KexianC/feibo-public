package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.CopywritingSource;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingSourceMapper
 * @Date: 2025/9/3 17:16
 */
public interface CopywritingSourceMapper extends BaseMapper<CopywritingSource> {

    List<CopywritingSource> selectCopywritingSourceListByCopywritingSourceIds(@Param("sourceIds") List<Long> sourceIds);

    List<CopywritingSource> selectAllValidCopywritingSource();
}
