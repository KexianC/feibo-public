package com.hzmwmt.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DoctorStatisticsResponseDTO {
    public Long total;
    public Long knowledgeAvailable;
    public Long enabled;
    public Long disabled;
}
