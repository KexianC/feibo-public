package com.hzmwmt.domain.ai.service.chat.model.strategy;

import org.springframework.stereotype.Service;

/**
 * HY3 follows the same OpenAI-compatible chat flow as qwen.
 */
@Service("hy3")
public class Hy3ModelStrategyService extends QWenModelStrategyService {
}
