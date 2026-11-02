CREATE TABLE supplements (
 id UUID PRIMARY KEY,
 name VARCHAR(120) NOT NULL, brand VARCHAR(120) NOT NULL,
 category VARCHAR(30) NOT NULL CHECK(category IN ('PROTEINA','CREATINA','PREENTRENO','AMINOACIDOS','VITAMINAS','OTROS')),
 presentation VARCHAR(120) NOT NULL, net_content VARCHAR(100) NOT NULL, flavor VARCHAR(100) NOT NULL DEFAULT '',
 description VARCHAR(4000) NOT NULL, ingredients VARCHAR(2000) NOT NULL DEFAULT '',
 price_pen NUMERIC(12,2) NOT NULL CHECK(price_pen>0),
 availability VARCHAR(20) NOT NULL CHECK(availability IN ('DISPONIBLE','AGOTADO')),
 published BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 1,
 created_at_utc TIMESTAMP NOT NULL, updated_at_utc TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX uk_supplement_presentation ON supplements(lower(name),lower(brand),lower(presentation),lower(net_content),lower(flavor));
CREATE TABLE supplement_images (
 id UUID PRIMARY KEY, supplement_id UUID NOT NULL REFERENCES supplements(id),
 filename VARCHAR(160) NOT NULL, media_type VARCHAR(30) NOT NULL,
 bytes BYTEA NOT NULL, thumbnail BYTEA NOT NULL, sha256 VARCHAR(64) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, position INT NOT NULL,
 CHECK(octet_length(bytes)<=5242880)
);
CREATE INDEX idx_supplement_images ON supplement_images(supplement_id,active,position);
CREATE TABLE supplement_changes (
 id UUID PRIMARY KEY, supplement_id UUID NOT NULL REFERENCES supplements(id),
 version BIGINT NOT NULL, actor VARCHAR(150) NOT NULL, changed_at_utc TIMESTAMP NOT NULL,
 snapshot JSONB NOT NULL, UNIQUE(supplement_id,version)
);
CREATE TABLE supplement_promotions (
 id UUID PRIMARY KEY, supplement_id UUID NOT NULL REFERENCES supplements(id),
 name VARCHAR(160) NOT NULL, description VARCHAR(2000) NOT NULL, conditions VARCHAR(2000) NOT NULL,
 reference_price_pen NUMERIC(12,2) NOT NULL CHECK(reference_price_pen>0),
 offer_price_pen NUMERIC(12,2) NOT NULL CHECK(offer_price_pen>0 AND offer_price_pen<reference_price_pen),
 start_date DATE NOT NULL, end_date DATE NOT NULL CHECK(end_date>start_date),
 enabled BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 1,
 created_at_utc TIMESTAMP NOT NULL, updated_at_utc TIMESTAMP NOT NULL
);
CREATE INDEX idx_supplement_promotion_dates ON supplement_promotions(supplement_id,start_date,end_date) WHERE enabled;
CREATE TABLE promotion_changes (
 id UUID PRIMARY KEY, promotion_id UUID NOT NULL REFERENCES supplement_promotions(id),
 version BIGINT NOT NULL, actor VARCHAR(150) NOT NULL, changed_at_utc TIMESTAMP NOT NULL,
 snapshot JSONB NOT NULL, UNIQUE(promotion_id,version)
);
