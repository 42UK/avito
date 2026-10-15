# ADR-005: Build- и frontend-инструменты
Status: Superseded for backend build tooling; frontend decision remains accepted.

## Контекст
В существующем репозитории backend уже использует Gradle Groovy DSL и Java 21.
Для дашборда нужен небольшой клиент без требований SEO или SSR.

## Решение
По требованию MVP каноническая backend-сборка переведена на Maven Wrapper 3.9.16,
Spring Boot 3.5.16 и Java release 21. Ранее существующий Gradle build сохранён
как совместимая сборка, но CI и Docker используют Maven. Для frontend используем
Vite + React + TypeScript. Next.js рассматривается только при появлении требования SSR/SEO.

## Последствия
Backend имеет Maven Wrapper как воспроизводимую каноническую сборку; сохранённый
Gradle build остаётся дополнительным путём проверки. Frontend собирается отдельно
от Spring Boot приложения.
