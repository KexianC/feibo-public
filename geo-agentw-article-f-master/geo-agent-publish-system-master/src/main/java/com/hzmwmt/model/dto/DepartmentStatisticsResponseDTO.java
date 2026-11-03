package com.hzmwmt.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentStatisticsResponseDTO {
    public Long total;
    public Long hospital;
    public Long enabled;
    public Long disabled;
}
