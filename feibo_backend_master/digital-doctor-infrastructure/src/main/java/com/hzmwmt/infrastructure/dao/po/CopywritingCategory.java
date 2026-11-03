package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingCategory
 * @Date: 2025/9/3 17:17
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_copywriting_category")
public class CopywritingCategory {

    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 业务ID (雪花算法生成)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long copywritingCategoryId;

    /**
     * 分类名
     */
    private String name;

    /**
     * 状态 (0:未激活, 1:激活)
     */
    private Boolean status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
