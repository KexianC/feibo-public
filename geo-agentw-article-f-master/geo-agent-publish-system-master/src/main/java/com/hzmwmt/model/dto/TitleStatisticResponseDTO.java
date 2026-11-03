package com.hzmwmt.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TitleStatisticResponseDTO {
    public Long total;
    public Long enabled;
    public Long disabled;
    public Long maxLevel;
}
