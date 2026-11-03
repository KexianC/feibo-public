package com.hzmwmt.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApiOffsetPageData
 * @Date: 2025/9/9 17:21
 */
@AllArgsConstructor
@Data
@Builder
@NoArgsConstructor
public class ApiOffsetPageData<T> implements Serializable {


    /**
     * 数据
     */
    private List<T> data;
    /**
     * 是否有下一页
     */
    private Boolean hasNext;
    /**
     * 总数
     */
    private long total;
    /**
     * 当前页码
     */
    private int page;
    /**
     * 每页数量
     */
    private int size;
}
