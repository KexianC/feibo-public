package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TCopywritingSource;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
@Hidden // Swagger 隐藏 Mapper 接口（无需在文档中展示）
public interface TCopywritingSourceMapper extends BaseMapper<TCopywritingSource> {
    // 继承 BaseMapper 后，无需手动编写 CRUD 方法，MP 自动实现

    /**
     * 根据文案来源ID查询来源名称
     *
     * @param copywritingSourceId 文案来源ID
     * @return 来源名称
     */
    @Select("SELECT name FROM t_copywriting_source WHERE copywriting_source_id = #{copywritingSourceId} AND status = 0")
    String selectSourceNameByCopywritingSourceId(Long copywritingSourceId);
}