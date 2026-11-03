package com.hzmwmt.domain.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.user.TDigitalWork;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminDigitalWorkMapper extends BaseMapper<TDigitalWork> {

    /**
     * 获取总合成视频时间最长的用户排行
     *
     * @param topNum 前几名
     * @return 用户ID列表
     */
    @Select("""
            <script>SELECT
                u.nick_name,
                IFNULL(SUM(dw.duration), 0) AS total_duration
            FROM t_digital_work dw
                     LEFT JOIN t_user u ON dw.user_id = u.user_id
            <where>
              <if test='start != null'>dw.create_time &gt;= #{start}</if>
              <if test='end != null'>AND dw.create_time &lt; #{end}</if>
            </where>
            GROUP BY dw.user_id, u.nick_name
            ORDER BY total_duration DESC
            LIMIT #{topNum}</script>""")
    List<Map<String, Integer>> getTopUser(@Param("topNum") Integer topNum,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
