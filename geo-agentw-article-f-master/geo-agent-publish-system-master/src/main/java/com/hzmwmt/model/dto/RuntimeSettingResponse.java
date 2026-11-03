package com.hzmwmt.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeSettingResponse {

    private String name;

    private String value;

    private String groupName;

    private boolean sensitive;
}
