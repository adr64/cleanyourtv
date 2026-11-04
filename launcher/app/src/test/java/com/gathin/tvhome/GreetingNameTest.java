package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The greeting name typed in Launcher settings, and how it overrides a theme's greeting_name. */
public class GreetingNameTest {
    @Test public void nothingSavedKeepsTheThemesName() {
        assertEquals("Mr. Trịnh", Config.chosenGreetingName(null, "Mr. Trịnh"));
        assertEquals("", Config.chosenGreetingName(null, ""));
    }

    @Test public void aSavedNameWinsOverTheTheme() {
        assertEquals("Adrián", Config.chosenGreetingName("Adrián", "Mr. Trịnh"));
    }

    @Test public void anEmptySavedNameMeansTheGreetingAlone() {
        assertEquals("", Config.chosenGreetingName("", "Mr. Trịnh"));
        assertEquals("", Config.chosenGreetingName("   ", "Mr. Trịnh"));
    }

    @Test public void theTypedNameIsTrimmedAndCleaned() {
        assertEquals("Adrián", Config.cleanGreetingName("  Adrián \n"));
        assertEquals("Ana María", Config.cleanGreetingName("Ana\tMaría"));
        assertEquals("", Config.cleanGreetingName(null));
    }

    @Test public void aLongNameIsCut() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 60; i++) b.append('a');
        assertEquals(Config.GREETING_NAME_MAX, Config.cleanGreetingName(b.toString()).length());
    }
}
