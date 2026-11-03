package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.AIRechargePack;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIRechargePackMapper
 * @Date: 2025/8/26 15:58
 */
public interface AIRechargePackMapper extends BaseMapper<AIRechargePack> {

    List<AIRechargePack> selectValidAiRechargePack();

    AIRechargePack selectValidAiRechargePackByAiRechargePackId(@Param("aiRechargePackId") Long aiRechargePackId);

    List<AIRechargePack> selectAllAiRechargePack();
}
