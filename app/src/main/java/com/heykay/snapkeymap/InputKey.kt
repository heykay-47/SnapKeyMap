package com.heykay.snapkeymap

data class InputKey(val code: String)

data class InputKeyEvent(val key: InputKey, val down: Boolean)

private val INPUT_LINE = Regex(
    """^\s*\[\s*\d+\.\d+\]\s+/dev/input/event\d+:\s+EV_KEY\s+(\w+)\s+(DOWN|UP)\s*$""",
)

/** getevent -lt uses explicit edges; repeats and other event types are not gestures. */
fun parseInputKeyEvent(line: String): InputKeyEvent? {
    val match = INPUT_LINE.matchEntire(line) ?: return null
    return InputKeyEvent(InputKey(match.groupValues[1]), match.groupValues[2] == "DOWN")
}
