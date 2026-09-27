package com.xeye.backend.search.application.service;

import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.search.application.command.ConsoleSearchCommand;
import com.xeye.backend.search.application.port.out.SearchQueryGateway;
import com.xeye.backend.search.application.port.out.SearchQueryGateway.Hit;
import com.xeye.backend.search.application.port.out.SearchQueryGateway.SearchQueryResult;
import com.xeye.backend.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El playground solo busca en listas del usuario autenticado; la visibilidad no importa. */
class ConsoleSearchServiceTest {

    private static final long OWNER = 7L;
    private static final long LIST_ID = 3L;

    private final ListQueryPort lists = mock(ListQueryPort.class);
    private final SearchQueryGateway gateway = mock(SearchQueryGateway.class);
    private final ConsoleSearchService service = new ConsoleSearchService(lists, gateway);
    private final ConsoleSearchCommand command = new ConsoleSearchCommand("cámara", 10, true);

    @Test
    void privateListOfTheOwnerIsSearchable() {
        when(lists.findById(LIST_ID)).thenReturn(Optional.of(list(OWNER, false)));
        SearchQueryResult expected = new SearchQueryResult(
                List.of(new Hit("Cámara réflex", 0.93, null, 0.5, 1.0)), 1, "cámara", "Cámaras", 12);
        when(gateway.search(LIST_ID, command)).thenReturn(expected);

        assertEquals(expected, service.search(OWNER, LIST_ID, command));
    }

    @Test
    void someoneElsesListIs404AndNeverReachesTheSearchService() {
        when(lists.findById(LIST_ID)).thenReturn(Optional.of(list(99L, true)));

        assertThrows(NotFoundException.class, () -> service.search(OWNER, LIST_ID, command));
        verify(gateway, never()).search(anyLong(), any());
    }

    @Test
    void unknownListIs404() {
        when(lists.findById(LIST_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.search(OWNER, LIST_ID, command));
        verify(gateway, never()).search(anyLong(), any());
    }

    private static ItemList list(long userId, boolean isPublic) {
        return new ItemList(LIST_ID, "Cámaras", null, isPublic, userId, null, null);
    }
}
