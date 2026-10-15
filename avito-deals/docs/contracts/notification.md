# Contract: Notification
Status: Implemented (NOTIFY-001)

`NotificationService.send(NotificationMessage)` is the application port. The
message contains `dealScoreId`, `dedupKey`, `channel`, `listingId`, `title`,
`body`, and `url`; the result reports `SENT`, `DUPLICATE`, `SKIPPED`, `FAILED`,
or `UNCERTAIN` and may include the persisted notification ID and a safe error
code. The only current channel adapter is Telegram.

`scoring` publishes the `DealScored` application event after persisting a score.
Its payload includes the listing title and URL resolved through the listing
application API, along with price, currency, score, risk and gate flags.
Notification delivery runs after commit. Only scores with `DEAL` and neither
`BLOCKED` nor `REVIEW` are sent.

The deduplication key is `deal-score:<dealScoreId>` and the database unique
constraint on `(channel, dedup_key)` is authoritative. Claims are persisted as
`SENDING` before calling Telegram; row-level write locking serializes retries
across application instances and the unique constraint protects concurrent
first claims. A successful response becomes `SENT`.
Definite rejection/configuration errors become `FAILED` and can be retried on a
subsequent delivery up to `avito-deals.notifications.max-attempts` (default 3).
Transport errors, HTTP 5xx responses, and in-progress deliveries with an unknown
outcome become `UNCERTAIN` and are not automatically repeated because Telegram
offers no idempotency key; this avoids claiming exactly-once delivery or
silently duplicating a possibly accepted message. Error status, code, message
and attempt metadata are persisted; logs contain no bot token or raw HTTP
exception.

Set `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, and optionally
`TELEGRAM_TIMEOUT` in the runtime environment. No token or chat ID is stored in
the repository. Unit tests replace the transport and do not issue network calls.
