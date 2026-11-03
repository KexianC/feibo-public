package com.hzmwmt.domain.auth.model.aggregate;

import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.model.entity.RoleEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AuthUserAggregate
 * @Date: 2025/9/16 16:29
 */
@Builder
@Data
public class AuthUserAggregate {
    private AuthUserEntity authUserEntity;
    private List<RoleEntity> roleEntityList;
}
