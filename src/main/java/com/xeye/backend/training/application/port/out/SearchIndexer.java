package com.xeye.backend.training.application.port.out;

import com.xeye.backend.training.application.command.SearchIndexCommand;

/** Puerto de salida que empuja los elementos/embeddings de una lista completada a búsqueda. */
public interface SearchIndexer {

    void index(SearchIndexCommand command);
}
