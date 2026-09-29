-- Sección D del backlog: opt-out por lista del LLM.
-- TRUE (por defecto) = los entrenamientos pueden generar descripciones con un modelo de lenguaje;
-- FALSE = el dueño renuncia: la lista se entrena siempre sin paso LLM y el worker recibe
-- llm_enrichment=false en el job, así que sus textos no salen hacia ningún modelo.
ALTER TABLE lists ADD COLUMN llm_enrichment BOOLEAN NOT NULL DEFAULT TRUE AFTER is_public;
