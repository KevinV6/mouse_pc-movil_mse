package com.example.mouse_movil.ui.keyboard

sealed interface KeyboardDiffAction {
    data class SendText(val text: String) : KeyboardDiffAction
    data class SendBackspaces(val count: Int) : KeyboardDiffAction
    data object SendEnter : KeyboardDiffAction
}

object RealtimeKeyboardDiffer {
    const val SENTINEL = " "

    /**
     * Computes incremental protocol actions between [previous] and [current] buffer states.
     * Supports predictive IME replacements as well as single backspace and newline detection.
     */
    fun diff(previous: String, current: String): List<KeyboardDiffAction> {
        if (previous == current) return emptyList()

        // If user pressed Backspace on the sentinel empty state
        if (previous == SENTINEL && current.isEmpty()) {
            return listOf(KeyboardDiffAction.SendBackspaces(1))
        }

        val prevContent = previous.removePrefix(SENTINEL)
        val currContent = current.removePrefix(SENTINEL)

        var commonPrefix = 0
        val minLen = minOf(prevContent.length, currContent.length)
        while (commonPrefix < minLen && prevContent[commonPrefix] == currContent[commonPrefix]) {
            commonPrefix++
        }

        val actions = mutableListOf<KeyboardDiffAction>()
        val deletedCount = prevContent.length - commonPrefix
        if (deletedCount > 0) {
            actions.add(KeyboardDiffAction.SendBackspaces(deletedCount))
        }

        val added = currContent.substring(commonPrefix)
        if (added.isNotEmpty()) {
            val segments = added.split('\n')
            for ((index, seg) in segments.withIndex()) {
                val cleanSeg = seg.replace("\r", "")
                if (cleanSeg.isNotEmpty()) {
                    actions.add(KeyboardDiffAction.SendText(cleanSeg))
                }
                if (index < segments.lastIndex) {
                    actions.add(KeyboardDiffAction.SendEnter)
                }
            }
        }

        return actions
    }
}
