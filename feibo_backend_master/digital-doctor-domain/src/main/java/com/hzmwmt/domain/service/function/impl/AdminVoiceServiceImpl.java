package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.function.TDigitalVoice;
import com.hzmwmt.domain.mapper.function.AdminVoiceMapper;
import com.hzmwmt.domain.service.function.AdminVoiceService;
import com.hzmwmt.types.annotations.OperationLog;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 语音业务逻辑实现
 */
@Service
public class AdminVoiceServiceImpl extends ServiceImpl<AdminVoiceMapper, TDigitalVoice> implements AdminVoiceService {

    @Override
    @OperationLog(operationType = "查询", description = "分页查询语音列表", recordParams = true)
    public IPage<TDigitalVoice> pageQuery(Integer pageNum, Integer pageSize, Map<String, Object> params) {
        Page<TDigitalVoice> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TDigitalVoice> wrapper = new LambdaQueryWrapper<>();

        // 名称模糊查询
        String name = (String) params.get("name");
        if (StringUtils.hasText(name)) {
            wrapper.like(TDigitalVoice::getName, name);
        }

        // 性别精确查询（排除“全部性别”默认值）
        String gender = (String) params.get("gender");
        if (StringUtils.hasText(gender) && !"全部性别".equals(gender)) {
            wrapper.eq(TDigitalVoice::getGender, gender);
        }

        // 语种查询（示例：若页面有语种筛选，需数据库表有对应字段）
        String language = (String) params.get("language");
        if (StringUtils.hasText(language) && !"请选择".equals(language)) {
            // wrapper.eq(VoiceEntity::getLanguage, language); // 需实体类添加language字段
        }

        return this.page(page, wrapper);
    }

    @Override
    public boolean saveVoice(TDigitalVoice voice) {
        return this.save(voice);
    }

    @Override
    public boolean updateVoice(TDigitalVoice voice) {
        return this.updateById(voice);
    }

    @Override
    @OperationLog(operationType = "删除", description = "删除语音", recordParams = true)
    public boolean removeVoice(Long id) {
        return this.removeById(id);
    }

    @Override
    @OperationLog(operationType = "查询", description = "根据ID查询语音详情", recordParams = true)
    public TDigitalVoice getVoiceById(Long id) {
        return this.getById(id);
    }

    @Override
    public boolean addStyle(Long voiceId, Map<String, Object> styleInfo) {
        // 示例：实际需插入"语音-风格"中间表（如 t_voice_style）
        // 这里简化为"模拟成功"，实际需根据业务完善
        return true;
    }
}
