package com.hzmwmt.domain.service.packages;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.packages.TAiRechargePack;
import java.math.BigDecimal;

public interface AdminAiRechargePackService extends IService<TAiRechargePack> {

    IPage<TAiRechargePack> pageWithCondition(Integer pageNum, Integer pageSize, String name);
    IPage<TAiRechargePack> pageWithCondition(Integer pageNum, Integer pageSize, String name, Integer status, BigDecimal minPrice, BigDecimal maxPrice);
}
