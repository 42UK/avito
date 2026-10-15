# DealRadar

Локальный MVP для наблюдения объявлений, анализа сохранённых сделок и расчёта
сценарной прибыльности. Автоматический сбор Avito в текущей среде не подтверждён:
сайт отвечает HTTP 429. Интеграция останавливается/приостанавливается при
ограничениях и не обходит CAPTCHA или защиту доступа.

## Быстрый запуск

Требуются Java 21, Docker Compose, Node.js/npm для запуска frontend вне Compose.
Backend собирается Maven Wrapper (Maven 3.9.16) с Java release 21. PostgreSQL,
backend и production frontend запускаются через Compose:

```sh
if [ ! -f .env ]; then cp .env.example .env; fi
# Установить собственный локальный POSTGRES_PASSWORD в .env.
docker compose up --build -d
docker compose ps
```

Frontend: <http://localhost:5173>. Backend health: <http://localhost:8080/actuator/health>.
OpenAPI: <http://localhost:8080/v3/api-docs>; Swagger UI:
<http://localhost:8080/swagger-ui/index.html>. Порты Compose привязаны к localhost.
Чтобы выполнить поиск сразу, откройте «Поиски», создайте сохранённый запрос и нажмите
«Запустить». Это вызывает `POST /api/searches/{id}/run`; активные запросы также
проверяются планировщиком раз в минуту согласно их интервалу. Источник выбирается
переменной `AVITO_DEALS_INGESTION_SOURCE` (по умолчанию `avito`). HTTP 429 виден
как ошибка API и включает период охлаждения вместо обхода ограничения.
Остановка без удаления базы:

```sh
docker compose down
```

Не используйте `docker compose down -v`, если хотите сохранить данные.

## Проверки

```sh
./mvnw --batch-mode clean verify
cd frontend
npm ci
npm test
npm run build
```

## Важные ограничения

- Единственный доступный источник — текущий адаптер публичной страницы Avito и
  изолированный mock для разработки. Наличие адаптера не гарантирует разрешённый
  или успешный доступ; на 2026-10-15 сервер отвечал HTTP 429.
- Текущий Avito адаптер обращается к публичным страницам, но не получил ни одного
  объявления при последней реальной проверке: Avito ответил HTTP 429. Не считайте
  интеграцию успешно работающей или доступ гарантированным.
- Полученные записи проходят нормализацию, сопоставление, оценку рынка и scoring.
  Происхождение `REAL`/`IMPORTED`/`DEMO` хранится отдельно; CSV/JSON импорт,
  сохранение расходов, watchlist и полный интерфейс статусов/настроек пока не
  реализованы.
- Калькулятор прибыльности на странице сделки отправляет введённые расходы в
  `POST /api/profitability/calculate`; это отдельный расчёт, не сохранённый в
  сделке и не используемый для рейтинга.
- API не имеет авторизации. Запускайте только локально и не публикуйте в сеть.

См. [аудит среды](docs/ENVIRONMENT_AUDIT.md),
[критерии готовности](docs/ACCEPTANCE_CRITERIA.md) и
[инструкции Compose/backup](avito-deals/docs/development/README.md).
