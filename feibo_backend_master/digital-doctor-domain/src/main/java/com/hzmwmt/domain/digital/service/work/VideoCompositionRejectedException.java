package com.hzmwmt.domain.digital.service.work;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;

/** The provider explicitly rejected submission; unlike a timeout it is safe to release the hold. */
public class VideoCompositionRejectedException extends AppException {
    public VideoCompositionRejectedException() {
        super(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(), "数字人供应商拒绝合成，额度已退回");
    }
}
