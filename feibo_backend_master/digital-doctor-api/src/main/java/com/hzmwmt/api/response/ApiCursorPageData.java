package com.hzmwmt.api.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApiCursorPageDTO
 * @Date: 2025/8/21 14:42
 */
@Data
@Builder
public class ApiCursorPageData<T> {
    /**
     * 当前页的数据列表
     */
    private List<T> data;

    /**
     * 是否有下一页
     */
    private Boolean hasNext;

    /**
     * 下一页的游标ID
     */
    private Long nextCursorId;

}
