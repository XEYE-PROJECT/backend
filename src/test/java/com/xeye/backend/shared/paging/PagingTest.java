package com.xeye.backend.shared.paging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagingTest {

    @Test
    void defaultsAndClamping() {
        Paging p = Paging.of(null, null);
        assertEquals(0, p.offset());
        assertEquals(Paging.DEFAULT_LIMIT, p.limit());

        assertEquals(Paging.MAX_LIMIT, Paging.of(0, 10_000).limit());
        assertEquals(1, Paging.of(0, 0).limit());
        assertEquals(0, Paging.of(-5, 10).offset());
        assertEquals(20, Paging.of(0, 100, 20).limit());
    }

    @Test
    void pageNumberRoundsDownToTheLimit() {
        assertEquals(2, Paging.of(50, 25).pageNumber());
        assertEquals(1, Paging.of(30, 25).pageNumber());
    }
}
