package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TAdminDigitalModel;
import com.hzmwmt.domain.vo.DigitalModelVO;

import java.util.List;

/**
 * 数字模型服务接口
 *
 * @author AI Assistant
 */
public interface DigitalModelService extends IService<TAdminDigitalModel> {

    /**
     * 查询所有数字模型信息
     *
     * @return 数字模型VO列表
     */
    List<DigitalModelVO> queryAllDigitalModels();

    /**
     * 根据用户ID查询绑定的数字模型列表
     *
     * @param userId 用户ID
     * @return 用户绑定的数字模型VO列表
     */
    List<DigitalModelVO> queryUserBoundModels(Long userId);

    /**
     * 修改用户和数字模型的绑定关系
     * 逻辑：先删除用户的全部绑定关系，然后添加新的绑定关系
     *
     * @param userId   用户ID
     * @param modelIds 要绑定的数字模型ID列表
     * @return 是否修改成功
     */
    boolean updateUserModelBindings(Long userId, List<Long> modelIds);
}