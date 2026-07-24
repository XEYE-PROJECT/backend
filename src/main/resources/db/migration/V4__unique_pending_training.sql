-- At most ONE pending training per list, enforced by the database: concurrent element
-- edits fire concurrent async listeners, and an application-level check-then-insert lets
-- duplicates slip through. MariaDB has no partial unique indexes, so the classic trick:
-- a generated column that is the list_id only while the row is pending (NULL otherwise —
-- and NULLs never collide in a unique index).
ALTER TABLE trainings
    ADD COLUMN pending_list_id BIGINT GENERATED ALWAYS AS (IF(status = 'pending', list_id, NULL)) VIRTUAL,
    ADD UNIQUE KEY uq_trainings_pending_list (pending_list_id);
