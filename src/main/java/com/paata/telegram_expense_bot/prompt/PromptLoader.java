package com.paata.telegram_expense_bot.prompt;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Компонент для загрузки prompt-файлов из resources.
 *
 * <p>Prompt-файлы хранятся отдельно от Java-кода, чтобы инструкции для LLM
 * было удобно менять без переписывания бизнес-логики.</p>
 */
@Component
public class PromptLoader {

    /**
     * Загружает prompt из classpath.
     *
     * @param path путь до prompt-файла внутри {@code src/main/resources}
     * @return текст prompt-файла в UTF-8
     */
    public String loadPrompt(String path) {
        try (
                InputStream inputStream =
                        getClass()
                                .getClassLoader()
                                .getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new RuntimeException(
                        "Prompt not found: " + path
                );
            }
            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to load prompt",
                    e
            );
        }
    }

    /**
     * Очищает ответ LLM от markdown-обертки вокруг JSON.
     *
     * @param response исходный ответ модели
     * @return строка без блоков {@code ```json} и {@code ```}
     */
    public String clean(String response) {
        return response
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }
}
