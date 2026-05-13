package com.paata.telegram_expense_bot.groq.service;

import com.paata.telegram_expense_bot.model.dto.IntentResponse;
import com.paata.telegram_expense_bot.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с Groq API.
 */
@Service
@RequiredArgsConstructor
public class GroqService {

    private final PromptLoader promptLoader;
    /**
     * Groq API key.
     */
    @Value("${groq.api-key}")
    private String apiKey;
    private final ObjectMapper objectMapper=new ObjectMapper();

    /**
     * HTTP client.
     */
    private final WebClient webClient;
    /**
     * Определяет intent пользователя.
     *
     * @param text сообщение пользователя
     * @return определенный intent
     */
    public IntentResponse detectIntent(String text) {

        try {

            String systemPrompt =
                    promptLoader.loadPrompt(
                            "prompts/intent-classifier.txt"
                    );

            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", systemPrompt
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", text
                            )
                    )
            );

            Map response = webClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List choices =
                    (List) response.get("choices");

            Map firstChoice =
                    (Map) choices.getFirst();

            Map message =
                    (Map) firstChoice.get("message");

            String content =
                    (String) message.get("content");

            return objectMapper.readValue(
                    content,
                    IntentResponse.class
            );

        } catch (Exception e) {

            return new IntentResponse();
        }
    }
    /**
     * Отправляет запрос в LLM.
     *
     * @param userMessage сообщение пользователя
     * @return ответ модели
     */
    public String ask(String userMessage) {
        try {
            String systemPrompt =
                    promptLoader.loadPrompt(
                            "prompts/expense-extractor.txt"
                    );
            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", systemPrompt
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", userMessage
                            )
                    )
            );
            Map response = webClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            List choices = (List) response.get("choices");
            Map firstChoice = (Map) choices.getFirst();
            Map message = (Map) firstChoice.get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            e.printStackTrace();
            return "Groq API error: " + e.getMessage();
        }
    }
}