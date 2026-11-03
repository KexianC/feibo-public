package com.hzmwmt.infrastructure.gateway.pay.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.infrastructure.gateway.pay.dto.AllInPayQueryRequestDTO;
import com.hzmwmt.infrastructure.gateway.pay.dto.AllInPayRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AllInPayRsaSignUtil
 * @Date: 2025/8/28 15:20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllInPaySecurityUtil {

    private final ObjectMapper objectMapper;

    public String generateSign(AllInPayRequestDTO requestDTO, String privateKeyPkcs8) {
        log.info("为[支付请求]DTO生成签名...");
        Map<String, Object> params = objectMapper.convertValue(requestDTO, new TypeReference<>() {
        });
        return generateSignFromMap(params, privateKeyPkcs8);
    }

    public String generateSign(AllInPayQueryRequestDTO queryDTO, String privateKeyPkcs8) {
        log.info("为[查询请求]DTO生成签名...");
        Map<String, Object> params = objectMapper.convertValue(queryDTO, new TypeReference<>() {
        });
        return generateSignFromMap(params, privateKeyPkcs8);
    }

    public Map<String, String> convertDtoToFormMap(Object dto) {
        Map<String, Object> objectMap = objectMapper.convertValue(dto, new TypeReference<>() {
        });
        return objectMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
    }

    private String generateSignFromMap(Map<String, Object> params, String privateKeyPkcs8) {
        if (privateKeyPkcs8 == null || privateKeyPkcs8.trim().isEmpty()) {
            throw new IllegalArgumentException("RSA私钥不能为空");
        }
        String stringToSign = buildCanonicalString(params);
        log.info("【通联支付】待签名参数已准备: parameterCount={}", params.size());

        try {
            PrivateKey privateKey = getPrivateKeyFromString(privateKeyPkcs8);
            Signature signature = Signature.getInstance("SHA1WithRSA");
            signature.initSign(privateKey);
            signature.update(stringToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signedBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signedBytes);
        } catch (Exception e) {
            log.error("RSA 签名生成失败: type={}", e.getClass().getSimpleName());
            throw new RuntimeException("RSA 签名生成失败", e);
        }
    }


    /**
     * 【验签】使用通联支付的公钥，验证一个通知 Map 的签名是否正确。
     *
     * @param notificationPayload 接收到的完整通知参数 Map
     * @param publicKeyStr        通联支付平台的 RSA 公钥字符串
     * @return boolean 验签是否通过
     */
    public boolean verifySign(Map<String, String> notificationPayload, String publicKeyStr) {
        String sign = notificationPayload.get("sign");
        if (sign == null || sign.isEmpty()) {
            log.warn("通知报文中没有签名(sign)字段，验签失败");
            return false;
        }

        try {
            // 1. 构建待验签字符串
            String stringToVerify = buildCanonicalString(notificationPayload);
            log.info("【通联支付】待验签参数已准备: parameterCount={}", notificationPayload.size());

            // 2. 执行 RSA 验签
            PublicKey publicKey = getPublicKeyFromString(publicKeyStr);
            Signature signature = Signature.getInstance("SHA1WithRSA");
            signature.initVerify(publicKey);
            signature.update(stringToVerify.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.error("RSA 验签时发生异常: type={}", e.getClass().getSimpleName());
            return false; // 发生任何异常都应视为验签失败
        }
    }

    /**
     * 【参数转换】将请求 DTO 转换为用于 FORM 表单提交的 Map<String, String>。
     *
     * @param requestDTO 待转换的DTO
     * @return 转换后的Map
     */
    public Map<String, String> convertDtoToFormMap(AllInPayRequestDTO requestDTO) {
        Map<String, Object> objectMap = objectMapper.convertValue(requestDTO, new TypeReference<>() {
        });
        // 过滤掉 value 为 null 的条目，并将所有 value 转换为 String
        return objectMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
    }

    /**
     * 【随机串生成】生成32位不含减号的随机字符串。
     *
     * @return 随机字符串
     */
    public String generateRandomStr() {
        return UUID.randomUUID().toString().replace("-", "");
    }


    /**
     * 【核心共享逻辑】构建规范化的待签名/验签字符串。
     */
    private String buildCanonicalString(Map<String, ?> params) {
        TreeMap<String, Object> sortedParams = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!"sign".equals(key) && value != null && !"".equals(value.toString())) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(key).append("=").append(value);
            }
        }
        return sb.toString();
    }

    /**
     * 将 PKCS#8 格式的私钥字符串转换为 PrivateKey 对象
     */
    private PrivateKey getPrivateKeyFromString(String privateKeyPkcs8) throws Exception {
        String privateKeyContent = privateKeyPkcs8.replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * 将 X.509 格式的公钥字符串转换为 PublicKey 对象
     */
    private PublicKey getPublicKeyFromString(String publicKeyStr) throws Exception {
        String publicKeyContent = publicKeyStr.replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
}
