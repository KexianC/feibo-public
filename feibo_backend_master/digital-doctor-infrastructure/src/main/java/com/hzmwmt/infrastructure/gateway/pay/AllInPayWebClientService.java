package com.hzmwmt.infrastructure.gateway.pay;

import com.hzmwmt.infrastructure.gateway.pay.dto.AllInPayQueryResponseDTO;
import com.hzmwmt.infrastructure.gateway.pay.dto.AllInPayResponseDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AllInPayWebClientService
 * @Date: 2025/9/23 20:24
 */
@Slf4j
@Service
public class AllInPayWebClientService implements IAllInPayApiService {

    @Qualifier("allInPayWebClient")
    @Resource
    private WebClient allInPayWebClient;

    @Override
    public Mono<AllInPayResponseDTO> unitOrderPay(Map<String, String> request) {
        log.info("发起通联支付统一下单请求");

        // 将 Map 转换为 MultiValueMap
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        request.forEach(formData::add);

        return allInPayWebClient
                .post()
                .uri("/unitorder/pay")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(AllInPayResponseDTO.class)
                .doOnSuccess(response -> log.info("通联支付统一下单完成，收到响应: {}", response != null))
                .doOnError(error -> log.error("通联支付统一下单失败，异常类型: {}",
                        error == null ? "unknown" : error.getClass().getSimpleName()));
    }

    @Override
    public Mono<AllInPayQueryResponseDTO> queryTransaction(Map<String, String> fields) {
        log.info("发起通联支付交易查询请求");

        // 将 Map 转换为 MultiValueMap
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        fields.forEach(formData::add);

        return allInPayWebClient
                .post()
                .uri("/tranx/query")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(AllInPayQueryResponseDTO.class)
                .doOnSuccess(response -> log.info("通联支付交易查询完成，收到响应: {}", response != null))
                .doOnError(error -> log.error("通联支付交易查询失败，异常类型: {}",
                        error == null ? "unknown" : error.getClass().getSimpleName()));
    }
}
