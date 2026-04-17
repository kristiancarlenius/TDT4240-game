package com.mygame.server.application.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR4   — Players shall enter a username before joining.
 * FR4.1 — Players shall be able to choose their own username.
 */
class HandleJoinUseCaseTest {

    // FR4 — username required
    @Test
    void sanitise_nullUsername_returnsNull() {
        assertNull(HandleJoinUseCase.sanitise(null));
    }

    @Test
    void sanitise_emptyUsername_returnsNull() {
        assertNull(HandleJoinUseCase.sanitise(""));
    }

    @Test
    void sanitise_onlyWhitespace_returnsNull() {
        assertNull(HandleJoinUseCase.sanitise("   "));
    }

    // FR4.1 — player can choose username
    @Test
    void sanitise_validUsername_preserved() {
        assertEquals("Fermin", HandleJoinUseCase.sanitise("Fermin"));
    }

    @Test
    void sanitise_allowsAlphanumericAndUnderscoreAndHyphen() {
        assertEquals("Player_1-ok", HandleJoinUseCase.sanitise("Player_1-ok"));
    }

    @Test
    void sanitise_stripsDisallowedCharacters() {
        String result = HandleJoinUseCase.sanitise("H@ck<er>!");
        assertNotNull(result);
        assertFalse(result.contains("@"));
        assertFalse(result.contains("<"));
        assertFalse(result.contains("!"));
    }

    @Test
    void sanitise_trimsSurroundingWhitespace() {
        assertEquals("Player", HandleJoinUseCase.sanitise("  Player  "));
    }

    @Test
    void sanitise_collapsesInternalWhitespace() {
        assertEquals("a b", HandleJoinUseCase.sanitise("a   b"));
    }

    @Test
    void sanitise_truncatesAtTwentyCharacters() {
        String long21 = "abcdefghijklmnopqrstu"; // 21 chars
        String result = HandleJoinUseCase.sanitise(long21);
        assertNotNull(result);
        assertTrue(result.length() <= 20);
    }

    @Test
    void sanitise_acceptsExactlyTwentyCharacters() {
        String exact20 = "abcdefghijklmnopqrst"; // 20 chars
        assertEquals(exact20, HandleJoinUseCase.sanitise(exact20));
    }

    @Test
    void sanitise_singleCharacter_valid() {
        assertEquals("A", HandleJoinUseCase.sanitise("A"));
    }
}
