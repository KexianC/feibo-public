package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.vo.AdminCopywritingExpertAssignmentVO;
import com.hzmwmt.domain.vo.AdminReviewAccountOptionVO;
import com.hzmwmt.domain.vo.AdminReviewExpertOptionVO;

import java.util.List;

public interface AdminCopywritingExpertAssignmentService {

    List<AdminCopywritingExpertAssignmentVO> queryAssignments(List<Long> accountUserIds);

    IPage<AdminReviewAccountOptionVO> queryAccounts(Integer page, Integer size, String keyword);

    List<AdminReviewExpertOptionVO> queryExpertOptions(String keyword);

    List<AdminCopywritingExpertAssignmentVO> replaceAssignments(
            Long accountUserId,
            List<Long> expertUserIds);
}
