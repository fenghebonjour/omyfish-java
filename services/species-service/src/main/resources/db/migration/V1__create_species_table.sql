CREATE SCHEMA IF NOT EXISTS species;

CREATE OR REPLACE FUNCTION species.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE species.species (
    id                          UUID PRIMARY KEY,
    scientific_name             VARCHAR(255) NOT NULL UNIQUE,
    common_name                 VARCHAR(255) NOT NULL,
    family                      VARCHAR(255),
    conservation_status         VARCHAR(50),
    habitat                     TEXT,
    geographic_range             TEXT,
    description                 TEXT,
    is_north_american_freshwater BOOLEAN NOT NULL DEFAULT FALSE,
    image_url                   VARCHAR(512),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_species_scientific_name ON species.species (scientific_name);
CREATE INDEX idx_species_common_name     ON species.species (common_name);
CREATE INDEX idx_species_conservation    ON species.species (conservation_status);
CREATE INDEX idx_species_na_freshwater   ON species.species (is_north_american_freshwater) WHERE is_north_american_freshwater = TRUE;

CREATE TABLE species.predictions (
    id                  UUID PRIMARY KEY,
    species_id          UUID REFERENCES species.species(id),
    scientific_name     VARCHAR(255) NOT NULL,
    image_storage_key   VARCHAR(512) NOT NULL,
    confidence          DOUBLE PRECISION NOT NULL,
    rank                INT NOT NULL DEFAULT 1,
    predicted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_predictions_species   ON species.predictions (species_id);
CREATE INDEX idx_predictions_image_key ON species.predictions (image_storage_key);
CREATE INDEX idx_predictions_date      ON species.predictions (predicted_at DESC);

CREATE TRIGGER trg_species_updated_at
    BEFORE UPDATE ON species.species
    FOR EACH ROW EXECUTE FUNCTION species.update_updated_at_column();
