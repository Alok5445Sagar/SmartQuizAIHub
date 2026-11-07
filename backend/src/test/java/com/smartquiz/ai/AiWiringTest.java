package com.smartquiz.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import com.smartquiz.BaseTest;

@DisplayName("AI wiring")
class AiWiringTest extends BaseTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Azure OpenAI is not on the classpath and no Azure beans are registered")
    void noAzureBeans() {
        assertThat(ClassUtils.isPresent("org.springframework.ai.azure.openai.AzureOpenAiChatModel", null)).isFalse();
        assertThat(Arrays.stream(context.getBeanDefinitionNames()))
                .noneMatch(name -> name.toLowerCase().contains("azure"));
    }

    @Test
    @DisplayName("Ollama integration is on the classpath")
    void ollamaPresent() {
        assertThat(ClassUtils.isPresent("org.springframework.ai.ollama.OllamaChatModel", null)).isTrue();
    }
}
