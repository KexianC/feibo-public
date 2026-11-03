package com.hzmwmt.security;

import com.hzmwmt.api.ICopywritingApi;
import com.hzmwmt.domain.copywriting.service.manager.CopywritingManagerService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CopywritingTaxonomyAuthorizationContractTest {

    @Test
    void globalTaxonomyWritesRequireContentCapabilityAtApiAndServiceBoundaries() throws Exception {
        assertContentManager("createCopywritingCategory");
        assertContentManager("createCopywritingSource");
    }

    private void assertContentManager(String methodName) throws Exception {
        assertContentBoundary(ICopywritingApi.class.getMethod(methodName, String.class));
        assertContentBoundary(CopywritingManagerService.class.getMethod(methodName, String.class));
    }

    private void assertContentBoundary(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value())
                .isEqualTo("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')");
    }
}
