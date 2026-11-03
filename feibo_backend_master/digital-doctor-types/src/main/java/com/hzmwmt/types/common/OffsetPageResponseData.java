package com.hzmwmt.types.common;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OffsetPageResponseData<T> {
    private List<T> data;
    private Boolean hasNext;
    private Long total;
    private Integer page;
    private Integer size;

    // 无参构造器
    public OffsetPageResponseData() {
    }

    // 全参构造器
    public OffsetPageResponseData(List<T> data, Boolean hasNext, Long total, Integer page, Integer size) {
        this.data = data;
        this.hasNext = hasNext;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    // 静态工厂方法
    public static <T> OffsetPageResponseData<T> of(List<T> data, Boolean hasNext, Long total, Integer page, Integer size) {
        return new OffsetPageResponseData<>(data, hasNext, total, page, size);
    }

    // 空数据的便捷方法
    public static <T> OffsetPageResponseData<T> empty(int page, int size) {
        return OffsetPageResponseData.<T>builder()
                .data(null)
                .hasNext(false)
                .total(0L)
                .page(page)
                .size(size)
                .build();
    }
}