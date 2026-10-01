package com.hackathonstarter.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathonstarter.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmConfig {

    @Bean
    LlmClient llmClient(AppProperties props, RestClient.Builder restClientBuilder, ObjectMapper mapper,
                        LlmCallLogRepository repository) {
        AppProperties.Llm llm = props.llm();
        LlmClient delegate = llm.isMock()
                ? new MockLlmClient(mapper, llm.model())
                : new AnthropicClaudeClient(llm, restClientBuilder, mapper);
        return new LoggingLlmClient(delegate, repository, llm.model());
    }
}
