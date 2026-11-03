package com.hzmwmt.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hzmwmt.domain.entity.function.*;
import com.hzmwmt.domain.handler.DigitalModelDataHandler;
import com.hzmwmt.domain.mapper.function.AdminDigitalModelMapper;
import com.hzmwmt.domain.mapper.function.TDigitalImageMapper;
import com.hzmwmt.domain.service.function.AdminDigitalModelService;
import com.hzmwmt.domain.service.function.impl.AdminDigitalModelServiceImpl;
import com.hzmwmt.domain.service.function.impl.TDigitalImageServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminMaterialQueryTest {
    @Test void backgroundUsesNameColumnAndInclusiveEndDateThroughExclusiveNextDay() {
        TDigitalImageMapper mapper=mock(TDigitalImageMapper.class);
        TDigitalImageServiceImpl service=new TDigitalImageServiceImpl();
        ReflectionTestUtils.setField(service,"baseMapper",mapper);
        service.pageWithLike(2,5,"背景",0,LocalDate.of(2026,9,1),LocalDate.of(2026,9,9));
        @SuppressWarnings("rawtypes") ArgumentCaptor<QueryWrapper> query=ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(any(),query.capture());
        String sql=query.getValue().getSqlSegment();
        assertTrue(sql.contains("name LIKE"),sql);
        assertFalse(sql.contains("title LIKE"),sql);
        assertTrue(sql.contains("create_time >="),sql);
        assertTrue(sql.contains("create_time <"),sql);
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(LocalDate.of(2026,9,10).atStartOfDay()));
        assertThrows(IllegalArgumentException.class,()->service.pageWithLike(1,5,null,0,LocalDate.of(2026,9,9),LocalDate.of(2026,9,1)));
    }

    @Test void modelNameSearchAndSortTargetPersistentColumns() {
        AdminDigitalModelMapper mapper=mock(AdminDigitalModelMapper.class);
        AdminDigitalModelServiceImpl service=new AdminDigitalModelServiceImpl();
        ReflectionTestUtils.setField(service,"baseMapper",mapper);
        service.pageDigitalModels(1,5,"测试",null,null,null);
        @SuppressWarnings("rawtypes") ArgumentCaptor<QueryWrapper> query=ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(any(),query.capture());
        String sql=query.getValue().getSqlSegment();
        assertTrue(sql.contains("model_name LIKE"),sql);
        assertTrue(sql.contains("sort_num ASC") && sql.contains("id DESC"),sql);
    }

    @Test void importValidatesAllRowsBeforeSavingAndMapsSupplierFields() throws Exception {
        AdminDigitalModelService service=mock(AdminDigitalModelService.class);
        DigitalModelDataHandler handler=new DigitalModelDataHandler();
        ReflectionTestUtils.setField(handler,"adminDigitalModelService",service);
        String invalid="{\"data\":{\"records\":[{\"id\":\"12\",\"speakerName\":\"有效\",\"speakerId\":\"p12\"},{\"id\":\"13\"}]}}";
        assertThrows(IllegalArgumentException.class,()->handler.parseAndSave(invalid));
        verify(service,never()).batchSaveModels(any());
        when(service.batchSaveModels(any())).thenReturn(true);
        handler.parseAndSave("{\"data\":{\"records\":[{\"id\":\"12\",\"speakerName\":\"有效\",\"speakerId\":\"p12\",\"sortNum\":4,\"status\":\"published\"}]}}");
        @SuppressWarnings({"rawtypes","unchecked"}) ArgumentCaptor<List<TAdminDigitalModel>> records=ArgumentCaptor.forClass((Class)List.class);
        verify(service).batchSaveModels(records.capture());
        assertEquals("有效",records.getValue().get(0).getModelName());
        assertEquals("p12",records.getValue().get(0).getModelId());
        assertEquals(4,records.getValue().get(0).getSortNum());
    }
}
