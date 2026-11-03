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
 * @ClassName Copywriting
 * @Date: 2025/9/3 17:19
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_copywriting")
public class Copywriting {

    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 业务ID (雪花算法生成)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long copywritingId;

    /**
     * 作者用户业务ID
     */
    private Long userId;

    /**
     * 文案分类业务ID
     */
    private Long copywritingCategoryId;

    /**
     * 文案来源业务ID
     */
    private Long copywritingSourceId;

    /**
     * 文案标题
     */
    private String title;

    /**
     * 文案摘要
     */
    private String summary;

    /**
     * 文案详细内容
     */
    private String content;

    /**
     * 文案自身状态 (0:草稿, 1:已发布, 2:归档)
     */
    private Integer status;

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
