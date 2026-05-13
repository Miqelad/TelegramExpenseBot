package com.paata.telegram_expense_bot.prompt;

import lombok.SneakyThrows;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Загрузчик prompt файлов.
 */
@Component
public class PromptLoader {

    /**
     * Загружает prompt из resources.
     *
     * @param path путь до prompt файла
     * @return prompt text
     */
    @SneakyThrows
    public String loadPrompt(String path) {

        ClassPathResource resource =
                new ClassPathResource(path);

        return new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }
}