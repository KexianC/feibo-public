package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TDigitalVoice;

import java.util.Map;

/**
 * 语音业务逻辑接口
 */
public interface AdminVoiceService extends IService<TDigitalVoice> {

    /**
     * 分页查询语音（支持名称、性别、语种筛选）
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param params   筛选参数（name、gender、language等）
     * @return 分页结果
     */
    IPage<TDigitalVoice> pageQuery(Integer pageNum, Integer pageSize, Map<String, Object> params);

    /**
     * 新增语音
     */
    boolean saveVoice(TDigitalVoice voice);

    /**
     * 更新语音
     */
    boolean updateVoice(TDigitalVoice voice);

    /**
     * 删除语音
     */
    boolean removeVoice(Long id);

    /**
     * 根据ID查询语音
     */
    TDigitalVoice getVoiceById(Long id);

    /**
     * 为语音添加风格（示例：实际需关联“语音-风格”中间表）
     *
     * @param voiceId   语音ID
     * @param styleInfo 风格信息
     * @return 是否成功
     */
    boolean addStyle(Long voiceId, Map<String, Object> styleInfo);
}