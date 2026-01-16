package com.build4all.ai.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;


@Service
@ConditionalOnProperty(name = "gemini.api.key")
public class GeminiAiProviderService implements AiProviderService {
    private final Client client;
    private final String model;

    public GeminiAiProviderService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.model:gemini-1.5-flash}") String model
    ) {
        this.client = Client.builder().apiKey(apiKey).build();
        this.model = model;
    }

    @Override
    public String ask(String prompt) {
        GenerateContentResponse res = client.models.generateContent(model, prompt, null);
        String text = res.text();
        return (text == null || text.isBlank()) ? "No answer generated." : text.trim();
    }
}

