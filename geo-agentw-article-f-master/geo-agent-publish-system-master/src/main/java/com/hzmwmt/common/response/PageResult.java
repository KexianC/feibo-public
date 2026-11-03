package com.hzmwmt.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 分页接口返回对象。
 *
 * @param <T> 数据类型
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageResult<T> extends Result<List<T>> {

    /**
     * 当前页码。
     */
    private Long pageNum;

    /**
     * 分页大小。
     */
    private Long pageSize;

    /**
     * 总条数。
     */
    private Long total;

    /**
     * 总页数。
     */
    private Long pages;

    /**
     * 根据 MyBatis-Plus 分页对象构建分页结果。
     *
     * @param page 分页对象
     * @param <T> 数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setSuccess(true);
        result.setCode("SUCCESS");
        result.setMessage("操作成功");
        result.setData(page.getRecords());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        return result;
    }
}
