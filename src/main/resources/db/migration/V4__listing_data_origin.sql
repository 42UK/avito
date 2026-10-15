ALTER TABLE listing
    ADD COLUMN data_origin varchar(16) NOT NULL DEFAULT 'DEMO';

UPDATE listing
SET data_origin = CASE
    WHEN source = 'avito' THEN 'REAL'
    ELSE 'DEMO'
END;

ALTER TABLE listing
    ADD CONSTRAINT ck_listing_data_origin
        CHECK (data_origin IN ('REAL', 'IMPORTED', 'DEMO'));

CREATE INDEX idx_listing_data_origin_status
    ON listing (data_origin, status);
