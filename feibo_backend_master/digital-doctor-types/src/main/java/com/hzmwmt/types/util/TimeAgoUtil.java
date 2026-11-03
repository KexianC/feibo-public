package com.hzmwmt.types.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TimeAgoUtil
 * @Date: 2025/9/11 13:48
 */
public class TimeAgoUtil {
    // 预定义常用的时间格式，
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 私有构造函数，防止被实例化
    private TimeAgoUtil() {
    }


    public static Date calculateEndTime(Date startTime, Integer durationDays) {
        if (durationDays == null || durationDays <= 0) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startTime);
        calendar.add(Calendar.DAY_OF_MONTH, durationDays);
        return calendar.getTime();
    }

    /**
     * 核心方法：格式化 LocalDateTime
     *
     * @param pastTime 过去的某个时间点
     * @return 人性化的时间描述字符串
     */
    public static String format(LocalDateTime pastTime) {
        if (pastTime == null) {
            return "未知时间";
        }

        LocalDateTime now = LocalDateTime.now();
        if (pastTime.isAfter(now)) {
            return "来自未来的时间";
        }

        // 计算秒、分钟、小时的差值
        Duration duration = Duration.between(pastTime, now);
        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return "刚刚";
        }

        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return minutes + "分钟前";
        }

        long hours = duration.toHours();
        if (hours < 24) {
            return hours + "小时前";
        }

        // 计算天、月、年的差值
        Period period = Period.between(pastTime.toLocalDate(), now.toLocalDate());

        if (period.getYears() == 0 && period.getMonths() == 0 && period.getDays() == 1) {
            // 判断是否是昨天
            if (now.toLocalDate().minusDays(1).equals(pastTime.toLocalDate())) {
                return "昨天 " + pastTime.format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        }

        if (period.getYears() == 0 && period.getMonths() == 0) {
            return period.getDays() + "天前";
        }

        if (period.getYears() == 0) {
            // 如果超过一个月但不足两个月，可以显示“一个月前”
            int totalMonths = period.getMonths();
            if (period.getDays() > 0 && totalMonths == 0) { // 例如1月31号和3月1号
                totalMonths++;
            }
            return totalMonths + "个月前";
        }

        // 超过一年
        int totalYears = period.getYears();
        if (period.getMonths() > 6) { // 如果超过半年，可以算作下一年
            totalYears++;
        }
        return totalYears + "年前";
    }

    /**
     * 便捷方法：格式化字符串形式的时间
     *
     * @param timeStr 时间字符串，格式应为 "yyyy-MM-dd HH:mm:ss"
     * @return 人性化的时间描述字符串
     */
    public static String format(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "未知时间";
        }
        try {
            LocalDateTime pastTime = LocalDateTime.parse(timeStr, DEFAULT_FORMATTER);
            return format(pastTime);
        } catch (Exception e) {
            // 如果格式解析失败，可以返回原始字符串或一个错误提示
            return timeStr;
        }
    }

    /**
     * 便捷方法：格式化旧的 java.util.Date 对象
     *
     * @param date Date 对象
     * @return 人性化的时间描述字符串
     */
    public static String format(Date date) {
        if (date == null) {
            return "未知时间";
        }
        LocalDateTime pastTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return format(pastTime);
    }
}
