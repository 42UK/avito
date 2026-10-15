# Avito HTML parser (INGEST-002)

`AvitoHtmlParser` parses supplied HTML strings only. It performs no HTTP
requests. The caller provides a `Clock` so relative publication dates are
deterministic in tests. Date results carry `EXACT` or `APPROXIMATE` accuracy;
calendar dates without an explicit year are approximate.

The parser uses the following stable markers and structured data. It does not
select by CSS class. **Avito markup may change; selectors and field availability
must be rechecked against approved public-page fixtures.**

| Page | Data | Selector / source |
|---|---|---|
| Feed | Listing card and ID | `[data-marker="item"]`, `data-item-id` |
| Feed | Title, location, relative date | `[data-marker="item-title"]`, `[data-marker="item-location"]`, `[data-marker="item-date"]` |
| Feed | Price and currency | `meta[itemprop="price"]`, `meta[itemprop="priceCurrency"]` |
| Feed | Canonical listing URL and description | `a[itemprop="url"]`, `meta[itemprop="description"]`; query and fragment are removed from URL |
| Feed | Total count and next-page numbers | `[data-marker="page-title/count"]`, `[data-marker="hidden-pagination/list-element/link"]` (`p` query parameter) |
| Item | Product fields and ID | JSON-LD `Product.sku`, `name`, `description`, `offers.price`, `offers.priceCurrency`, `offers.url`, `offers.itemCondition` |
| Item | Category path | JSON-LD `BreadcrumbList.itemListElement[].name`; `Главная` is omitted |
| Item | Attributes and displayed condition | `[data-marker="item-view/item-params"] li`; key from `span`, value from remaining item text |
| Item | Publication date, seller type | `[data-marker="item-view/item-date"]`, `[data-marker="seller-info/label"]` |

Returned records contain only the seller type (`PRIVATE`, `BUSINESS`, or
`UNKNOWN`); seller name, rating, phone, and view count are not persisted.
Missing fields remain null or empty collections. Invalid JSON-LD is logged and
does not prevent parsing available attributes or other cards.

Fixtures and parser acceptance tests are under `src/test/resources/avito/` and
`src/test/java/ru/avitodeals/ingestion/infrastructure/avito/`.
