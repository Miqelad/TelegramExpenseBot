package com.paata.telegram_expense_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа в Spring Boot приложение Telegram Expense Bot.
 *
 * <p>Поднимает Spring-контекст, регистрирует Telegram long polling бота,
 * подключает сервисы работы с LLM, embeddings, PostgreSQL и Liquibase.</p>
 */
@SpringBootApplication
public class TelegramExpenseBotApplication {

	/**
	 * Запускает приложение.
	 *
	 * @param args аргументы командной строки
	 */
	public static void main(String[] args) {
		SpringApplication.run(TelegramExpenseBotApplication.class, args);
	}

}
