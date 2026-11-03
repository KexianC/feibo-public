package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.CopywritingCategory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingCategoryMapper
 * @Date: 2025/9/3 17:18
 */
public interface CopywritingCategoryMapper extends BaseMapper<CopywritingCategory> {

    List<CopywritingCategory> selectCopywritingCategoryListByCopywritingCategoryIds(@Param("categoryIds") List<Long> categoryIds);

    List<CopywritingCategory> selectAllValidCopywritingCategory();
}
