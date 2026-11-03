package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalWork;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalWorkMapper
 * @Date: 2025/9/24 13:41
 */
public interface DigitalWorkMapper extends BaseMapper<DigitalWork> {

    List<DigitalWork> selectUserWorkListCursorPageByUserId(Long userId, Long lastCursorId, int size);
}
