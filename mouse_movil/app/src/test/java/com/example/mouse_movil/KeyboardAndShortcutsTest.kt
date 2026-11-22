package com.example.mouse_movil

import com.example.mouse_movil.data.model.Shortcut
import com.example.mouse_movil.ui.keyboard.KeyboardDiffAction
import com.example.mouse_movil.ui.keyboard.RealtimeKeyboardDiffer
import com.example.mouse_movil.ui.shortcuts.ShortcutValidator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardAndShortcutsTest {

    @Test
    fun keyboardDiffer_typingSingleCharactersAndUnicode() {
        val actions = RealtimeKeyboardDiffer.diff(" ", " Hola 😀")
        assertEquals(listOf(KeyboardDiffAction.SendText("Hola 😀")), actions)
    }

    @Test
    fun keyboardDiffer_backspaceFromEmptySentinel_emitsSingleBackspace() {
        val actions = RealtimeKeyboardDiffer.diff(" ", "")
        assertEquals(listOf(KeyboardDiffAction.SendBackspaces(1)), actions)
    }

    @Test
    fun keyboardDiffer_predictiveImeWordReplacement_emitsBackspacesThenNewText() {
        val actions = RealtimeKeyboardDiffer.diff(" hoka", " hola")
        assertEquals(
            listOf(
                KeyboardDiffAction.SendBackspaces(2),
                KeyboardDiffAction.SendText("la")
            ),
            actions
        )
    }

    @Test
    fun keyboardDiffer_newline_emitsEnter() {
        val actions = RealtimeKeyboardDiffer.diff(" abc", " abc\n")
        assertEquals(listOf(KeyboardDiffAction.SendEnter), actions)
    }

    @Test
    fun shortcutValidator_checksNameAndMainKey() {
        assertTrue(ShortcutValidator.isValid("Copiar", listOf("CTRL", "C")))
        assertTrue(ShortcutValidator.isValid("Inicio", listOf("WIN")))
        assertFalse(ShortcutValidator.isValid("", listOf("CTRL", "C")))
        assertFalse(ShortcutValidator.isValid("Solo modificador", listOf("CTRL", "SHIFT")))
    }

    @Test
    fun shortcutSerialization_roundtripsCleanly() {
        val sc = Shortcut("id-1", "Admin tareas", "Terminal", listOf("CTRL", "SHIFT", "ESC"))
        val raw = Json.encodeToString(Shortcut.serializer(), sc)
        val decoded = Json.decodeFromString(Shortcut.serializer(), raw)
        assertEquals(sc, decoded)
    }
}
