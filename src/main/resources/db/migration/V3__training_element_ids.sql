-- The element ids captured when the training launched (id ASC). Row i of the
-- embeddings matrix corresponds to element_ids[i], so the search service can align
-- vectors by id even when elements were added/removed while the training ran.
ALTER TABLE trainings
    ADD COLUMN element_ids JSON NULL AFTER options;
