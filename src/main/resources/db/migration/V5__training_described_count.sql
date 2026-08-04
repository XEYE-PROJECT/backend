-- Nº de elementos que tenían descripción LLM (caché + generadas en el run) en el momento de
-- calcular los embeddings. Puede ser menor que el nº de elementos si el LLM falló en algunos.
ALTER TABLE trainings
    ADD COLUMN described_count INT NULL AFTER element_ids;
