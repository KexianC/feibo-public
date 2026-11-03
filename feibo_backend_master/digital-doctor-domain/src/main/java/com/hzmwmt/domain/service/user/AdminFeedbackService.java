package com.hzmwmt.domain.service.user;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.user.TFeedback;
import java.time.LocalDate;
public interface AdminFeedbackService extends IService<TFeedback> {
    IPage<TFeedback> pageQuery(Integer pageNum, Integer pageSize, Long userId, Long type, String keyword,
                              Integer status, Integer priority, LocalDate startDate, LocalDate endDate);
    TFeedback process(Long id, long version, Integer targetStatus, String reply, Integer priority, Long operatorId);
}
