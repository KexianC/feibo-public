package com.hzmwmt.security;

import com.hzmwmt.domain.controller.log.LoginLogController;
import com.hzmwmt.domain.controller.log.OperationLogsController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AuditLogEndpointContractTest {

    @Test
    void auditEvidenceHasNoExternallyWritableControllerEndpoint() {
        for (Class<?> controller : new Class<?>[]{LoginLogController.class, OperationLogsController.class}) {
            for (Method method : controller.getDeclaredMethods()) {
                assertFalse(method.isAnnotationPresent(PostMapping.class),
                        () -> controller.getSimpleName() + "." + method.getName() + " must not accept forged audit entries");
                RequestMapping mapping = method.getAnnotation(RequestMapping.class);
                if (mapping != null) {
                    assertFalse(Arrays.asList(mapping.method()).contains(RequestMethod.POST),
                            () -> controller.getSimpleName() + "." + method.getName() + " must remain read-only");
                }
            }
        }
    }
}
