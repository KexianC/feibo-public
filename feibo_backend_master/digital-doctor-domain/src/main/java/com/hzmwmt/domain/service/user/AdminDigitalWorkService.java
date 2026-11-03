package com.hzmwmt.domain.service.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.user.TDigitalWork;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数字作品 Service 接口
 * 继承 IService，复用 MP 通用业务方法
 */
public interface AdminDigitalWorkService extends IService<TDigitalWork> {

    /**
     * 带条件的分页查询
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param keyword  搜索关键字（标题模糊匹配）
     * @return 分页结果
     */
    IPage<TDigitalWork> pageQuery(Integer pageNum, Integer pageSize, String keyword);
    IPage<Map<String, Object>> pageAuditQuery(Integer pageNum, Integer pageSize, String keyword);
    Map<String, Object> getAuditStatistics(LocalDate startDate, LocalDate endDate);


    /**
     * 根据ID删除单个数字作品
     *
     * @param id 作品ID（数据库主键id）
     * @return 是否删除成功
     */
    boolean deleteById(Integer id);

    /**
     * 批量删除数字作品
     *
     * @param ids 作品ID列表（数据库主键id集合）
     * @return 是否删除成功（true=全部删除成功/false=部分或全部失败）
     */
    boolean deleteBatch(List<Integer> ids);

    /**
     * 获取总合成视频时间最长的用户排行
     *
     * @param topNum 前几名，默认10
     * @return 用户列表
     */
    List<Map<String, Integer>> getTopUser(Integer topNum, LocalDate startDate, LocalDate endDate);
}
