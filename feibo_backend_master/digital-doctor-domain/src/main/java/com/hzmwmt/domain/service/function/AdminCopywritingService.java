package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TCopywriting;
import com.hzmwmt.domain.vo.AdminCopywritingVO;
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
public interface AdminCopywritingService extends IService<TCopywriting> {

    /**
     * 分页查询文案（支持标题、分类筛选）
     */
    IPage<AdminCopywritingVO> pageCopywritings(Integer pageNum, Integer pageSize, String title, Long categoryId);

    /**
     * 分页查询待专家组专业审核的文案（current_status=1）
     */
    IPage<AdminCopywritingVO> pageExpertApprovalCopywritings(Integer pageNum, Integer pageSize, String title, Long categoryId);

    /**
     * 分页查询所属医院/机构待终审的文案（current_status=3）
     */
    IPage<AdminCopywritingVO> pagePlatformApprovalCopywritings(Integer pageNum, Integer pageSize, String title, Long categoryId);

    /**
     * 分页查询机构终审通过的文案（current_status=4）
     */
    IPage<AdminCopywritingVO> pagePlatformApprovedCopywritings(Integer pageNum, Integer pageSize, String title, Long categoryId);

    /**
     * 查询分类名称（辅助前端展示）
     */
    String getCategoryName(Long categoryId);

    /**
     * 根据ID查询文案VO
     */
    AdminCopywritingVO getById(Long id);

    /**
     * Update a copywriting record only when it has no approval flow. Reviewed or
     * in-flight content must be changed through a dedicated resubmission workflow.
     */
    boolean updateCopywritingSafely(TCopywriting copywriting);

    /**
     * Delete a copywriting record only when it has no approval flow.
     */
    boolean removeCopywritingSafely(Long id);

    /** Create a draft while ignoring client supplied identifiers and workflow state. */
    boolean createCopywritingSafely(TCopywriting copywriting);
}
