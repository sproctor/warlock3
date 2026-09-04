import warlockfe.warlock3.compose.model.LiteralIgnore
import warlockfe.warlock3.compose.model.RegexIgnore
import warlockfe.warlock3.core.prefs.models.IgnoreMatchMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewIgnoreTest {
    @Test
    fun containsMatchesAnywhere() {
        val ignore = LiteralIgnore("orc", IgnoreMatchMode.CONTAINS, ignoreCase = false)
        assertTrue(ignore.matches("an orc arrives"))
        assertTrue(ignore.matches("a fierce orcish warrior"))
        assertFalse(ignore.matches("an ORC arrives"))
        assertFalse(ignore.matches("nothing here"))
    }

    @Test
    fun containsIgnoreCase() {
        val ignore = LiteralIgnore("orc", IgnoreMatchMode.CONTAINS, ignoreCase = true)
        assertTrue(ignore.matches("an ORC arrives"))
        assertTrue(ignore.matches("Orcish blade"))
    }

    @Test
    fun wordRequiresBoundaries() {
        val ignore = LiteralIgnore("orc", IgnoreMatchMode.WORD, ignoreCase = false)
        assertTrue(ignore.matches("an orc arrives"))
        assertTrue(ignore.matches("orc"))
        assertTrue(ignore.matches("the orc, snarling"))
        assertFalse(ignore.matches("a fierce orcish warrior"))
        assertFalse(ignore.matches("a dorc"))
    }

    @Test
    fun wordMatchesMultiWordLiteral() {
        val ignore = LiteralIgnore("greater orc", IgnoreMatchMode.WORD, ignoreCase = false)
        assertTrue(ignore.matches("a greater orc arrives"))
        assertFalse(ignore.matches("a greater orcish warrior"))
    }

    @Test
    fun wordIgnoreCase() {
        val ignore = LiteralIgnore("orc", IgnoreMatchMode.WORD, ignoreCase = true)
        assertTrue(ignore.matches("an ORC arrives"))
        assertFalse(ignore.matches("an ORCISH warrior"))
    }

    @Test
    fun lineRequiresExactMatch() {
        val ignore = LiteralIgnore("an orc arrives", IgnoreMatchMode.LINE, ignoreCase = false)
        assertTrue(ignore.matches("an orc arrives"))
        assertFalse(ignore.matches("an orc arrives."))
        assertFalse(ignore.matches("suddenly, an orc arrives"))
        assertFalse(ignore.matches("An Orc Arrives"))
    }

    @Test
    fun lineIgnoreCase() {
        val ignore = LiteralIgnore("an orc arrives", IgnoreMatchMode.LINE, ignoreCase = true)
        assertTrue(ignore.matches("An Orc Arrives"))
        assertFalse(ignore.matches("An Orc Arrives!"))
    }

    @Test
    fun regexMatchesAnywhere() {
        val ignore = RegexIgnore(Regex("or[ck]"))
        assertTrue(ignore.matches("an orc arrives"))
        assertTrue(ignore.matches("pork chops"))
        assertFalse(ignore.matches("an ORC arrives"))
    }

    @Test
    fun regexWithIgnoreCaseOption() {
        val ignore = RegexIgnore(Regex("orc", RegexOption.IGNORE_CASE))
        assertTrue(ignore.matches("an ORC arrives"))
    }

    @Test
    fun regexCanAnchorEntireLine() {
        val ignore = RegexIgnore(Regex("^an orc arrives$"))
        assertTrue(ignore.matches("an orc arrives"))
        assertFalse(ignore.matches("suddenly, an orc arrives"))
    }
}
