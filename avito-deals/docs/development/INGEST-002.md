# INGEST-002: Avito HTML parser (слой разбора, без HTTP)

Owner: ingestion | Depends on: INGEST-001, ADR-006 (draft) | Status: TODO

## Цель
Класс `AvitoHtmlParser`, который превращает сохранённый HTML страницы Avito в доменные объекты. Работает только со строкой HTML, сеть не использует. Реализация HTTP (`AvitoPageFetcher`) и `AvitoListingSource` — отдельная задача INGEST-003.

## Рамки (обязательно)
- Только публичные страницы. Никаких обходов CAPTCHA, антибота, лимитов; не подделывать отпечатки браузера, не использовать прокси-ротацию и антикапча-сервисы.
- Не сохранять персональные данные: имя продавца, рейтинг, телефон. Допустим только тип продавца (PRIVATE / BUSINESS / UNKNOWN).
- Все селекторы только по `data-marker`, `itemprop`, `data-item-id` и JSON-LD. CSS-классы хэшированные, на них опираться нельзя.
- Парсер не должен падать на неполной карточке: отсутствующие поля = null/Optional, ошибка по карточке логируется, остальные карточки разбираются.

## Фикстуры
`src/test/resources/avito/`:
- `feed-electronics.html` — страница ленты раздела (`/kirzhach/bytovaya_elektronika`)
- `item-cpu-i5-10400f.html` — объявление: процессор Intel Core i5 (id 8098848667)
- `item-iphone-16-pro.html` — объявление: iPhone 16 Pro 128 ГБ (id 8450600956)
Копировать только `.html`, папки `*_files` не копировать.

## Селекторы
### Лента
| Поле | Источник |
|---|---|
| карточка | `[data-marker="item"]` (id в `data-item-id`) |
| название | `[data-marker="item-title"]` (текст или атрибут title) |
| цена | `meta[itemprop="price"]` content, валюта `meta[itemprop="priceCurrency"]` |
| URL | `a[itemprop="url"]` href, без query-параметров |
| описание | `meta[itemprop="description"]` content |
| регион | `[data-marker="item-location"]` (может быть пустым, тогда брать slug региона из URL) |
| дата | `[data-marker="item-date"]` — относительная («2 часа назад», «Вчера», «3 дня назад») |
| всего объявлений | `[data-marker="page-title/count"]` |
| пагинация | `[data-marker="hidden-pagination/list-element/link"]` (параметр `p`) |

Внимание: на странице есть карточки из рекомендательных блоков других регионов. Регион карточки определять по slug в URL; поле `inRequestedRegion` считает вызывающий код, парсер возвращает slug региона.

### Страница объявления
| Поле | Источник |
|---|---|
| id | JSON-LD `Product.sku` |
| название, описание, цена, валюта, URL | JSON-LD `Product`, `offers` |
| состояние | `offers.itemCondition` (UsedCondition / NewCondition) и параметр «Состояние» |
| категория | JSON-LD `BreadcrumbList` (имена элементов, без «Главная») |
| характеристики | `[data-marker="item-view/item-params"] li`: ключ в `span` (без «: »), значение — остаток текста; у «Состояние» в одном из вариантов нет «: » — обработать |
| дата публикации | `[data-marker="item-view/item-date"]`: «· вчера в 18:38», «· 2 октября в 19:10» |
| тип продавца | `[data-marker="seller-info/label"]`: «Частное лицо» / магазин |
| просмотры | `[data-marker="item-view/total-views"]` (необязательно) |

## Что вернуть
`ParsedFeedItem(externalId, title, price, currency, url, regionSlug, description, publishedAtApprox)`
`ParsedItemPage(externalId, title, description, price, currency, url, condition, categoryPath, attributes: Map<String,String>, sellerType, publishedAt)`
Относительные даты переводить в `Instant` через `Clock` (параметр конструктора), чтобы тесты были детерминированными. Помечать точность (ТОЧНО / ПРИБЛИЗИТЕЛЬНО).

## Acceptance criteria
- Лента: все карточки разобраны; id, название, цена и URL заполнены у каждой, где они есть в HTML; тест сверяет конкретные значения (например, 45000 RUB для id 8376354125).
- Объявления: CPU — модель «Core i5-10400F», сокет «LGA1200», состояние «Б/у», категория Процессоры; iPhone — состояние «Хорошее», «Состояние аккумулятора» 92 %, комплект «Коробка».
- Тесты на: пустой HTML, HTML без карточек, карточка без цены, битый JSON-LD, относительные даты («2 часа назад», «Вчера», «3 дня назад», «вчера в 18:38», «2 октября в 19:10»).
- Нет обращения к сети; `./gradlew test` проходит.
- ArchUnit: парсер лежит в `ingestion.infrastructure.avito`, бизнес-модули его не импортируют.
- Документация: `docs/development/avito-parser.md` с таблицей селекторов и пометкой «разметка может измениться».
