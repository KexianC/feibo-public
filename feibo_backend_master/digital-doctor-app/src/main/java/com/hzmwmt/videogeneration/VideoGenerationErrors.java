package com.hzmwmt.videogeneration;

import com.hzmwmt.domain.common.Result;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={VideoGenerationController.class, VideoGenerationAdminController.class, VideoQuotaReconciliationController.class})
public class VideoGenerationErrors {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Result<Void> invalid(RuntimeException exception) { return Result.error(400, exception.getMessage()); }
}
