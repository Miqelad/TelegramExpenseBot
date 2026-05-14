# Telegram Expense Bot

Telegram Expense Bot - это Spring Boot приложение для учета расходов через Telegram. Пользователь пишет обычным текстом, бот через LLM понимает намерение, извлекает расходы, сохраняет их в PostgreSQL, строит embeddings и использует semantic search/RAG для анализа трат.

## Важная логика данных

Расходы сохраняются с автором:

- `user_id` - Telegram user id;
- `username` - Telegram username, а если username отсутствует, имя/фамилия или `unknown`.

При этом отчеты, semantic search и AI-анализ работают по общей базе расходов без фильтрации по пользователю. То есть бот хранит автора записи, но аналитика строится по всем сохраненным расходам.

## Возможности

- Прием сообщений из Telegram через long polling.
- Ответ в исходный чат/тему по умолчанию или в заданный через env чат/тему.
- Классификация намерений пользователя через Groq LLM.
- Сохранение одного или нескольких расходов из одного сообщения.
- Сохранение автора расхода по Telegram `user_id` и `username`.
- Нормализация расходов через LLM в расширенный набор категорий: еда, транспорт, дом, здоровье, уход, животные, социальные траты, развлечения, покупки, digital/IT, финансы и привычки.
- Отчеты по всем расходам за текущий месяц, несколько месяцев, конкретный месяц или диапазон дат.
- Отчеты по конкретной категории или теме расходов с детализацией по описаниям, списком операций и AI-сводкой.
- Генерация embeddings через Jina AI.
- Хранение embeddings в PostgreSQL через pgvector.
- Semantic search по похожим расходам.
- RAG-анализ расходов с учетом найденного контекста.
- Docker Compose запуск приложения и базы.
- Отдельный пользователь БД для приложения, создаваемый через `init.sh`.

## Стек

- Java 21
- Spring Boot 4
- Spring WebFlux / WebClient
- Spring Data JPA
- Liquibase
- PostgreSQL 16
- pgvector
- TelegramBots 9.0.0
- Groq API
- Jina AI Embeddings API
- Docker / Docker Compose
- Lombok
- spring-dotenv для локального чтения `.env`

## Как это работает

1. Пользователь пишет сообщение боту.
2. `TelegramBot` получает update из Telegram.
3. Из Telegram update достаются текст, chat id, thread id, user id и username.
4. `GroqService.detectIntent` определяет intent:
   - `SAVE_EXPENSE`
   - `MONTHLY_REPORT`
   - `CATEGORY_REPORT`
   - `ANALYZE`
   - `UNKNOWN`
5. Для сохранения расходов `ExpenseExtractorService` извлекает список расходов из текста.
6. `ExpenseService` сохраняет расходы вместе с `user_id` и `username`.
7. `EmbeddingService` генерирует embedding для каждого расхода.
8. `ExpenseRepository.updateEmbedding` записывает vector в поле `embedding`.
9. Для общего отчета `ExpenseService` берет все расходы за период без фильтра по пользователю.
10. Для отчета по категории `CategoryReportQueryService` извлекает категорию, поисковый текст и период, а `ExpenseService` группирует расходы по описаниям и добавляет AI-сводку.
11. Для анализа `VectorSearchService` ищет похожие расходы по общей базе через pgvector.
12. `ExpenseAnalysisService` передает релевантный контекст в LLM.
13. Ответ отправляется в исходный чат/тему или в чат/тему из env-переменных.

## LLM

LLM используется для нескольких задач:

- `intent-classifier.txt` - понять, что хочет пользователь.
- `expense-extractor.txt` - извлечь расходы из свободного текста.
- `report-parser.txt` - понять период отчета.
- `category-report-parser.txt` - понять категорию, тему расходов и период для детального отчета.
- `category-report-summary.txt` - сформировать короткую сводку по отчету категории.
- `analyze-expenses.txt` - сформировать финансовый анализ.

Пример сообщения:

```text
кофе 300 и такси 500
```

LLM может вернуть список расходов:

```json
[
  {
    "amount": 300,
    "category": "COFFEE",
    "description": "кофе"
  },
  {
    "amount": 500,
    "category": "TAXI",
    "description": "такси"
  }
]
```

## Embedding, Vector, Semantic Search и RAG

### Embedding

Embedding - это числовое представление текста. В проекте используется модель:

```text
jina-embeddings-v3
```

Embedding строится для текста расхода:

```text
{description} {category}
```

### Vector и pgvector

В PostgreSQL используется расширение `pgvector`. В таблице `expenses` есть поле:

```sql
embedding vector(1024)
```

Расширения включаются так:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

### Semantic Search

Semantic search ищет расходы не по точному совпадению слов, а по смысловой близости:

```sql
SELECT *
FROM expenses
ORDER BY embedding <-> CAST(:embedding AS vector)
LIMIT 5
```

Для некоторых тем анализа поиск дополнительно фильтруется по категориям, но не по пользователю.

### RAG

RAG - Retrieval-Augmented Generation. Сначала приложение достает из базы похожие расходы, затем передает их как контекст в LLM. Так AI отвечает не абстрактно, а с учетом реальных данных из общей базы расходов.

## Структура проекта

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
│   ├── VectorSearchService.java
│   ├── ai
│   │   ├── CategoryReportQueryService.java
│   │   ├── ExpenseAnalysisService.java
│   │   └── ReportQueryService.java
│   ├── expense
│   │   ├── ExpenseExtractorService.java
│   │   └── ExpenseService.java
│   └── report/ReportPeriodService.java
├── telegram
│   ├── TelegramBot.java
│   └── TelegramConfig.java
└── util/VectorUtils.java
```

## Основные классы

- `TelegramBot` - принимает сообщения Telegram, достает `user_id`/`username`, маршрутизирует intent и выбирает чат/тему для ответа.
- `GroqService` - вызывает Groq Chat Completions API.
- `ExpenseExtractorService` - извлекает расходы из текста и добавляет автора записи.
- `ExpenseService` - сохраняет расходы, строит общие отчеты и отчеты по категории/теме расходов.
- `ReportQueryService` - парсит период отчета через LLM.
- `CategoryReportQueryService` - парсит категорию, поисковый текст и период для детального отчета.
- `ReportPeriodService` - вычисляет даты отчета.
- `EmbeddingService` - получает embeddings от Jina AI.
- `VectorSearchService` - ищет похожие расходы через pgvector по общей базе.
- `ExpenseAnalysisService` - делает AI-анализ расходов через RAG.
- `PromptLoader` - загружает prompt-файлы из resources.

## Модель данных

Таблица `expenses`:

| Поле | Тип | Описание |
| --- | --- | --- |
| `uuid` | `UUID` | Идентификатор расхода |
| `user_id` | `BIGINT` | Telegram user id автора расхода |
| `username` | `VARCHAR(255)` | Telegram username или fallback-имя автора |
| `amount` | `NUMERIC(19,2)` | Сумма |
| `category` | `VARCHAR(255)` | Категория |
| `description` | `TEXT` | Описание |
| `created_at` | `TIMESTAMP` | Дата создания |
| `embedding` | `vector(1024)` | Embedding расхода |

Категории:

- `GROCERIES`
- `RESTAURANTS`
- `COFFEE`
- `ALCOHOL`
- `TRANSPORT`
- `TAXI`
- `FUEL`
- `CAR`
- `RENT`
- `UTILITIES`
- `HOME`
- `HEALTH`
- `PHARMACY`
- `FITNESS`
- `SELFCARE`
- `COSMETICS`
- `PET`
- `FRIENDS`
- `GIFTS`
- `DATES`
- `ENTERTAINMENT`
- `GAMES`
- `TRAVEL`
- `SHOPPING`
- `CLOTHES`
- `ELECTRONICS`
- `DIGITAL_SERVICES`
- `SERVERS`
- `SOFTWARE`
- `SUBSCRIPTIONS`
- `EDUCATION`
- `WORK_EXPENSES`
- `CREDIT_PAYMENT`
- `TAXES`
- `INSURANCE`
- `TOBACCO`
- `VAPE`
- `OTHER`

Темы анализа:

- `HEALTH`
- `SAVINGS`
- `FOOD`
- `TRANSPORT`
- `SHOPPING`
- `GENERAL`

## Переменные окружения

Создайте `.env` на основе `.env.example`:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Пример:

```env
POSTGRES_DB=expenses
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_USER=postgres
POSTGRES_PASSWORD=change_me

EXPENSE_DB_USER=expense_user
EXPENSE_DB_PASSWORD=change_me_too

TELEGRAM_BOT_USERNAME=your_bot_username
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
TELEGRAM_RESPONSE_CHAT_ID=
TELEGRAM_RESPONSE_THREAD_ID=

GROQ_API_KEY=your_groq_api_key
JINA_API_KEY=your_jina_api_key
```

### Куда бот отправляет ответы

По умолчанию `TELEGRAM_RESPONSE_CHAT_ID` и `TELEGRAM_RESPONSE_THREAD_ID` пустые. В таком режиме бот отвечает как обычный Telegram-бот: в тот же чат и в ту же тему, где его спросили.

Если нужно, чтобы ответы всегда уходили в конкретную группу или тему, заполните переменные:

```env
TELEGRAM_RESPONSE_CHAT_ID=-100xxxxxxxxxx
TELEGRAM_RESPONSE_THREAD_ID=2
```

`TELEGRAM_RESPONSE_CHAT_ID` - id чата или группы, куда отправлять ответы. Для супергрупп Telegram id обычно начинается с `-100`.

`TELEGRAM_RESPONSE_THREAD_ID` - id темы внутри Telegram-группы. Если чат без тем или нужно отвечать просто в группу, оставьте переменную пустой.

Важно: эти параметры отвечают именно за место отправки ответа. Расходы все равно сохраняются с `user_id` и `username` автора входящего сообщения.

Где взять ключи:

- Jina AI API key: <https://jina.ai/api-dashboard/key-manager>
- Groq API key: <https://console.groq.com/keys?utm_source=chatgpt.com>
- Telegram bot token создается через BotFather.

Не коммитьте `.env`. В репозиторий должен попадать только `.env.example`.

## Docker Compose

Запуск приложения и базы:

```bash
docker-compose up --build -d
```

Запуск только базы:

```bash
docker-compose up -d postgres
```

Логи приложения:

```bash
docker-compose logs -f app
```

Логи базы:

```bash
docker-compose logs -f postgres
```

Остановка:

```bash
docker-compose down
```

Полное пересоздание базы с удалением volume:

```bash
docker-compose down -v
docker-compose up --build -d
```

## init.sh и миграции

`init.sh` выполняется контейнером Postgres только при первом создании volume. Он:

- включает `vector`;
- включает `uuid-ossp`;
- создает пользователя приложения из `EXPENSE_DB_USER`;
- назначает права на базу и схему `public`.

Liquibase миграции:

- `001-create-expenses-table.yaml` - создает таблицу расходов, pgvector и индексы.
- `002-add-expense-username.yaml` - добавляет поле `username` и индекс для существующих баз.

## Локальный запуск

1. Поднимите БД:

   ```bash
   docker-compose up -d postgres
   ```

2. Заполните `.env`.

3. Запустите приложение:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

## Примеры сообщений боту

Сохранение одного расхода:

```text
кофе 300
```

Сохранение нескольких расходов:

```text
кофе 300, такси 500, сигареты 400
```

Отчет по общей базе:

```text
отчет за месяц
```

```text
отчет за последние 3 месяца
```

```text
отчет за апрель
```

Отчет по категории или теме:

```text
отчет по кофе за месяц
```

```text
сколько ушло на такси в апреле
```

```text
покажи расходы на chatgpt за последние 3 месяца
```

Анализ по общей базе:

```text
проанализируй мои расходы
```

```text
что мне сократить?
```

```text
что вредно для здоровья?
```

## Полезные команды

Сборка:

```bash
./mvnw clean package
```

Тесты:

```bash
./mvnw test
```

Проверка контейнеров:

```bash
docker-compose ps
```

Обновление на сервере:

```bash
git pull
docker-compose up --build -d
```

## Безопасность

- Реальные ключи храните только в `.env` или переменных окружения сервера.
- `.env` находится в `.gitignore`.
- В README и `.env.example` используются только placeholder-значения.
- Если ключи когда-либо попадали в публичный репозиторий, их нужно перевыпустить.
