package com.hzmwmt.geo.service;

import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoMonitorTaskMapper;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeoMonitorSchedulerWorkflowTest {
    @Test void allFourRetestDatesAreIndependentlyScheduledAndLinkedToTheSuggestion() {
        GeoMonitorScheduler scheduler = new GeoMonitorScheduler();
        GeoMonitorTaskMapper mapper = mock(GeoMonitorTaskMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        ReflectionTestUtils.setField(scheduler, "monitorTaskMapper", mapper);
        ReflectionTestUtils.setField(scheduler, "geoProperties", new GeoProperties());
        ReflectionTestUtils.setField(scheduler, "snowflakeIdGenerator", new SnowflakeIdGenerator(1, 1));
        var result = scheduler.scheduleRetests(GeoMonitorRecord.builder().queryId(1L).engine("qwen").build(),
                GeoQuery.builder().id(1L).queryText("问题").build(), 99L);
        assertThat(result).hasSize(4).allSatisfy(task -> {
            assertThat(task.getFeedbackSuggestionId()).isEqualTo(99L);
            assertThat(task.getRetestBatchId()).isEqualTo(99L);
        });
        assertThat(result.stream().map(task -> task.getPlannedTime().toLocalDate())).containsExactly(
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), LocalDate.now().plusDays(7), LocalDate.now().plusDays(14));
    }
}
