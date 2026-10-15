CREATE TABLE seller (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source varchar(100) NOT NULL,
    external_id varchar(255) NOT NULL,
    name text,
    CONSTRAINT uq_seller_source_external_id UNIQUE (source, external_id),
    CONSTRAINT ck_seller_source_not_blank CHECK (btrim(source) <> ''),
    CONSTRAINT ck_seller_external_id_not_blank CHECK (btrim(external_id) <> '')
);

CREATE TABLE search (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    query text NOT NULL,
    filters jsonb NOT NULL DEFAULT '{}'::jsonb,
    min_deal_score numeric(5, 2) NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    interval_minutes integer NOT NULL,
    CONSTRAINT ck_search_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_search_query_not_blank CHECK (btrim(query) <> ''),
    CONSTRAINT ck_search_filters_object CHECK (jsonb_typeof(filters) = 'object'),
    CONSTRAINT ck_search_min_deal_score_range CHECK (min_deal_score BETWEEN 0 AND 100),
    CONSTRAINT ck_search_interval_positive CHECK (interval_minutes > 0)
);

CREATE TABLE listing (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source varchar(100) NOT NULL,
    external_id varchar(255) NOT NULL,
    seller_id uuid REFERENCES seller (id) ON DELETE SET NULL,
    title text NOT NULL,
    description text,
    price numeric(14, 2) NOT NULL,
    currency varchar(3) NOT NULL,
    condition varchar(16) NOT NULL,
    location text,
    url text NOT NULL,
    status varchar(16) NOT NULL,
    content_hash varchar(128),
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    CONSTRAINT uq_listing_source_external_id UNIQUE (source, external_id),
    CONSTRAINT ck_listing_source_not_blank CHECK (btrim(source) <> ''),
    CONSTRAINT ck_listing_external_id_not_blank CHECK (btrim(external_id) <> ''),
    CONSTRAINT ck_listing_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_listing_price_nonnegative CHECK (price >= 0),
    CONSTRAINT ck_listing_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_listing_condition CHECK (condition IN ('NEW', 'GOOD', 'FAIR', 'DEFECTIVE', 'UNKNOWN')),
    CONSTRAINT ck_listing_url_not_blank CHECK (btrim(url) <> ''),
    CONSTRAINT ck_listing_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_listing_seen_order CHECK (first_seen_at <= last_seen_at)
);

CREATE TABLE search_listing (
    search_id uuid NOT NULL REFERENCES search (id) ON DELETE CASCADE,
    listing_id uuid NOT NULL REFERENCES listing (id) ON DELETE CASCADE,
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    CONSTRAINT pk_search_listing PRIMARY KEY (search_id, listing_id),
    CONSTRAINT ck_search_listing_seen_order CHECK (first_seen_at <= last_seen_at)
);

CREATE TABLE listing_price_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id uuid NOT NULL REFERENCES listing (id),
    price numeric(14, 2) NOT NULL,
    currency varchar(3) NOT NULL,
    observed_at timestamptz NOT NULL,
    CONSTRAINT ck_listing_price_history_price_nonnegative CHECK (price >= 0),
    CONSTRAINT ck_listing_price_history_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE product (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_key varchar(512) NOT NULL,
    brand text,
    model text,
    category text,
    CONSTRAINT uq_product_canonical_key UNIQUE (canonical_key),
    CONSTRAINT ck_product_canonical_key_not_blank CHECK (btrim(canonical_key) <> '')
);

CREATE TABLE product_attribute (
    product_id uuid NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    attribute_key varchar(255) NOT NULL,
    attribute_value text NOT NULL,
    CONSTRAINT pk_product_attribute PRIMARY KEY (product_id, attribute_key),
    CONSTRAINT ck_product_attribute_key_not_blank CHECK (btrim(attribute_key) <> ''),
    CONSTRAINT ck_product_attribute_value_not_blank CHECK (btrim(attribute_value) <> '')
);

CREATE TABLE listing_product_match (
    listing_id uuid PRIMARY KEY REFERENCES listing (id),
    product_id uuid REFERENCES product (id),
    confidence numeric(5, 4) NOT NULL,
    matcher_version varchar(100) NOT NULL,
    status varchar(16) NOT NULL,
    CONSTRAINT ck_listing_product_match_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_listing_product_match_version_not_blank CHECK (btrim(matcher_version) <> ''),
    CONSTRAINT ck_listing_product_match_status CHECK (status IN ('MATCHED', 'UNMATCHED', 'AMBIGUOUS')),
    CONSTRAINT ck_listing_product_match_product_status CHECK (
        (status <> 'MATCHED' OR product_id IS NOT NULL)
        AND (status <> 'UNMATCHED' OR product_id IS NULL)
    )
);

CREATE TABLE market_price (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id uuid NOT NULL REFERENCES product (id),
    condition_group varchar(16) NOT NULL,
    median numeric(14, 2) NOT NULL,
    p25 numeric(14, 2) NOT NULL,
    p75 numeric(14, 2) NOT NULL,
    market_price numeric(14, 2) NOT NULL,
    currency varchar(3) NOT NULL,
    sample_size integer NOT NULL,
    confidence_score numeric(5, 4) NOT NULL,
    confidence_level varchar(8) NOT NULL,
    outliers jsonb NOT NULL DEFAULT '[]'::jsonb,
    outliers_count integer NOT NULL,
    method varchar(100) NOT NULL,
    computed_at timestamptz NOT NULL,
    CONSTRAINT ck_market_price_condition_group CHECK (
        condition_group IN ('NEW', 'GOOD', 'FAIR', 'DEFECTIVE', 'UNKNOWN')
    ),
    CONSTRAINT ck_market_price_values_nonnegative CHECK (
        median >= 0 AND p25 >= 0 AND p75 >= 0 AND market_price >= 0
    ),
    CONSTRAINT ck_market_price_percentiles_order CHECK (p25 <= median AND median <= p75),
    CONSTRAINT ck_market_price_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_market_price_sample_size_nonnegative CHECK (sample_size >= 0),
    CONSTRAINT ck_market_price_confidence_score CHECK (confidence_score BETWEEN 0 AND 1),
    CONSTRAINT ck_market_price_confidence_level CHECK (confidence_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_market_price_outliers_array CHECK (jsonb_typeof(outliers) = 'array'),
    CONSTRAINT ck_market_price_outliers_count CHECK (
        outliers_count = jsonb_array_length(outliers)
        AND outliers_count BETWEEN 0 AND sample_size
    ),
    CONSTRAINT ck_market_price_method_not_blank CHECK (btrim(method) <> '')
);

CREATE TABLE deal_score (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id uuid NOT NULL REFERENCES listing (id),
    market_price_id uuid NOT NULL REFERENCES market_price (id),
    discount_percent numeric(19, 2) NOT NULL,
    deal_score numeric(5, 2) NOT NULL,
    risk_score numeric(5, 2) NOT NULL,
    confidence numeric(5, 4) NOT NULL,
    flags jsonb NOT NULL DEFAULT '[]'::jsonb,
    explanation jsonb NOT NULL DEFAULT '[]'::jsonb,
    computed_at timestamptz NOT NULL,
    CONSTRAINT ck_deal_score_score_range CHECK (deal_score BETWEEN 0 AND 100),
    CONSTRAINT ck_deal_score_risk_score_range CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT ck_deal_score_confidence_range CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_deal_score_flags_array CHECK (jsonb_typeof(flags) = 'array'),
    CONSTRAINT ck_deal_score_explanation_array CHECK (jsonb_typeof(explanation) = 'array')
);

CREATE TABLE notification (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_score_id uuid NOT NULL REFERENCES deal_score (id),
    channel varchar(32) NOT NULL,
    dedup_key varchar(512) NOT NULL,
    status varchar(32) NOT NULL,
    sent_at timestamptz,
    CONSTRAINT uq_notification_channel_dedup_key UNIQUE (channel, dedup_key),
    CONSTRAINT ck_notification_channel_not_blank CHECK (btrim(channel) <> ''),
    CONSTRAINT ck_notification_dedup_key_not_blank CHECK (btrim(dedup_key) <> ''),
    CONSTRAINT ck_notification_status_not_blank CHECK (btrim(status) <> '')
);

CREATE INDEX idx_listing_seller_id ON listing (seller_id);
CREATE INDEX idx_listing_status_last_seen_at ON listing (status, last_seen_at);
CREATE INDEX idx_listing_price ON listing (price);
CREATE INDEX idx_search_listing_listing_id ON search_listing (listing_id);
CREATE INDEX idx_listing_price_history_listing_observed_at
    ON listing_price_history (listing_id, observed_at);
CREATE INDEX idx_listing_product_match_product_id ON listing_product_match (product_id);
CREATE INDEX idx_market_price_product_condition_computed_at
    ON market_price (product_id, condition_group, computed_at DESC);
CREATE INDEX idx_deal_score_listing_computed_at ON deal_score (listing_id, computed_at DESC);
CREATE INDEX idx_deal_score_score ON deal_score (deal_score DESC);
CREATE INDEX idx_notification_deal_score_id ON notification (deal_score_id);
