# Локальная инфраструктура

## Docker Compose

Единственный canonical Compose-файл — [`../../../compose.yaml`](../../../compose.yaml)
в Git root. Запускайте команды из корня репозитория; конфигурация в этой
папке не дублируется. Сценарий предназначен для локальной разработки, не для
публикации в production.

1. Создайте локальный файл переменных и установите непустой случайный пароль
   PostgreSQL:

   ```sh
   if [ ! -f .env ]; then cp .env.example .env; fi
   ```

   `.env` игнорируется Git; не добавляйте в него секреты production и не
   передавайте его в образы.
2. Запустите PostgreSQL, backend и собранный Vite frontend:

   ```sh
   docker compose up --build -d
   docker compose ps
   ```
3. Откройте frontend по адресу `http://localhost:5173`. Backend доступен
   локально на `http://localhost:8080`, readiness — `/actuator/health/readiness`.
   PostgreSQL опубликован только на loopback `127.0.0.1:5432`.

Публикуемые порты всех сервисов привязаны к loopback. В Compose заданы
healthchecks; backend ждёт healthy PostgreSQL, frontend — healthy backend.
Backend readiness проверяет состояние приложения и доступность PostgreSQL.
Frontend собирается Vite и отдаётся непривилегированным Nginx; `/api` проксируется
на backend. Dockerfiles используют отдельные build/runtime stages и
непривилегированных runtime users.

Сохранить базу при остановке:

```sh
docker compose down
```

Обычная остановка не удаляет named volume базы. Не используйте `down -v`, если
не намерены безвозвратно удалить локальные данные. Резервное копирование и
безопасное восстановление описаны в
[PostgreSQL backup/restore](postgres-backups.md).

## Backend build

The canonical backend build uses the checked-in Maven Wrapper (Maven 3.9.16),
Java release 21, and Spring Boot 3.5.16:

```sh
./mvnw --batch-mode clean verify
```

The pre-existing Gradle wrapper/build is retained as a compatibility build.

## CI

GitHub Actions выполняет `./mvnw clean verify` и `npm ci`, `npm test`,
`npm run build`. Workflow не публикует образы и не выполняет deploy; deployment
требует отдельного согласования и настроенной policy/credentials.
