package com.hzmwmt.types.common;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CursorPageResponseData<T> {
    private List<T> data;
    private Boolean hasNext;
    private Long nextCursorId;

    public CursorPageResponseData() {
    }

    public CursorPageResponseData(List<T> data, Boolean hasNext, Long nextCursorId) {
        this.data = data;
        this.hasNext = hasNext;
        this.nextCursorId = nextCursorId;
    }

    public static <T> CursorPageResponseData<T> of(List<T> data, Boolean hasNext, Long nextCursorId) {
        return new CursorPageResponseData<>(data, hasNext, nextCursorId);
    }
}