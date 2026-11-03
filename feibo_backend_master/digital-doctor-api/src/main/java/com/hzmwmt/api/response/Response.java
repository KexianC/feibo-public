package com.hzmwmt.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> implements Serializable {

    private static final long serialVersionUID = 7000723935764546321L;

    private String code;
    private String info;
    private T data;

    public static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code("200")
                .info("success")
                .data(data)
                .build();
    }

    public static <T> Response<T> fail(String code, String info) {
        return Response.<T>builder()
                .code(code)
                .info(info)
                .build();
    }

}