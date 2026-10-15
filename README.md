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
- Публичный Avito импорт пока не подключён к цепочке product matching → market
  pricing → saved deal scoring. Не считайте импортированные объявления готовыми
  сделками.
- CSV/JSON импорт, источник происхождения REAL/IMPORTED/DEMO, сохранение расходов,
  watchlist и полный интерфейс статусов/настроек не реализованы.
- Калькулятор прибыльности на странице сделки отправляет введённые расходы в
  `POST /api/profitability/calculate`; это отдельный расчёт, не сохранённый в
  сделке и не используемый для рейтинга.
- API не имеет авторизации. Запускайте только локально и не публикуйте в сеть.

См. [аудит среды](docs/ENVIRONMENT_AUDIT.md),
[критерии готовности](docs/ACCEPTANCE_CRITERIA.md) и
[инструкции Compose/backup](avito-deals/docs/development/README.md).
