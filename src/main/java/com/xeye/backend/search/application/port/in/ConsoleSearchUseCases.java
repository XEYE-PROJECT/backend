package com.xeye.backend.search.application.port.in;

import com.xeye.backend.search.application.command.ConsoleSearchCommand;
import com.xeye.backend.search.application.port.out.SearchQueryGateway.SearchQueryResult;

/**
 * Puerto de entrada del playground de la consola: la búsqueda va por el backend con la sesión
 * del usuario (JWT) y de ahí al buscador por la red interna, así la API key nunca vive en el
 * navegador. A diferencia de la API pública, sirve también las listas privadas del usuario.
 */
public interface ConsoleSearchUseCases {

    SearchQueryResult search(Long userId, Long listId, ConsoleSearchCommand command);
}
