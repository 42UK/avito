# Contract: Product
Status: Draft
Product{id, canonicalKey, brand, model, category, attributes}; ProductMatch{listingId, productId|null, confidence 0..1, status MATCHED|UNMATCHED|AMBIGUOUS, matcherVersion}.
Интерфейсы: ProductNormalizer, AttributeExtractor, ProductClassifier, ProductMatcher.
Инвариант: разные модели/модификации/объёмы памяти — разные Product.
