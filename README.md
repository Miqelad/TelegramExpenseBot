# Telegram Expense Bot

Telegram Expense Bot - это Spring Boot приложение для учета расходов через Telegram. Пользователь пишет обычным текстом вроде `кофе 300` или `яндекс такси 340`, бот через LLM понимает намерение, извлекает сумму и категорию, сохраняет расход в PostgreSQL и дополнительно строит embedding для semantic search, RAG и AI-аналитики.

## Что умеет бот

- Принимать сообщения из Telegram через long polling.
- Определять intent пользователя через LLM:
  - `SAVE_EXPENSE` - сохранить расход.
  - `MONTHLY_REPORT` - показать отчет за текущий месяц.
  - `ANALYZE` - проанализировать расходы и дать рекомендации.
  - `UNKNOWN` - обработать непонятный запрос.
- Извлекать из текста сумму, категорию и описание расхода.
- Сохранять расходы в PostgreSQL.
- Генерировать embeddings через Jina AI.
- Хранить векторы в PostgreSQL с расширением `pgvector`.
- Искать похожие расходы через vector similarity / semantic search.
- Использовать RAG pipeline для анализа расходов через LLM.
- Создавать отчет по категориям за текущий месяц.

## Технологии

- Java 21.
- Spring Boot 4.
- Spring WebFlux и `WebClient` для HTTP-запросов к AI API.
- Spring Data JPA для работы с базой данных.
- PostgreSQL 16.
- `pgvector` для хранения и поиска vector embeddings.
- Liquibase для миграций базы данных.
- TelegramBots 9.0.0 для Telegram long polling бота.
- Groq API для LLM.
- Модель Groq: `llama-3.3-70b-versatile`.
- Jina AI Embeddings API.
- Модель embeddings: `jina-embeddings-v3`.
- Docker и Docker Compose для локального запуска.
- Lombok для уменьшения boilerplate-кода.

## LLM, Embedding, Vector, RAG и Semantic Search

### LLM

LLM используется в двух местах:

1. Определение intent пользователя.

   Пример:

   ```text
   отчет за месяц
   ```

   LLM возвращает JSON:

   ```json
   {
     "intent": "MONTHLY_REPORT"
   }
   ```

2. Извлечение данных расхода из свободного текста.

   Пример:

   ```text
   макдак 900
   ```

   LLM возвращает JSON:

   ```json
   {
     "amount": 900,
     "category": "FOOD",
     "description": "макдак"
   }
   ```

В проекте prompts лежат в:

- `src/main/resources/prompts/intent-classifier.txt`
- `src/main/resources/prompts/expense-extractor.txt`

### Embedding

Embedding - это числовое представление текста. Текст превращается в vector, где близкие по смыслу тексты имеют похожие векторы.

В проекте embedding создается через Jina AI:

```java
new EmbeddingRequest("jina-embeddings-v3", List.of(text))
```

Для расхода embedding строится из описания и категории:

```text
{description} {category}
```

Например:

```text
макдак FOOD
```

После этого vector сохраняется в поле `embedding`.

### Vector и pgvector

PostgreSQL сам по себе не умеет эффективно работать с embedding-векторами. Для этого используется расширение `pgvector`.

В таблице `expenses` есть поле:

```sql
embedding vector(1024)
```

Расширение включается так:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Для ускорения поиска создается vector index:

```sql
CREATE INDEX idx_expenses_embedding
ON expenses
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```

### Semantic Search

Semantic Search - это поиск не по точному совпадению слов, а по смысловой близости.

Пример: пользователь спрашивает:

```text
на что я трачу деньги на еду?
```

Система:

1. Генерирует embedding для запроса.
2. Сравнивает его с embedding расходов в базе.
3. Возвращает самые похожие расходы.

В проекте поиск похожих расходов выполняется SQL-запросом:

```sql
SELECT *
FROM expenses
ORDER BY embedding <-> CAST(:embedding AS vector)
LIMIT 5
```

Оператор `<->` сравнивает расстояние между vector embeddings. Чем меньше расстояние, тем ближе смысл.

### RAG

RAG - Retrieval-Augmented Generation. Это подход, где LLM отвечает не только на основе своих знаний, а получает релевантный контекст из базы данных.

В проекте pipeline такой:

1. Пользователь задает вопрос:

   ```text
   что мне сократить в расходах?
   ```

2. `VectorSearchService` ищет похожие расходы через semantic search.
3. Найденные расходы собираются в текстовый контекст.
4. Этот контекст добавляется в prompt.
5. Groq LLM анализирует расходы и отвечает пользователю.

Пример контекста для LLM:

```text
Категория: FOOD
Сумма: 900
Описание: макдак

Категория: TRANSPORT
Сумма: 340
Описание: яндекс такси
```

Так LLM отвечает с учетом реальных расходов пользователя.

## Архитектура проекта

```text
src/main/java/com/paata/telegram_expense_bot
├── groq
│   ├── config/WebClientConfig.java
│   └── service/GroqService.java
├── model
│   ├── dto
│   ├── entity/Expense.java
│   └── enums
├── prompt/PromptLoader.java
├── repository/ExpenseRepository.java
├── service
│   ├── EmbeddingService.java
│   ├── ExpenseAnalysisService.java
│   ├── ExpenseService.java
│   └── VectorSearchService.java
├── telegram
│   ├── TelegramBot.java
│   └── TelegramConfig.java
└── util/VectorUtils.java
```

## Основные классы

### `TelegramBot`

Главный обработчик Telegram updates.

Он:

- получает текст сообщения;
- достает `chatId` и `userId`;
- вызывает `GroqService.detectIntent`;
- в зависимости от intent вызывает нужный сервис;
- отправляет ответ обратно в Telegram.

### `GroqService`

Сервис для работы с Groq API.

Используется для:

- определения intent;
- извлечения данных расхода;
- AI-анализа расходов.

Endpoint:

```text
https://api.groq.com/openai/v1/chat/completions
```

### `ExpenseService`

Основная бизнес-логика расходов.

Умеет:

- сохранять расход;
- извлекать расход из текста через LLM;
- строить месячный отчет;
- получать расходы за текущий месяц;
- генерировать и сохранять embedding после сохранения расхода.

### `EmbeddingService`

Генерирует embedding через Jina AI.

Endpoint:

```text
https://api.jina.ai/v1/embeddings
```

### `VectorSearchService`

Выполняет semantic search:

- генерирует embedding для пользовательского запроса;
- конвертирует vector в формат `pgvector`;
- ищет похожие расходы в PostgreSQL.

### `ExpenseAnalysisService`

Делает RAG-анализ:

- ищет релевантные расходы;
- собирает контекст;
- отправляет контекст и вопрос пользователя в LLM;
- возвращает аналитический ответ.

### `ExpenseRepository`

JPA repository для расходов.

Содержит:

- поиск расходов пользователя;
- поиск расходов за период;
- обновление поля `embedding`;
- native SQL для vector similarity search.

### `VectorUtils`

Конвертирует Java `List<Float>` в строковый формат `pgvector`:

```text
[0.12,0.45,0.78]
```

## Модель данных

Таблица `expenses`:

| Поле | Тип | Описание |
| --- | --- | --- |
| `uuid` | `UUID` | Primary key |
| `user_id` | `BIGINT` | Telegram user id |
| `amount` | `NUMERIC(19,2)` | Сумма расхода |
| `category` | `VARCHAR(255)` | Категория |
| `description` | `TEXT` | Описание |
| `created_at` | `TIMESTAMP` | Дата создания |
| `embedding` | `vector(1024)` | Embedding расхода |

Категории:

- `FOOD`
- `TRANSPORT`
- `TOBACCO`
- `ENTERTAINMENT`
- `HEALTH`
- `SHOPPING`
- `OTHER`

## Переменные окружения

Секреты не хранятся в README и не должны храниться в репозитории. Создайте `.env` на основе `.env.example`:

```bash
cp .env.example .env
```

Для Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Заполните значения:

```env
POSTGRES_DB=expenses
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<your_postgres_password>

TELEGRAM_BOT_USERNAME=<your_bot_username>
TELEGRAM_BOT_TOKEN=<your_telegram_bot_token>

GROQ_API_KEY=<your_groq_api_key>
JINA_API_KEY=<your_jina_api_key>
```

Где взять ключи:

- Jina AI API key: <https://jina.ai/api-dashboard/key-manager>
- Groq API key: <https://console.groq.com/keys?utm_source=chatgpt.com>

Telegram bot token создается через BotFather в Telegram.

## Запуск через Docker Compose

1. Создайте `.env`:

   ```bash
   cp .env.example .env
   ```

2. Заполните `.env` реальными значениями.

3. Запустите приложение и базу:

   ```bash
   docker compose up --build
   ```

Будут подняты:

- `telegram-expense-bot` - Spring Boot приложение.
- `expense-postgres` - PostgreSQL 16 с `pgvector`.

PostgreSQL будет доступен на:

```text
localhost:5432
```

Приложение будет доступно на:

```text
localhost:8080
```

Важно: бот работает через Telegram long polling, поэтому основной пользовательский интерфейс - это чат с ботом в Telegram.

## Docker Compose

В проекте добавлен `docker-compose.yml`:

```yaml
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: telegram-expense-bot
    depends_on:
      - postgres
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/expenses?tcpKeepAlive=true
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      TELEGRAM_BOT_USERNAME: ${TELEGRAM_BOT_USERNAME}
      TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN}
      GROQ_API_KEY: ${GROQ_API_KEY}
      JINA_API_KEY: ${JINA_API_KEY}
    ports:
      - "8080:8080"

  postgres:
    image: pgvector/pgvector:pg16
    container_name: expense-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

volumes:
  postgres_data:
```

## init.sql

`init.sql` включает расширения PostgreSQL:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

Liquibase также включает эти расширения в changelog, поэтому база корректно поднимается и при обычном запуске приложения.

## Локальный запуск без Docker

Нужны:

- Java 21.
- PostgreSQL с расширением `pgvector`.
- Maven wrapper из проекта.
- Telegram bot token.
- Groq API key.
- Jina AI API key.

Поднимите PostgreSQL отдельно, например через Docker:

```bash
docker compose up postgres
```

Задайте переменные окружения.

PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/expenses?tcpKeepAlive=true"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="<your_postgres_password>"
$env:TELEGRAM_BOT_USERNAME="<your_bot_username>"
$env:TELEGRAM_BOT_TOKEN="<your_telegram_bot_token>"
$env:GROQ_API_KEY="<your_groq_api_key>"
$env:JINA_API_KEY="<your_jina_api_key>"
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/expenses?tcpKeepAlive=true"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="<your_postgres_password>"
export TELEGRAM_BOT_USERNAME="<your_bot_username>"
export TELEGRAM_BOT_TOKEN="<your_telegram_bot_token>"
export GROQ_API_KEY="<your_groq_api_key>"
export JINA_API_KEY="<your_jina_api_key>"
./mvnw spring-boot:run
```

## Примеры сообщений боту

Сохранение расходов:

```text
кофе 300
```

```text
макдак 900
```

```text
яндекс такси 340
```

```text
вейп 1500
```

Отчет:

```text
отчет за месяц
```

AI-анализ:

```text
проанализируй мои расходы
```

```text
что мне сократить?
```

```text
куда уходит больше всего денег?
```

## Как проходит сохранение расхода

1. Пользователь пишет сообщение в Telegram.
2. `TelegramBot` получает update.
3. `GroqService.detectIntent` определяет intent.
4. Если intent `SAVE_EXPENSE`, вызывается `ExpenseService.saveExpense`.
5. `GroqService.ask` извлекает `amount`, `category`, `description`.
6. Расход сохраняется в таблицу `expenses`.
7. `EmbeddingService` создает embedding через Jina AI.
8. `VectorUtils` конвертирует embedding в формат `pgvector`.
9. `ExpenseRepository.updateEmbedding` сохраняет vector в PostgreSQL.

## Как проходит AI-анализ

1. Пользователь задает аналитический вопрос.
2. LLM классифицирует intent как `ANALYZE`.
3. `ExpenseAnalysisService` вызывает semantic search.
4. `VectorSearchService` ищет похожие расходы в базе.
5. Найденные расходы становятся контекстом.
6. Контекст и вопрос отправляются в Groq LLM.
7. Пользователь получает ответ с учетом своих данных.

## Миграции базы

Liquibase changelog:

```text
src/main/resources/db/changelog/changelog-master.yaml
src/main/resources/db/changelog/changes/001-create-expenses-table.yaml
```

Миграция создает:

- расширение `vector`;
- расширение `uuid-ossp`;
- таблицу `expenses`;
- индексы по `user_id`, `category`, `created_at`;
- vector index `idx_expenses_embedding`.

## Полезные команды

Собрать проект:

```bash
./mvnw clean package
```

Windows:

```powershell
.\mvnw.cmd clean package
```

Запустить тесты:

```bash
./mvnw test
```

Остановить Docker Compose:

```bash
docker compose down
```

Остановить и удалить volume базы:

```bash
docker compose down -v
```

## Безопасность

- Не коммитьте `.env`.
- Не вставляйте реальные Telegram, Groq и Jina ключи в README, issue, commit messages или screenshots.
- Для публикации проекта лучше перевыпустить ключи, если они когда-либо попадали в репозиторий.
- В `application.yaml` используются переменные окружения, а не реальные секреты.
