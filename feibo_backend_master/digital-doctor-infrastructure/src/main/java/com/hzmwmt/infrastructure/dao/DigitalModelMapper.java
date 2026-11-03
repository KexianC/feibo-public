package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalModel;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalModelMapper
 * @Date: 2025/9/12 23:52
 */
public interface DigitalModelMapper extends BaseMapper<DigitalModel> {
    List<DigitalModel> selectDigitalModelCursorPageByUserId(@Param("userId") Long userId, @Param("lastCursorId") Long lastCursorId, @Param("size") Integer size);
}
