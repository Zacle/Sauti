package com.sauti.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "sauti.llm.provider=spring-ai",
        "sauti.llm.default-model=gemini-3.1-flash-lite",
        "sauti.llm.advanced-model=gpt-5.4-mini",
        "spring.ai.google.genai.api-key=test-google-key",
        "spring.ai.openai.api-key=test-openai-key"
})
class SpringAiToolCallingLlmProviderContextTest {

    @Autowired
    private LlmToolCallingProvider provider;

    @Test
    void loadsTieredSpringAiProvider() {
        assertThat(provider).isInstanceOf(SpringAiToolCallingLlmProvider.class);
    }
}
