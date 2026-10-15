# ARCHITECTURE (draft, ARCH-001)

## Стиль
Модульный монолит: один Spring Boot процесс, один PostgreSQL. Модули общаются через интерфейсы и доменные события внутри процесса.

## Конвейер
```
ListingSource -> Ingestion -> Matching -> Pricing -> Scoring -> Notification
 (Mock/Avito)       |           |           |          |            |
                 listing     product   market_price  deal_score  notification
                                  REST API <- Frontend
```

## Модули
| Модуль | Отвечает | Зависит от |
|---|---|---|
| `shared` | базовые типы, Money, ошибки | — |
| `listing` | Listing, Seller, PriceHistory | shared |
| `ingestion` | ListingSource, pipeline, адаптеры | listing |
| `product` | Product, ProductAttribute, matching | listing, shared |
| `pricing` | MarketPriceService | product, listing |
| `scoring` | DealScore, риск, explanation | pricing, product, listing |
| `search` | сохранённые поиски, планировщик | ingestion, scoring, shared |
| `notification` | NotificationService, Telegram-адаптер | scoring (через события), shared |
| `api` | REST, DTO, OpenAPI | все (только через сервисные интерфейсы) |

В колонке `Зависит от` перечислены разрешённые compile-time зависимости. `A -> B`
означает, что модуль `A` может зависеть только от экспортируемого API модуля
`B`; порядок строк таблицы не задаёт направление зависимостей. Межмодульный
вызов проходит через публичный application-интерфейс или контракт события, а не
через внутренние пакеты. Граф зависимостей должен оставаться ацикличным.

`api` и адаптеры (`AvitoListingSource`, `TelegramNotificationService`) — внешний
слой; `scoring` не знает о Telegram; `ingestion` не знает деталей Avito за
пределами адаптера; Controller без бизнес-логики; DTO отдельно от entity.
`shared.application` экспортирует общую транзакционную синхронизацию ключей для
защиты конкурентных обновлений агрегатов. Границы и перечисленный граф
проверяются ArchUnit-тестами (BACKEND-001).

`scoring` публикует `DealScored` после сохранения результата; событие содержит
минимальные данные объявления и оценки для уведомления. `notification`
обрабатывает его только после commit и только при флаге `DEAL`, без
`BLOCKED`/`REVIEW`. `scoring` не зависит от notification.

## Принципы алгоритмов (детали в ADR/algorithms при реализации)
- Matching: детерминированные правила + словари синонимов RU/EN, версия матчера в данных; неоднозначность -> `UNMATCHED`/`AMBIGUOUS`, не угадывание.
- Market price: медиана сопоставимых (тот же product + состояние), отсев выбросов по MAD/IQR, при sampleSize < порога — LOW confidence без скоринга.
- Scoring: прозрачная формула, список причин (explanation), риск-флаги (дефект/запчасти, подозрительно малая цена и т.д.).

## Нефункциональное
Конфиг и секреты только через env; идемпотентный ingestion; retry/timeout/rate limit в адаптере; логи без секретов; health check Actuator.
