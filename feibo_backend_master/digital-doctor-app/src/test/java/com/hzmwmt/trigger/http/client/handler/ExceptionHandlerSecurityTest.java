package com.hzmwmt.trigger.http.client.handler;

import com.hzmwmt.api.response.Response;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.exception.AuthorizationDataUnavailableException;
import com.hzmwmt.types.exception.RateLimitException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class ExceptionHandlerSecurityTest {

    private static final String SECRET = "jdbc:mysql://db.internal/app?password=super-secret";
    private static final String TO_STRING_SECRET = "exception-toString-secret";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorProbeController())
                .setControllerAdvice(new ExceptionHandler())
                .build();
    }

    @Test
    void malformedArgumentsAndBodiesReturn400WithoutRawParserDetails() throws Exception {
        mockMvc.perform(get("/error-probe/number").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResponseCode.ILLEGAL_PARAMETER.getCode()))
                .andExpect(jsonPath("$.data").value("参数'value'的格式不正确，期望的类型是'Integer'"));

        mockMvc.perform(post("/error-probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":" + SECRET + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResponseCode.ILLEGAL_PARAMETER.getCode()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET))));

        mockMvc.perform(post("/error-probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResponseCode.ILLEGAL_PARAMETER.getCode()))
                .andExpect(jsonPath("$.info").value("value不能为空"));
    }

    @Test
    void authenticationAndAuthorizationUse401And403WithoutLeakingExceptionMessages() throws Exception {
        mockMvc.perform(get("/error-probe/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResponseCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET))));

        mockMvc.perform(get("/error-probe/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ResponseCode.FORBIDDEN.getCode()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET))));

        mockMvc.perform(get("/error-probe/app-unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResponseCode.TOKEN_EXPIRED.getCode()));

        mockMvc.perform(get("/error-probe/app-forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ResponseCode.FORBIDDEN.getCode()));
    }

    @Test
    void unsupportedMethodUses405() throws Exception {
        mockMvc.perform(get("/error-probe/post-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.REQUEST_METHOD_NOT_SUPPORTED.getCode()));
    }

    @Test
    void missingRouteUses404InsteadOfUnknown500() throws Exception {
        mockMvc.perform(post("/api/v1/ai/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"test reply\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.info")
                        .value(ResponseCode.RESOURCE_NOT_FOUND.getInfo()));
    }

    @Test
    void ordinaryRepeatAndDataConflictUse409ButRateLimitUses429() throws Exception {
        mockMvc.perform(get("/error-probe/repeated"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode()));

        mockMvc.perform(get("/error-probe/data-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ResponseCode.INDEX_EXCEPTION.getCode()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET))));

        mockMvc.perform(get("/error-probe/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode()));
    }

    @Test
    void unavailableResourceUses503AndHidesItsInternalAddress(
            CapturedOutput output) throws Exception {
        mockMvc.perform(get("/error-probe/resource"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ResponseCode.INTERNET_EXCEPTION.getCode()))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET))));

        mockMvc.perform(get("/error-probe/authorization-store"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ResponseCode.SERVICE_ERROR.getCode()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET))));

        String logs = output.getAll();
        assertTrue(logs.contains("type=ResourceAccessException"));
        assertTrue(logs.contains("code=" + ResponseCode.INTERNET_EXCEPTION.getCode()));
        assertTrue(logs.contains("type=AuthorizationDataUnavailableException"));
        assertTrue(logs.contains("code=" + ResponseCode.SERVICE_ERROR.getCode()));
        assertFalse(logs.contains(SECRET));
        assertFalse(logs.contains("\tat "));
    }

    @Test
    void unknownAndInternalExceptionsUse500AndNeverExposeRawDetails(
            CapturedOutput output) throws Exception {
        mockMvc.perform(get("/error-probe/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ResponseCode.RUNTIME_EXCEPTION.getCode()))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET))));

        mockMvc.perform(get("/error-probe/checked"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ResponseCode.UN_ERROR.getCode()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET))));

        mockMvc.perform(get("/error-probe/internal-app"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ResponseCode.SERVICE_ERROR.getCode()))
                .andExpect(jsonPath("$.info").value(ResponseCode.SERVICE_ERROR.getInfo()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(SECRET))));

        String logs = output.getAll();
        assertTrue(logs.contains("type=SensitiveRuntimeException"));
        assertTrue(logs.contains("type=SensitiveCheckedException"));
        assertTrue(logs.contains("type=AppException"));
        assertTrue(logs.contains("code=" + ResponseCode.SERVICE_ERROR.getCode()));
        assertTrue(logs.contains("httpStatus=500"));
        assertFalse(logs.contains(SECRET));
        assertFalse(logs.contains(TO_STRING_SECRET));
        assertFalse(logs.contains("\tat "));
    }

    @Test
    void explicitClientBusinessFailureRetainsItsSafeBusinessMessage() throws Exception {
        mockMvc.perform(get("/error-probe/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResponseCode.ILLEGAL_PARAMETER.getCode()))
                .andExpect(jsonPath("$.info").value("状态异常"));
    }

    @RestController
    @RequestMapping("/error-probe")
    private static class ErrorProbeController {

        @GetMapping("/number")
        Response<Void> number(@RequestParam Integer value) {
            return Response.<Void>builder().build();
        }

        @PostMapping("/body")
        Response<Void> body(@RequestBody ProbeBody body) {
            return Response.<Void>builder().build();
        }

        @PostMapping("/validated")
        Response<Void> validated(@Valid @RequestBody ValidatedProbeBody body) {
            return Response.<Void>builder().build();
        }

        @GetMapping("/unauthenticated")
        Response<Void> unauthenticated() {
            throw new AuthenticationCredentialsNotFoundException(SECRET);
        }

        @GetMapping("/forbidden")
        Response<Void> forbidden() {
            throw new AccessDeniedException(SECRET);
        }

        @GetMapping("/app-unauthenticated")
        Response<Void> appUnauthenticated() {
            throw new AppException(ResponseCode.TOKEN_EXPIRED.getCode(), SECRET);
        }

        @GetMapping("/app-forbidden")
        Response<Void> appForbidden() {
            throw new AppException(ResponseCode.FORBIDDEN);
        }

        @PostMapping("/post-only")
        Response<Void> postOnly() {
            return Response.<Void>builder().build();
        }

        @GetMapping("/repeated")
        Response<Void> repeated() {
            throw new AppException(ResponseCode.REPEATED_OPERATION_EXCEPTION);
        }

        @GetMapping("/data-conflict")
        Response<Void> dataConflict() {
            throw new DataIntegrityViolationException(SECRET);
        }

        @GetMapping("/rate-limit")
        Response<Void> rateLimit() {
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }

        @GetMapping("/resource")
        Response<Void> resource() {
            throw new ResourceAccessException(SECRET);
        }

        @GetMapping("/authorization-store")
        Response<Void> authorizationStore() {
            throw new AuthorizationDataUnavailableException(
                    new IllegalStateException(SECRET));
        }

        @GetMapping("/runtime")
        Response<Void> runtime() {
            throw new SensitiveRuntimeException(SECRET);
        }

        @GetMapping("/checked")
        Response<Void> checked() throws Exception {
            throw new SensitiveCheckedException(SECRET);
        }

        @GetMapping("/internal-app")
        Response<Void> internalApp() {
            throw new AppException(ResponseCode.SERVICE_ERROR.getCode(), SECRET);
        }

        @GetMapping("/business")
        Response<Void> business() {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "状态异常");
        }
    }

    private record ProbeBody(String value) {
    }

    private record ValidatedProbeBody(@NotBlank(message = "value不能为空") String value) {
    }

    private static final class SensitiveRuntimeException extends RuntimeException {
        private SensitiveRuntimeException(String message) {
            super(message);
        }

        @Override
        public String toString() {
            return TO_STRING_SECRET;
        }
    }

    private static final class SensitiveCheckedException extends Exception {
        private SensitiveCheckedException(String message) {
            super(message);
        }

        @Override
        public String toString() {
            return TO_STRING_SECRET;
        }
    }
}
