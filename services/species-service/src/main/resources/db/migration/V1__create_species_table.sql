CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE species (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scientific_name             VARCHAR(255) NOT NULL UNIQUE,
    common_name                 VARCHAR(255) NOT NULL,
    family                      VARCHAR(255),
    conservation_status         VARCHAR(50),   -- LC, NT, VU, EN, CR, EW, EX
    habitat                     TEXT,
    geographic_range             TEXT,
    description                 TEXT,
    is_north_american_freshwater BOOLEAN NOT NULL DEFAULT FALSE,
    image_url                   VARCHAR(512),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_species_scientific_name ON species (scientific_name);
CREATE INDEX idx_species_common_name     ON species (common_name);
CREATE INDEX idx_species_conservation    ON species (conservation_status);
CREATE INDEX idx_species_na_freshwater   ON species (is_north_american_freshwater) WHERE is_north_american_freshwater = TRUE;

-- AI prediction results (for audit / retraining)
CREATE TABLE predictions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    species_id          UUID REFERENCES species(id),
    scientific_name     VARCHAR(255) NOT NULL,   -- snapshot in case species row changes
    image_storage_key   VARCHAR(512) NOT NULL,
    confidence          DOUBLE PRECISION NOT NULL,
    rank                INT NOT NULL DEFAULT 1,
    predicted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_predictions_species   ON predictions (species_id);
CREATE INDEX idx_predictions_image_key ON predictions (image_storage_key);
CREATE INDEX idx_predictions_date      ON predictions (predicted_at DESC);

CREATE TRIGGER trg_species_updated_at
    BEFORE UPDATE ON species
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
