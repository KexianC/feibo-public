package com.hzmwmt.domain.user.service.manager;

import com.hzmwmt.domain.auth.adapter.port.IAuthPort;
import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import com.hzmwmt.domain.service.user.AdminUserService;
import com.hzmwmt.domain.user.model.entity.*;
import com.hzmwmt.domain.user.service.IUserManagerService;
import com.hzmwmt.types.enums.GenderEnumVO;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserManagerServiceImpl implements IUserManagerService {

    // 头像在OSS中的存储根路径（可根据业务调整）
    private static final String OSS_AVATAR_ROOT_PATH = "avatar/";
    // 1. 定义默认头像URL常量（与你的默认头像地址完全一致）
    private static final String DEFAULT_AVATAR_URL = "https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/avatar/default.jpg";
    @Resource(name = "hybridOSSGateway")
    private FileStorageGateway fileStorageGateway;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private IAuthPort authPort;

    @Override
    public void sendUpdateShortMessage(String phoneNumber) {
        authPort.sendAuthShortMessage(phoneNumber);
    }

    @Override
    public void updatePassword(UpdatePasswordCommandEntity updatePasswordCommandEntity) {
        adminUserService.updatePassword(updatePasswordCommandEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNickName(UpdateNickNameCommandEntity command) {
        // 1. 参数校验
        Long userId = command.getUserId();
        String newNickName = command.getNewNickName();

        // 校验用户ID非空
        if (userId == null) {
            throw new AppException("USER_ID_NULL", "用户ID不能为空");
        }
        // 校验新昵称非空且长度合理（根据业务需求调整长度限制）
        if (!StringUtils.hasText(newNickName)) {
            throw new AppException("NICK_NAME_NULL", "新昵称不能为空");
        }
        if (newNickName.length() > 50) { // 假设最大长度50
            throw new AppException("NICK_NAME_TOO_LONG", "昵称长度不能超过50个字符");
        }

        try {
            // 2. 根据业务userId查询用户
            Optional<TUser> userOptional = adminUserService.getUserByBusinessUserId(userId);
            if (userOptional.isEmpty()) {
                throw new AppException("USER_NOT_FOUND", "用户不存在，无法更新昵称");
            }
            TUser user = userOptional.get();
            log.info("查询到待更新昵称的用户：id={}, userId={}",
                    user.getId(), user.getUserId());

            // 3. 若昵称未变化，直接返回（避免无效更新）
            if (newNickName.equals(user.getNickName())) {
                log.info("用户[{}]新昵称与旧昵称一致，无需更新", userId);
                return;
            }

            // 4. 设置新昵称和更新时间
            user.setNickName(newNickName);
            user.setUpdateTime(LocalDateTime.now()); // 手动更新时间（因updateTime无自动填充）

            // 5. 调用MP更新方法（根据业务userId更新，复用之前的updateByUserId）
            boolean updateSuccess = adminUserService.updateByUserId(userId, user);
            if (!updateSuccess) {
                throw new AppException("DB_UPDATE_FAILED", "数据库更新失败，请重试");
            }

            log.info("用户[{}]昵称更新成功", userId);

        } catch (AppException e) {
            log.error("用户[{}]昵称更新业务异常: code={}, type={}",
                    userId, e.getCode(), e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            log.error("用户[{}]昵称更新系统异常: type={}", userId, e.getClass().getSimpleName());
            throw new AppException("SYSTEM_ERROR", "系统异常，昵称更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatar(UpdateAvatarCommandEntity command) {
        // 1. 参数校验
        Long userId = command.getUserId();
        MultipartFile avatarFile = command.getAvatar();

        if (userId == null) {
            throw new AppException("USER_ID_NULL", "用户ID不能为空");
        }
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new AppException("AVATAR_FILE_NULL", "请上传头像文件");
        }

        // 2. 生成OSS存储路径
        String fileExtension = StringUtils.getFilenameExtension(avatarFile.getOriginalFilename());
        if (!isValidImageExtension(fileExtension)) {
            throw new AppException("INVALID_IMAGE_FORMAT", "仅支持jpg、jpeg、png格式的图片");
        }
        String dateDir = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String uniqueFileName = UUID.randomUUID().toString() + "." + fileExtension;
        String ossObjectKey = OSS_AVATAR_ROOT_PATH + userId + "/" + dateDir + "/" + uniqueFileName;

        try {
            // 3. 上传新头像到OSS
            UploadDTO uploadDTO = new UploadDTO();
            uploadDTO.setFile(avatarFile);
            uploadDTO.setPath(ossObjectKey);
            String newAvatarUrl = fileStorageGateway.upload(uploadDTO);
            log.info("用户[{}]新头像OSS上传成功", userId);

            // 4. 查询用户（利用AdminUserService的getUserById）
            Optional<TUser> userOptional = adminUserService.getUserByBusinessUserId(userId);
            if (userOptional.isEmpty()) {
                // 抛出的异常信息保持纯文本，不要加多余格式
                throw new AppException("USER_NOT_FOUND", "用户不存在，无法更新头像");
            }
            TUser user = userOptional.get();
            log.info("查询到的用户信息：id={}, userId={}", user.getId(), user.getUserId()); // 新增日志
            String oldAvatarUrl = user.getAvatar(); // 记录旧头像URL

            // 5. 更新用户头像（修正后：传递 user 实体 + businessUserId）
            user.setAvatar(newAvatarUrl);
// 手动设置 updateTime（彻底绕过自动填充，避免之前的问题）
            user.setUpdateTime(LocalDateTime.now());
// 关键修改：调用时传两个参数（业务userId + 带新头像的user实体）
            boolean updateSuccess = adminUserService.updateByUserId(userId, user);
            if (!updateSuccess) {
                throw new AppException("DB_UPDATE_FAILED", "数据库更新失败（无匹配记录或字段未变更）");
            }

            // 7. 清理旧头像（修正后）
            if (StringUtils.hasText(oldAvatarUrl)) {
                // 判断是否为默认头像
                if (DEFAULT_AVATAR_URL.equals(oldAvatarUrl)) {
                    log.info("用户[{}]旧头像是默认头像，无需删除OSS文件", userId);
                    // 直接跳过后续删除逻辑（不用写continue）
                } else {
                    // 非默认头像：修复URL格式并删除
                    if (!oldAvatarUrl.startsWith("http://") && !oldAvatarUrl.startsWith("https://")) {
                        oldAvatarUrl = "https://" + oldAvatarUrl;
                        log.warn("用户[{}]旧头像URL格式不完整，已修复", userId);
                    }
                    // 执行OSS删除
                    fileStorageGateway.delete(oldAvatarUrl);
                    log.info("用户[{}]旧头像（非默认）清理成功", userId);
                }
            }

        } catch (AppException e) {
            log.error("用户[{}]头像更新业务异常: code={}, type={}",
                    userId, e.getCode(), e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            log.error("用户[{}]头像更新系统异常: type={}", userId, e.getClass().getSimpleName());
            throw new AppException("SYSTEM_ERROR", "系统异常，头像更新失败");
        }
    }

    @Override
    public void updateSpecialty(UpdateSpecialtyCommandEntity command) {
        // 1. 参数校验
        Long userId = command.getUserId();
        String specialty = command.getSpecialty();

        // 校验用户ID非空
        if (userId == null) {
            throw new AppException("USER_ID_NULL", "用户ID不能为空");
        }
        // 校验新专业非空且长度合理（根据业务需求调整长度限制）
        if (!StringUtils.hasText(specialty)) {
            throw new AppException("SPECIALTY_NULL", "专业不能为空");
        }
        if (specialty.length() > 10) { // 假设最大长10
            throw new AppException("SPECIALTY_TOO_LONG", "专业长度不能超过10个字符");
        }

        try {
            // 2. 根据业务userId查询用户
            Optional<TUser> userOptional = adminUserService.getUserByBusinessUserId(userId);
            if (userOptional.isEmpty()) {
                throw new AppException("USER_NOT_FOUND", "用户不存在，无法更新专业");
            }
            TUser user = userOptional.get();
            log.info("查询到待更新专业的用户：id={}, userId={}",
                    user.getId(), user.getUserId());

            // 3. 若专业未变化，直接返回（避免无效更新）
            if (specialty.equals(user.getSpecialty())) {
                log.info("用户[{}]新专业与旧专业一致，无需更新", userId);
                return;
            }

            // 4. 设置新专业和更新时间
            user.setSpecialty(specialty);
            user.setUpdateTime(LocalDateTime.now()); // 手动更新时间（因updateTime无自动填充）

            // 5. 调用MP更新方法（根据业务userId更新，复用之前的updateByUserId）
            boolean updateSuccess = adminUserService.updateByUserId(userId, user);
            if (!updateSuccess) {
                throw new AppException("DB_UPDATE_FAILED", "数据库更新失败，请重试");
            }

            log.info("用户[{}]专业更新成功", userId);

        } catch (AppException e) {
            log.error("用户[{}]专业更新业务异常: code={}, type={}",
                    userId, e.getCode(), e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            log.error("用户[{}]专业更新系统异常: type={}", userId, e.getClass().getSimpleName());
            throw new AppException("SYSTEM_ERROR", "系统异常，专业更新失败");
        }
    }

    /**
     * 验证图片文件扩展名
     */
    private boolean isValidImageExtension(String extension) {
        if (extension == null) {
            return false;
        }
        String lowerExt = extension.toLowerCase();
        return "jpg".equals(lowerExt) || "jpeg".equals(lowerExt) || "png".equals(lowerExt);
    }

    @Override
    public UserEntity getUserById(Long userId) {
        // 参数校验
        if (userId == null) {
            throw new AppException("USER_ID_NULL", "用户ID不能为空");
        }

        try {
            // 查询用户信息
            Optional<TUser> userOptional = adminUserService.getUserByBusinessUserId(userId);
            if (userOptional.isEmpty()) {
                throw new AppException("USER_NOT_FOUND", "用户不存在");
            }

            TUser user = userOptional.get();

            // 转换为UserEntity对象
            UserEntity userEntity = UserEntity.builder()
                    .userId(user.getUserId())
                    .nickName(user.getNickName())
                    .avatar(user.getAvatar())
                    .phoneNumber(user.getPhoneNumber())
                    .genderEnumVO(GenderEnumVO.fromCode(user.getGender()))
                    .build();

            return userEntity;
        } catch (AppException e) {
            log.error("获取用户信息业务异常：userId={}, code={}, type={}",
                    userId, e.getCode(), e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            log.error("获取用户信息系统异常：userId={}, type={}", userId, e.getClass().getSimpleName());
            throw new AppException("SYSTEM_ERROR", "系统异常，获取用户信息失败");
        }
    }

    @Override
    public Boolean queryIsSetSpecialty(Long userId) {
        // 参数校验
        if (userId == null) {
            throw new AppException("USER_ID_NULL", "用户ID不能为空");
        }

        try {
            // 查询用户信息
            Optional<TUser> userOptional = adminUserService.getUserByBusinessUserId(userId);
            if (userOptional.isEmpty()) {
                throw new AppException("USER_NOT_FOUND", "用户不存在");
            }
            return userOptional.get().getSpecialty() != null;
        } catch (AppException e) {
            log.error("获取用户信息业务异常：userId={}, code={}, type={}",
                    userId, e.getCode(), e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            log.error("获取用户信息系统异常：userId={}, type={}", userId, e.getClass().getSimpleName());
            throw new AppException("SYSTEM_ERROR", "系统异常，获取用户信息失败");
        }
    }
}


