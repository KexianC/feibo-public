package com.hzmwmt.security;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.controller.function.AdminCopywritingCategoryController;
import com.hzmwmt.domain.controller.function.AdminDigitalTemplateController;
import com.hzmwmt.domain.entity.function.TCopywritingCategory;
import com.hzmwmt.domain.entity.function.TDigitalTemplate;
import com.hzmwmt.domain.service.function.AdminCopywritingCategoryService;
import com.hzmwmt.domain.service.function.AdminDigitalTemplateService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class AdminContentConfigurationTest {
    @Test void categoryCreatesBusinessIdAndMapsEnabledStatusWithoutAcceptingOwnershipFields() {
        var service=mock(AdminCopywritingCategoryService.class);var controller=new AdminCopywritingCategoryController();ReflectionTestUtils.setField(controller,"adminCopywritingCategoryService",service);
        when(service.save(any())).thenAnswer(call->{TCopywritingCategory c=call.getArgument(0);c.setId(8L);return true;});
        var input=new TCopywritingCategory();input.setId(99L);input.setCopywritingCategoryId(1L);input.setName(" Science ");input.setStatus(false);
        TCopywritingCategory result=controller.save(input).getData();assertEquals(8L,result.getId());assertNotEquals(1L,result.getCopywritingCategoryId());assertEquals("Science",result.getName());assertFalse(result.getStatus());
    }
    @Test void categorySearchTargetsThePersistedName() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"category-test"),TCopywritingCategory.class);
        var service=mock(AdminCopywritingCategoryService.class);var controller=new AdminCopywritingCategoryController();ReflectionTestUtils.setField(controller,"adminCopywritingCategoryService",service);
        when(service.page(any(Page.class),any(Wrapper.class))).thenAnswer(call->{Wrapper<?> query=call.getArgument(1);assertTrue(query.getSqlSegment().contains("name LIKE"));return call.getArgument(0);});
        controller.page(1,10,"Science");verify(service).page(any(Page.class),any(Wrapper.class));
    }
    @Test void templateBodyRoundTripHasBusinessIdAndSystemCatalogueOwnership() {
        var service=mock(AdminDigitalTemplateService.class);var controller=new AdminDigitalTemplateController();
        ReflectionTestUtils.setField(controller,"adminDigitalTemplateService",service);ReflectionTestUtils.setField(controller,"objectMapper",new ObjectMapper());
        when(service.save(any())).thenAnswer(call->{TDigitalTemplate t=call.getArgument(0);t.setId(8);return true;});
        var request=new TDigitalTemplate();request.setTitle("Template");request.setBody("{\"active\":0,\"elements\":[]}");request.setUserId(999L);
        var created=controller.add(request);assertNull(created.getUserId());assertNotNull(created.getDigitalTemplateId());assertEquals(request.getBody(),created.getBody());
        request.setBody("invalid");assertThrows(ResponseStatusException.class,()->controller.add(request));verify(service,times(1)).save(any());
    }
}
