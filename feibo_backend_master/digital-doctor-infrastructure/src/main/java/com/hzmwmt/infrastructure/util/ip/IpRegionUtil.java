package com.hzmwmt.infrastructure.util.ip;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * IP地址转地理位置工具类
 * 基于ip2region实现，纯本地、高性能、免费
 */
@Slf4j
public class IpRegionUtil {

    private static final Object lock = new Object();
    private static Searcher searcher = null;

    static {
        initSearcher();
    }

    /**
     * 初始化Searcher
     */
    private static void initSearcher() {
        try {
            // 从classpath加载ip2region_v4.xdb文件
            ClassPathResource resource = new ClassPathResource("ip2region_v4.xdb");
            InputStream inputStream = resource.getInputStream();
            byte[] cBuff = inputStream.readAllBytes();
            searcher = Searcher.newWithBuffer(cBuff);
            log.info("ip2region初始化成功");
        } catch (Exception e) {
            log.error("ip2region初始化失败: type={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 根据IP地址获取地理位置信息
     *
     * @param ip IP地址
     * @return 地理位置信息，格式：国家|区域|省份|城市|ISP
     */
    public static String getRegion(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "未知地区";
        }

        // 处理本地回环地址
        if ("127.0.0.1".equals(ip) || "localhost".equals(ip)) {
            return "本地回环地址";
        }

        // 处理内网IP地址
        if (isInternalIp(ip)) {
            return "内网IP";
        }

        try {
            // 确保searcher已初始化
            if (searcher == null) {
                synchronized (lock) {
                    if (searcher == null) {
                        initSearcher();
                    }
                }
            }

            if (searcher == null) {
                log.warn("searcher未初始化，无法查询IP地理位置");
                return "未知地区";
            }

            long startTime = System.nanoTime();
            String region = searcher.search(ip);
            long cost = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startTime);
            log.debug("IP地理位置查询完成: costMicros={}, found={}",
                    cost, region != null && !region.isEmpty());

            // 处理查询结果
            if (region == null || region.isEmpty() || "内网IP".equals(region)) {
                return "未知地区";
            }

            return formatRegion(region);
        } catch (Exception e) {
            log.error("查询IP地理位置失败: type={}", e.getClass().getSimpleName());
            return "未知地区";
        }
    }

    /**
     * 格式化地区信息，只保留省份和城市
     *
     * @param region 原始地区信息
     * @return 格式化后的地区信息
     */
    private static String formatRegion(String region) {
        if (region == null || region.isEmpty()) {
            return "未知地区";
        }

        // 分割地区信息
        String[] parts = region.split("\\|");
        if (parts.length < 4) {
            return region;
        }

        // 提取省份和城市
        String country = parts[0];
        String area = parts[1];
        String province = parts[2];
        String city = parts[3];

        // 处理特殊情况
        if ("0".equals(country)) {
            country = "";
        }
        if ("0".equals(area)) {
            area = "";
        }
        if ("0".equals(province)) {
            province = "";
        }
        if ("0".equals(city)) {
            city = "";
        }

        // 构建返回结果
        StringBuilder result = new StringBuilder();
        if (!country.isEmpty() && !"中国".equals(country)) {
            result.append(country);
        }
        if (!province.isEmpty() && !"0".equals(province)) {
            if (result.length() > 0) {
                result.append("-");
            }
            result.append(province);
        }
        if (!city.isEmpty() && !"0".equals(city)) {
            if (result.length() > 0) {
                result.append("-");
            }
            result.append(city);
        }

        return result.length() > 0 ? result.toString() : "未知地区";
    }

    /**
     * 判断是否为内网IP
     *
     * @param ip IP地址
     * @return 是否为内网IP
     */
    private static boolean isInternalIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return false;
            }

            int[] ipParts = new int[4];
            for (int i = 0; i < 4; i++) {
                ipParts[i] = Integer.parseInt(parts[i]);
            }

            // 10.0.0.0/8
            if (ipParts[0] == 10) {
                return true;
            }

            // 172.16.0.0/12
            if (ipParts[0] == 172 && ipParts[1] >= 16 && ipParts[1] <= 31) {
                return true;
            }

            // 192.168.0.0/16
            if (ipParts[0] == 192 && ipParts[1] == 168) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 关闭资源
     */
    public static void close() {
        if (searcher != null) {
            try {
                searcher.close();
                log.info("ip2region searcher已关闭");
            } catch (IOException e) {
                log.error("关闭ip2region searcher失败: type={}", e.getClass().getSimpleName());
            }
        }
    }
}
