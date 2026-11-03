package com.hzmwmt.domain.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.dto.AdminUserUpdateDTO;
import com.hzmwmt.domain.dto.ChangePasswordByOldRequestDTO;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.user.model.entity.UpdatePasswordCommandEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AdminUserService extends IService<TUser> {

    /**
     * 根据name的到id
     */
    List<Long> getIdByName(String name);

    List<Map<Long, String>> getNameByIds(List<Long> userIds);

    /**
     * 根据ID查询用户（用于修改前的信息回显）
     *
     * @param id 用户主键ID
     * @return 用户实体（Optional包装，避免null）
     */
    Optional<TUser> getUserById(Long id);

    Optional<TUser> getUserByBusinessUserId(Long businessUserId);

    /**
     * 根据用户名模糊查询用户ID列表
     */
    List<Long> getUserIdsByUsernameLike(String username);

    /**
     * 更新用户信息
     *
     * @param id        用户主键ID（路径参数，确保修改的是指定用户）
     * @param updateDTO 前端传递的修改参数
     * @return 操作结果（true=成功，false=失败）
     */
    boolean updateUser(Long id, AdminUserUpdateDTO updateDTO);

    /**
     * 启用或禁用用户
     *
     * @param id 用户主键ID
     * @return 操作结果（true=成功，false=失败）
     */
    boolean setUserDisabled(Long id, boolean disabled);

    boolean updateByUserId(Long businessUserId, TUser user);

    void updatePassword(UpdatePasswordCommandEntity command);

    void changePasswordByOld(Long userId, ChangePasswordByOldRequestDTO dto);

    /**
     * 创建新用户
     *
     * @param user   用户实体
     * @param roleId 角色ID
     * @return 创建的用户实体
     */
    TUser createUser(TUser user, Long roleId);

    /**
     * 通过Excel批量创建用户
     *
     * @param file
     * @return 错误日志
     * @throws IOException
     */
    List<String> createUserByExcel(MultipartFile file) throws IOException;

    String getSpecialtyByUserId(Long userId);

//    boolean updateByPhoneNumber(String PhoneNumber, TUser user);
}
