# Avito Deals Finder — правила для всех агентов

Продукт: Java-сервис поиска объявлений Avito с ценой существенно ниже рыночной.
Архитектура: **модульный монолит** (см. docs/architecture/ARCHITECTURE.md). Источник правды — `docs/`.

## Обязательные правила
1. Не придумывать файлы: перед изменением прочитать текущее содержимое.
2. Не менять публичные контракты (`docs/contracts`) без Architect + Orchestrator.
3. Не менять архитектуру самостоятельно; сомнения -> task для architect.
4. Не добавлять зависимости/инфраструктуру (Kafka, Redis, микросервисы) без ADR.
5. Не использовать LLM там, где надёжнее обычный алгоритм.
6. Секреты только через переменные окружения.
7. Никакого обхода CAPTCHA/антибота/ограничений источника. Разработка идёт на `MockListingSource`.
8. После изменений всегда запускать сборку и тесты; compile error чинить самостоятельно.
9. Не скрывать ошибки. Проблема вне зоны агента -> task для orchestrator.
10. Чужой код не переписывать: создавать task через orchestrator.

## Протокол задачи
Поля: Task ID, Title, Description, Requirements, Dependencies, Owner, Status, Acceptance Criteria, Files Changed, Tests, Review Status.
Статусы: TODO, IN_PROGRESS, BLOCKED, READY_FOR_REVIEW, CHANGES_REQUESTED, READY_FOR_TEST, PASSED, FAILED, DONE.
Поток: Developer -> Reviewer (APPROVED / CHANGES_REQUESTED) -> QA -> Orchestrator.

## Git
Ветки: `feature/*`, `fix/*`, `refactor/*`, `agent/<agent>/<task-id>`. В `main` напрямую не пушим.
Task -> Branch -> Implementation -> Tests -> Review -> QA -> Merge.

## Definition of Done
Код написан; проект собирается; тесты проходят; архитектура соответствует docs; нет критических замечаний ревью;
нет критических security issues; документация обновлена; acceptance criteria выполнены.
