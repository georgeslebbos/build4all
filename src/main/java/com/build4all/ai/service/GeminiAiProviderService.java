package com.build4all.ai.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.*;

@Service
@ConditionalOnProperty(name = "gemini.api.key")
public class GeminiAiProviderService implements AiProviderService {

    private final Client client;
    private final String model;

    // ✅ provider timeout (keep < your controller/service timeout)
    private final Duration timeout;

    // ✅ isolate AI calls so they don't block Tomcat threads
    private final ExecutorService pool;

    public GeminiAiProviderService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.model:gemini-1.5-flash}") String model,
            @Value("${gemini.timeoutSeconds:40}") long timeoutSeconds,
            @Value("${gemini.poolSize:6}") int poolSize
    ) {
        this.client = Client.builder().apiKey(apiKey).build();
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.pool = Executors.newFixedThreadPool(Math.max(2, poolSize));
        
        String masked = apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length()-4);
        System.out.println("✅ Gemini key loaded: " + masked);
        System.out.println("✅ Gemini model: " + model);
    }
    
    

    @Override
    public String ask(String prompt) {

        Future<String> f = pool.submit(() -> {
            GenerateContentResponse res = client.models.generateContent(model, prompt, null);
            String text = res.text();
            return (text == null || text.isBlank()) ? "No answer generated." : text.trim();
        });

        try {
            return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

        } catch (TimeoutException te) {
            f.cancel(true);
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI provider took too long. Try again."
            );

        } catch (ExecutionException ee) {
            Throwable root = (ee.getCause() != null) ? ee.getCause() : ee;
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI provider failed: " + root.getMessage()
            );

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI call interrupted"
            );
        }
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}