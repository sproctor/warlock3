package warlockfe.warlock3.compose.macros

import androidx.compose.ui.input.key.Key

/**
 * Comprehensive mapping of string codes to androidx.compose.ui.input.key.Key constants
 * for Compose Multiplatform keyboard handling.
 */
object KeyboardKeyMappings {

    val keyMap = mapOf(
        // Letters (A-Z)
        "A" to Key.A,
        "B" to Key.B,
        "C" to Key.C,
        "D" to Key.D,
        "E" to Key.E,
        "F" to Key.F,
        "G" to Key.G,
        "H" to Key.H,
        "I" to Key.I,
        "J" to Key.J,
        "K" to Key.K,
        "L" to Key.L,
        "M" to Key.M,
        "N" to Key.N,
        "O" to Key.O,
        "P" to Key.P,
        "Q" to Key.Q,
        "R" to Key.R,
        "S" to Key.S,
        "T" to Key.T,
        "U" to Key.U,
        "V" to Key.V,
        "W" to Key.W,
        "X" to Key.X,
        "Y" to Key.Y,
        "Z" to Key.Z,

        // Numbers (0-9)
        "ZERO" to Key.Zero,
        "ONE" to Key.One,
        "TWO" to Key.Two,
        "THREE" to Key.Three,
        "FOUR" to Key.Four,
        "FIVE" to Key.Five,
        "SIX" to Key.Six,
        "SEVEN" to Key.Seven,
        "EIGHT" to Key.Eight,
        "NINE" to Key.Nine,

        // Function Keys (F1-F12)
        "F1" to Key.F1,
        "F2" to Key.F2,
        "F3" to Key.F3,
        "F4" to Key.F4,
        "F5" to Key.F5,
        "F6" to Key.F6,
        "F7" to Key.F7,
        "F8" to Key.F8,
        "F9" to Key.F9,
        "F10" to Key.F10,
        "F11" to Key.F11,
        "F12" to Key.F12,

        // Navigation Keys
        "UP" to Key.DirectionUp,
        "DOWN" to Key.DirectionDown,
        "LEFT" to Key.DirectionLeft,
        "RIGHT" to Key.DirectionRight,
        "PAGE_UP" to Key.PageUp,
        "PAGE_DOWN" to Key.PageDown,
        "HOME" to Key.Home,
        "END" to Key.MoveEnd,

        // Editing Keys
        "ENTER" to Key.Enter,
        "BACKSPACE" to Key.Backspace,
        "DELETE" to Key.Delete,
        "INSERT" to Key.Insert,
        "TAB" to Key.Tab,
        "ESCAPE" to Key.Escape,
        "SPACE" to Key.Spacebar,

        // Modifier Keys
        "SHIFT_LEFT" to Key.ShiftLeft,
        "SHIFT_RIGHT" to Key.ShiftRight,
        "CTRL_LEFT" to Key.CtrlLeft,
        "CTRL_RIGHT" to Key.CtrlRight,
        "ALT_LEFT" to Key.AltLeft,
        "ALT_RIGHT" to Key.AltRight,
        "META_LEFT" to Key.MetaLeft,
        "META_RIGHT" to Key.MetaRight,
        "CAPS_LOCK" to Key.CapsLock,
        "NUM_LOCK" to Key.NumLock,
        "SCROLL_LOCK" to Key.ScrollLock,

        // Numpad Keys
        "NUMPAD_0" to Key.NumPad0,
        "NUMPAD_1" to Key.NumPad1,
        "NUMPAD_2" to Key.NumPad2,
        "NUMPAD_3" to Key.NumPad3,
        "NUMPAD_4" to Key.NumPad4,
        "NUMPAD_5" to Key.NumPad5,
        "NUMPAD_6" to Key.NumPad6,
        "NUMPAD_7" to Key.NumPad7,
        "NUMPAD_8" to Key.NumPad8,
        "NUMPAD_9" to Key.NumPad9,
        "NUMPAD_DIVIDE" to Key.NumPadDivide,
        "NUMPAD_MULTIPLY" to Key.NumPadMultiply,
        "NUMPAD_SUBTRACT" to Key.NumPadSubtract,
        "NUMPAD_ADD" to Key.NumPadAdd,
        "NUMPAD_DOT" to Key.NumPadDot,
        "NUMPAD_COMMA" to Key.NumPadComma,
        "NUMPAD_ENTER" to Key.NumPadEnter,
        "NUMPAD_EQUALS" to Key.NumPadEquals,
        "NUMPAD_LEFT_PAREN" to Key.NumPadLeftParenthesis,
        "NUMPAD_RIGHT_PAREN" to Key.NumPadRightParenthesis,

        // Punctuation and Symbols
        "GRAVE" to Key.Grave,
        "MINUS" to Key.Minus,
        "EQUALS" to Key.Equals,
        "LEFT_BRACKET" to Key.LeftBracket,
        "RIGHT_BRACKET" to Key.RightBracket,
        "BACKSLASH" to Key.Backslash,
        "SEMICOLON" to Key.Semicolon,
        "APOSTROPHE" to Key.Apostrophe,
        "COMMA" to Key.Comma,
        "PERIOD" to Key.Period,
        "SLASH" to Key.Slash,
        "PLUS" to Key.Plus,
        "ASTERISK" to Key.Multiply,
        "POUND" to Key.Pound,
        "AT" to Key.At,

        // Media Keys
        "VOLUME_UP" to Key.VolumeUp,
        "VOLUME_DOWN" to Key.VolumeDown,
        "VOLUME_MUTE" to Key.VolumeMute,
        "MEDIA_PLAY" to Key.MediaPlay,
        "MEDIA_PAUSE" to Key.MediaPause,
        "MEDIA_PLAY_PAUSE" to Key.MediaPlayPause,
        "MEDIA_STOP" to Key.MediaStop,
        "MEDIA_NEXT" to Key.MediaNext,
        "MEDIA_PREVIOUS" to Key.MediaPrevious,
        "MEDIA_REWIND" to Key.MediaRewind,
        "MEDIA_FAST_FORWARD" to Key.MediaFastForward,
        "MEDIA_RECORD" to Key.MediaRecord,

        // System Keys
        "PRINT_SCREEN" to Key.PrintScreen,
        "BREAK" to Key.Break,
        "MENU" to Key.Menu,
        "FUNCTION" to Key.Function,

        // Application Keys
        "BACK" to Key.Back,
        "FORWARD" to Key.Forward,
        "REFRESH" to Key.Refresh,
        "BROWSER" to Key.Browser,
        "SEARCH" to Key.Search,
        "BROWSER_HOME" to Key.Home,

        // Application Control Keys
        "APP_SWITCH" to Key.AppSwitch,
        "HELP" to Key.Help,
        "POWER" to Key.Power,
        "SLEEP" to Key.Sleep,
        "WAKEUP" to Key.WakeUp,

        // Additional Keys
        "COPY" to Key.Copy,
        "PASTE" to Key.Paste,
        "CUT" to Key.Cut,
        "CLEAR" to Key.Clear,

        // International Keys
        "YEN" to Key.Yen,
        "RO" to Key.Ro,
        "KANA" to Key.Kana,
        "EISU" to Key.Eisu,
        "HENKAN" to Key.Henkan,
        "MUHENKAN" to Key.Muhenkan,
        "KATAKANA_HIRAGANA" to Key.KatakanaHiragana,

        // Zoom Keys
        "ZOOM_IN" to Key.ZoomIn,
        "ZOOM_OUT" to Key.ZoomOut,

        // Channel/TV Keys
        "CHANNEL_UP" to Key.ChannelUp,
        "CHANNEL_DOWN" to Key.ChannelDown,

        // Directional Center
        "DPAD_CENTER" to Key.DirectionCenter,

        // Call Keys
        "CALL" to Key.Call,
        "END_CALL" to Key.EndCall,

        // Additional Navigation
        "MOVE_HOME" to Key.MoveHome,

        // Game Controller Buttons
        "BUTTON_A" to Key.ButtonA,
        "BUTTON_B" to Key.ButtonB,
        "BUTTON_C" to Key.ButtonC,
        "BUTTON_X" to Key.ButtonX,
        "BUTTON_Y" to Key.ButtonY,
        "BUTTON_Z" to Key.ButtonZ,
        "BUTTON_L1" to Key.ButtonL1,
        "BUTTON_L2" to Key.ButtonL2,
        "BUTTON_R1" to Key.ButtonR1,
        "BUTTON_R2" to Key.ButtonR2,
        "BUTTON_THUMBL" to Key.ButtonThumbLeft,
        "BUTTON_THUMBR" to Key.ButtonThumbRight,
        "BUTTON_START" to Key.ButtonStart,
        "BUTTON_SELECT" to Key.ButtonSelect,
        "BUTTON_MODE" to Key.ButtonMode,

        // Additional Control Keys
        "CTRL" to Key.CtrlLeft, // Generic Ctrl
        "ALT" to Key.AltLeft, // Generic Alt
        "SHIFT" to Key.ShiftLeft, // Generic Shift
        "META" to Key.MetaLeft, // Generic Meta/Windows/Command
    )

    /**
     * Reverse mapping from Key to string code
     */
    val reverseKeyMap = keyMap.entries.associate { (k, v) -> v to k }

    /**
     * Mapping from string code to key code constant
     */
    val keyCodeMap = keyMap.entries.associate { (k, v) -> k to v.keyCode }

    /**
     * Reverse mapping from key code constant to string code
     */
    val reverseKeyCodeMap = keyCodeMap.entries.associate { (k, v) -> v to k }

    /**
     * Get Key from string code
     */
    fun getKey(code: String): Key? = keyMap[code]

    /**
     * Get string code from Key
     */
    fun getCode(key: Key): String? = reverseKeyMap[key]

    /**
     * Check if a string code is valid
     */
    fun isValidCode(code: String): Boolean = code in keyMap

    /**
     * Get all available key codes
     */
    fun getAllCodes(): Set<String> = keyMap.keys

    /**
     * Get all available Keys
     */
    fun getAllKeys(): Set<Key> = keyMap.values.toSet()
}
