# Contract: DealScore
Status: Draft
DealScore{listingId, discountPercent, dealScore 0..100, riskScore 0..100, confidence 0..1, flags[], explanation[]}.
explanation — структурированные причины (код + параметры + текст): PRICE_BELOW_MEDIAN(23%), SAMPLE_SIZE(42), MODEL_MATCH(0.98), CONDITION(GOOD).
Правило: condition=DEFECTIVE или флаг 'на запчасти' не даёт высокий dealScore.
