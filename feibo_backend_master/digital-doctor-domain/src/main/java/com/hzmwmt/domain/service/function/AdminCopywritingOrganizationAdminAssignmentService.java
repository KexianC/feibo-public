package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.vo.AdminCopywritingOrganizationAdminAssignmentVO;
import com.hzmwmt.domain.vo.AdminReviewAccountOptionVO;
import com.hzmwmt.domain.vo.AdminReviewOrganizationAdminOptionVO;

import java.util.List;

public interface AdminCopywritingOrganizationAdminAssignmentService {

    IPage<AdminReviewAccountOptionVO> queryAccounts(Integer page, Integer size, String keyword);

    List<AdminCopywritingOrganizationAdminAssignmentVO> queryAssignments(List<Long> accountUserIds);

    List<AdminReviewOrganizationAdminOptionVO> queryAdminOptions(String keyword);

    List<AdminCopywritingOrganizationAdminAssignmentVO> replaceAssignments(
            Long accountUserId,
            List<Long> adminUserIds);
}
