# Module Boundaries

Пакет: `ru.avitodeals.<module>.{domain,application,infrastructure,api}`
- `domain` — сущности и интерфейсы-порты.
- `application` — сервисы, use cases, транзакции.
- `infrastructure` — репозитории, адаптеры (Avito, Telegram).
- `api` — Controller/DTO (только в модуле `api`).

Запреты (проверять ArchUnit): `scoring` -> `notification`; любой модуль -> `AvitoListingSource` напрямую; Controller -> Repository; domain -> infrastructure; frontend -> DB.
Общение между модулями: интерфейсы сервисов + события (`ListingIngested`, `ProductMatched`, `MarketPriceCalculated`, `DealScored`). Notification may depend on the public scoring event API and `shared.application`; scoring must not depend on notification.

Направление каждой разрешённой compile-time зависимости задаётся таблицей
`Зависит от` в `ARCHITECTURE.md`: запись `A -> B` означает зависимость модуля
`A` от публичного API `B`, а не обратное направление. Порядок модулей в таблице
не является правилом слоёв. Граф должен быть ацикличным; внутренние пакеты
соседнего модуля недоступны. ArchUnit проверяет именно перечисленные рёбра и
запреты, а межмодульные события не должны создавать обратную compile-time
зависимость.

`shared.application` предоставляет транзакционную синхронизацию ключей для
конкурентных обновлений; прикладные модули могут использовать только этот
экспортируемый API.
