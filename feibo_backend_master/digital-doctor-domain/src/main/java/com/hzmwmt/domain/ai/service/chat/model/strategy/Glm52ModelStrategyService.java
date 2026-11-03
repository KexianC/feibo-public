package com.hzmwmt.domain.ai.service.chat.model.strategy;

import org.springframework.stereotype.Service;

/**
 * Zhipu GLM models use the standard OpenAI-compatible streaming chat flow.
 * The service name is a stable routing code retained for configuration compatibility.
 */
@Service("glm52")
public class Glm52ModelStrategyService extends QWenModelStrategyService {
}
