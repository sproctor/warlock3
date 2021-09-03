package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.Script
import cc.warlock.warlock3.core.parser.WslLexer
import cc.warlock.warlock3.core.parser.WslParser
import cc.warlock.warlock3.core.parser.WslParser.EQ
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.io.FileReader
import java.math.BigDecimal

class WslScript(
    override val name: String,
    private val file: File,
) : Script {
    private var _isRunning = false
    override val isRunning: Boolean
        get() = _isRunning

    override fun start(arguments: List<String>) {
        val reader = FileReader(file)
        val input: CharStream = CaseChangingCharStream(CharStreams.fromReader(reader))
        val lexer = WslLexer(input)
        val parser = WslParser(CommonTokenStream(lexer))
        val script = parser.script()
        val lines = script.line().map { parseLine(it) }
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun suspend() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }

    private fun parseLine(line: WslParser.LineContext): WslLine {
        val label = line.Label()?.text
        val statement = parseStatement(line.statement())
        return WslLine(label, statement)
    }

    private fun parseStatement(statement: WslParser.StatementContext): WslStatement {
        val expression = statement.expression()
        return if (expression != null) {
            WslStatement.WithCondition(
                parseExpression(expression),
                parseStatement(statement.statement())
            )
        } else {
            parseCommand(statement.command())
        }
    }

    private fun parseExpression(expression: WslParser.ExpressionContext): WslExpression {
        return WslExpression(parseDisjunction(expression.disjunction()))
    }

    private fun parseCommand(command: WslParser.CommandContext): WslStatement.WslCommand {
        return WslStatement.WslCommand(
            contents = command.commandContent().map { parseCommandContent(it) }
        )
    }

    private fun parseCommandContent(commandContent: WslParser.CommandContentContext): WslCommandContent {
        commandContent.CommandText()?.let { return WslCommandContent.Text(it.text) }
        commandContent.CommandRef()?.let { return WslCommandContent.Variable(it.text) }
        throw WslParseException("Unhandled command content alternative")
    }

    private fun parseDisjunction(disjunction: WslParser.DisjunctionContext): WslDisjunction {
        return WslDisjunction(
            conjunctions = disjunction.conjunction().map { parseConjunction(it) }
        )
    }

    private fun parseConjunction(conjunction: WslParser.ConjunctionContext): WslConjunction {
        return WslConjunction(
            equalities = conjunction.equality().map { parseEquality(it) }
        )
    }

    private fun parseEquality(equality: WslParser.EqualityContext): WslEquality {
        return WslEquality(
            comparison = parseComparison(equality.comparison(0)),
            otherComparisons = equality.equalityOperator().mapIndexed { index, context ->
                val operator = when {
                    context.EQ() != null -> WslEqualityOperator.EQ
                    context.NEQ() != null -> WslEqualityOperator.NEQ
                    else -> throw WslParseException("Unhandled equality operator")
                }
                Pair(operator, parseComparison(equality.comparison((index + 1))))
            }
        )
    }

    private fun parseComparison(comparison: WslParser.ComparisonContext): WslComparison {
        return WslComparison(
            infixExpression = parseInfixExpression(comparison.infixExpression(0)),
            otherInfixExpressions = comparison.comparisonOperator().mapIndexed { index, context ->
                val operator = when {
                    context.GT() != null -> WslComparisonOperator.GT
                    context.LT() != null -> WslComparisonOperator.LT
                    context.GTE() != null -> WslComparisonOperator.GTE
                    context.LTE() != null -> WslComparisonOperator.LTE
                    else -> throw WslParseException("Unhandled comparison operator")
                }
                Pair(operator, parseInfixExpression(comparison.infixExpression(index + 1)))
            }
        )
    }

    private fun parseInfixExpression(infixExpression: WslParser.InfixExpressionContext): WslInfixExpression {
        return WslInfixExpression(
            additiveExpression = parseAdditiveExpression(infixExpression.additiveExpression(0)),
            otherAdditiveExpressions = infixExpression.infixOperator().mapIndexed { index, context ->
                val operator = when {
                    context.CONTAINS() != null -> WslInfixOperator.CONTAINS
                    context.CONTAINSRE() != null -> WslInfixOperator.CONTAINSRE
                    else -> throw WslParseException("Unhandled infix operator")
                }
                Pair(operator, parseAdditiveExpression(infixExpression.additiveExpression(index + 1)))
            }
        )
    }

    private fun parseAdditiveExpression(
        additiveExpression: WslParser.AdditiveExpressionContext
    ): WslAdditiveExpression {
        return WslAdditiveExpression(
            multiplicativeExpression = parseMultiplicativeExpression(additiveExpression.multiplicativeExpression(0)),
            otherMultiplicativeExpressions = additiveExpression.additiveOperator().mapIndexed { index, opContext ->
                val operator = when {
                    opContext.ADD() != null -> WslAdditiveOperator.ADD
                    opContext.SUB() != null -> WslAdditiveOperator.SUB
                    else -> throw WslParseException("Unhandled additive operator")
                }
                Pair(operator, parseMultiplicativeExpression(additiveExpression.multiplicativeExpression(index + 1)))
            }
        )
    }

    private fun parseMultiplicativeExpression(
        multiplicativeExpression: WslParser.MultiplicativeExpressionContext
    ): WslMultiplicativeExpression {
        return WslMultiplicativeExpression(
            unaryExpression = parseUnaryExpression(multiplicativeExpression.unaryExpression(0)),
            otherUnaryExpressions = multiplicativeExpression.multiplicativeOperator().mapIndexed { index, opContext ->
                val operator = when {
                    opContext.MULT() != null -> WslMultiplicativeOperator.MULT
                    opContext.DIV() != null -> WslMultiplicativeOperator.DIV
                    else -> throw WslParseException("Unhandled multiplicative operator")
                }
                Pair(operator, parseUnaryExpression(multiplicativeExpression.unaryExpression(index + 1)))
            }
        )
    }

    private fun parseUnaryExpression(
        unaryExpression: WslParser.UnaryExpressionContext
    ): WslUnaryExpression {
        unaryExpression.unaryOperator()?.let { opContext ->
            val operator = when {
                opContext.EXISTS() != null -> WslUnaryOperator.EXISTS
                opContext.NOT() != null -> WslUnaryOperator.NOT
                else -> throw WslParseException("Unhandled unary operator")
            }
            return WslUnaryExpression.WithOperator(
                operator = operator, unaryExpression = parseUnaryExpression(unaryExpression.unaryExpression())
            )
        }
        val primaryExpression =
            unaryExpression.primaryExpression() ?: throw WslParseException("Expected primary expression")
        return WslUnaryExpression.Base(primaryExpression = parsePrimaryExpression(primaryExpression))
    }

    private fun parsePrimaryExpression(
        primaryExpression: WslParser.PrimaryExpressionContext
    ): WslPrimaryExpression {
        val disjunction = primaryExpression.disjunction()
        val valueExpression = primaryExpression.valueExpression()
        return when {
            disjunction != null -> WslPrimaryExpression.WithParens(parseDisjunction(disjunction))
            valueExpression != null -> WslPrimaryExpression.WithValue(parseValueExpression(valueExpression))
            else -> throw WslParseException("Unexpected state parsing primary expression")
        }
    }

    private fun parseValueExpression(valueExpression: WslParser.ValueExpressionContext): WslValueExpression {
        valueExpression.variable()?.let { variableContext ->
            return WslValueExpression.WslVariableExpression(variableContext.VARIABLE_NAME().text)
        }
        valueExpression.stringLiteral()?.let { stringLiteralContext ->
            val contents = stringLiteralContext.stringContent().map { parseStringContent(it) }
            return WslValueExpression.WslStringExpression(contents)
        }
        val numberLiteral = valueExpression.NUMBER()
        val value = when {
            valueExpression.FALSE() != null -> WslValue.WslBoolean(false)
            valueExpression.TRUE() != null -> WslValue.WslBoolean(true)
            numberLiteral != null -> WslValue.WslNumber(
                numberLiteral.text.toBigDecimalOrNull() ?: throw WslParseException("Could not parse number")
            )
            else -> throw WslParseException("Unhandled alternative in value expression")
        }
        return WslValueExpression.WslLiteralExpression(value)
    }

    private fun parseStringContent(stringLiteral: WslParser.StringContentContext): WslStringContent {
        val text = stringLiteral.StringText()
        val escapedChar = stringLiteral.StringEscapedChar()
        val ref = stringLiteral.StringRef()
        return when {
            text != null -> WslStringContent.Text(text.text)
            escapedChar != null -> WslStringContent.EscapedChar(escapedChar.text)
            ref != null -> WslStringContent.Variable(ref.text)
            else -> throw WslParseException("Unhandled string content alternative")
        }
    }
}

class WslContext {
    fun lookupVariable(name: String): WslValue {
        return WslValue.WslString("")
    }

    fun hasVariable(name: String): Boolean {
        return false
    }
}

sealed class WslValue {
    data class WslBoolean(val value: Boolean) : WslValue() {
        override fun toBoolean(): Boolean {
            return value
        }

        override fun toNumber(): BigDecimal {
            return if (value) BigDecimal.ONE else BigDecimal.ZERO
        }

        override fun toString(): String {
            return value.toString()
        }

        override fun compareWith(operator: WslComparisonOperator, other: WslValue): Boolean {
            throw WslBooleanComparisonException()
        }

        override fun equals(other: Any?): Boolean {
            return when (other) {
                is WslValue -> value == other.toBoolean()
                else -> false
            }
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }
    }

    data class WslString(val value: String) : WslValue() {
        override fun toBoolean(): Boolean {
            return value.toBoolean()
        }

        override fun toNumber(): BigDecimal {
            return value.toBigDecimalOrNull() ?: BigDecimal.ZERO
        }

        override fun compareWith(operator: WslComparisonOperator, other: WslValue): Boolean {
            return when (other) {
                is WslBoolean -> throw WslBooleanComparisonException()
                is WslString -> compare(operator, value, other.value)
                is WslNumber -> compare(operator, toNumber(), other.value)
            }
        }

        override fun equals(other: Any?): Boolean {
            return when (other) {
                is WslBoolean -> value.toBoolean() == other.value
                is WslString -> value.equals(other = other.value, ignoreCase = true)
                is WslNumber -> value.toBigDecimal() == other.value
                else -> false
            }
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }
    }

    data class WslNumber(val value: BigDecimal) : WslValue() {
        override fun toBoolean(): Boolean {
            return false
        }

        override fun toNumber(): BigDecimal {
            return value
        }

        override fun equals(other: Any?): Boolean {
            return when (other) {
                is WslBoolean -> toBoolean() == other.value
                is WslString -> value == other.toNumber()
                is WslNumber -> value == other.value
                else -> false
            }
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }

        override fun compareWith(operator: WslComparisonOperator, other: WslValue): Boolean {
            return when (other) {
                is WslBoolean -> throw WslBooleanComparisonException()
                is WslString -> compare(operator, value, other.toNumber())
                is WslNumber -> compare(operator, value, other.value)
            }
        }
    }

    abstract fun toBoolean(): Boolean
    abstract fun toNumber(): BigDecimal
    abstract fun compareWith(operator: WslComparisonOperator, other: WslValue): Boolean
}

private fun <T> compare(operator: WslComparisonOperator, value1: Comparable<T>, value2: T): Boolean {
    return when (operator) {
        WslComparisonOperator.GT -> value1 > value2
        WslComparisonOperator.LT -> value1 < value2
        WslComparisonOperator.GTE -> value1 >= value2
        WslComparisonOperator.LTE -> value1 <= value2
    }
}

class WslBooleanComparisonException : WslRuntimeException("Cannot compare boolean expressions")
open class WslRuntimeException(val reason: String) : Exception(reason)
open class WslParseException(val reason: String) : Exception(reason)

data class WslLine(val label: String?, val statement: WslStatement)
sealed class WslStatement {
    data class WithCondition(val condition: WslExpression, val statement: WslStatement) : WslStatement()
    data class WslCommand(val contents: List<WslCommandContent>) : WslStatement()
}

sealed class WslCommandContent {
    data class Text(val text: String) : WslCommandContent() {
        override fun getValue(context: WslContext): String {
            return text
        }
    }

    data class Variable(val name: String) : WslCommandContent() {
        override fun getValue(context: WslContext): String {
            return context.lookupVariable(name).toString()
        }
    }

    abstract fun getValue(context: WslContext): String
}

data class WslExpression(val disjunction: WslDisjunction) {
    fun getValue(context: WslContext): Boolean {
        return disjunction.getValue(context).toBoolean()
    }
}

data class WslDisjunction(val conjunctions: List<WslConjunction>) {
    fun getValue(context: WslContext): WslValue {
        return conjunctions
            .map { it.getValue(context) }
            .reduce { acc, next -> WslValue.WslBoolean(acc.toBoolean() || next.toBoolean()) }
    }
}

data class WslConjunction(val equalities: List<WslEquality>) {
    fun getValue(context: WslContext): WslValue {
        return equalities
            .map { it.getValue(context) }
            .reduce { acc, unit -> WslValue.WslBoolean(acc.toBoolean() && unit.toBoolean()) }
    }
}

data class WslEquality(
    val comparison: WslComparison,
    val otherComparisons: List<Pair<WslEqualityOperator, WslComparison>>
) {
    fun getValue(context: WslContext): WslValue {
        var acc = comparison.getValue(context)
        otherComparisons.forEach {
            val operator = it.first
            val other = it.second.getValue(context)
            acc = when (operator) {
                WslEqualityOperator.EQ -> WslValue.WslBoolean(acc == other)
                WslEqualityOperator.NEQ -> WslValue.WslBoolean(acc != other)
            }
        }
        return acc
    }
}

enum class WslEqualityOperator {
    EQ,
    NEQ,
}

data class WslComparison(
    val infixExpression: WslInfixExpression,
    val otherInfixExpressions: List<Pair<WslComparisonOperator, WslInfixExpression>>
) {
    fun getValue(context: WslContext): WslValue {
        var acc = infixExpression.getValue(context)
        otherInfixExpressions.forEach {
            val op = it.first
            val other = it.second
            acc = WslValue.WslBoolean(acc.compareWith(op, other.getValue(context)))
        }
        return acc
    }
}

enum class WslComparisonOperator {
    GT,
    LT,
    GTE,
    LTE
}

data class WslInfixExpression(
    val additiveExpression: WslAdditiveExpression,
    val otherAdditiveExpressions: List<Pair<WslInfixOperator, WslAdditiveExpression>>
) {
    fun getValue(context: WslContext): WslValue {
        var acc = additiveExpression.getValue(context)
        otherAdditiveExpressions.forEach { (op, expression) ->
            acc = op.getValue(acc, expression.getValue(context))
        }
        return acc
    }
}

enum class WslInfixOperator {
    CONTAINS {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            return when (value1) {
                is WslValue.WslBoolean -> throw WslRuntimeException("Boolean cannot contain")
                is WslValue.WslString -> WslValue.WslBoolean(value1.value.contains(value2.toString()))
                is WslValue.WslNumber -> throw WslRuntimeException("Number cannot contain")
            }
        }
    },
    CONTAINSRE {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            return when (value1) {
                is WslValue.WslBoolean -> throw WslRuntimeException("Boolean cannot contain")
                is WslValue.WslString -> WslValue.WslBoolean(value1.value.contains(value2.toString().toRegex()))
                is WslValue.WslNumber -> throw WslRuntimeException("Number cannot contain")
            }
        }
    };

    abstract fun getValue(value1: WslValue, value2: WslValue): WslValue
}

data class WslAdditiveExpression(
    val multiplicativeExpression: WslMultiplicativeExpression,
    val otherMultiplicativeExpressions: List<Pair<WslAdditiveOperator, WslMultiplicativeExpression>>
) {
    fun getValue(context: WslContext): WslValue {
        var acc = multiplicativeExpression.getValue(context)
        otherMultiplicativeExpressions.forEach { (op, exp) ->
            acc = op.getValue(acc, exp.getValue(context))
        }
        return acc
    }
}

enum class WslAdditiveOperator {
    ADD {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            return if (value1 is WslValue.WslString || value2 is WslValue.WslString) {
                WslValue.WslString(value1.toString() + value2.toString())
            } else {
                WslValue.WslNumber(value1.toNumber() + value2.toNumber())
            }
        }
    },
    SUB {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            return WslValue.WslNumber(value1.toNumber() - value2.toNumber())
        }
    };

    abstract fun getValue(value1: WslValue, value2: WslValue): WslValue
}

data class WslMultiplicativeExpression(
    val unaryExpression: WslUnaryExpression,
    val otherUnaryExpressions: List<Pair<WslMultiplicativeOperator, WslUnaryExpression>>
) {
    fun getValue(context: WslContext): WslValue {
        var acc = unaryExpression.getValue(context)
        otherUnaryExpressions.forEach { (op, exp) ->
            acc = op.getValue(acc, exp.getValue(context))
        }
        return acc
    }
}

enum class WslMultiplicativeOperator {
    MULT {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            return when (value1) {
                is WslValue.WslString -> WslValue.WslString(value1.value.repeat(value2.toNumber().toInt()))
                else -> WslValue.WslNumber(value1.toNumber() * value2.toNumber())
            }
        }
    },
    DIV {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            val divisor = value2.toNumber()
            if (divisor == BigDecimal.ZERO) {
                throw WslRuntimeException("Cannot divide by 0")
            }
            return WslValue.WslNumber(value1.toNumber() / value2.toNumber())
        }
    };

    abstract fun getValue(value1: WslValue, value2: WslValue): WslValue
}

sealed class WslUnaryExpression {
    data class WithOperator(val operator: WslUnaryOperator, val unaryExpression: WslUnaryExpression) :
        WslUnaryExpression() {
        override fun getValue(context: WslContext): WslValue {
            return operator.getValue(unaryExpression.getValue(context), context)
        }
    }

    data class Base(val primaryExpression: WslPrimaryExpression) : WslUnaryExpression() {
        override fun getValue(context: WslContext): WslValue {
            return primaryExpression.getValue(context)
        }
    }

    abstract fun getValue(context: WslContext): WslValue
}

enum class WslUnaryOperator {
    NOT {
        override fun getValue(value: WslValue, context: WslContext): WslValue {
            return WslValue.WslBoolean(!value.toBoolean())
        }
    },
    EXISTS {
        override fun getValue(value: WslValue, context: WslContext): WslValue {
            val name = value.toString()
            return WslValue.WslBoolean(context.hasVariable(name))
        }
    };

    abstract fun getValue(value: WslValue, context: WslContext): WslValue
}

sealed class WslPrimaryExpression {
    data class WithParens(val disjunction: WslDisjunction) : WslPrimaryExpression() {
        override fun getValue(context: WslContext): WslValue {
            return disjunction.getValue(context)
        }
    }

    data class WithValue(val valueExpression: WslValueExpression) : WslPrimaryExpression() {
        override fun getValue(context: WslContext): WslValue {
            return valueExpression.getValue(context)
        }
    }

    abstract fun getValue(context: WslContext): WslValue
}

sealed class WslValueExpression {
    data class WslVariableExpression(val name: String) : WslValueExpression() {
        override fun getValue(context: WslContext): WslValue {
            return context.lookupVariable(name)
        }
    }

    data class WslStringExpression(val content: List<WslStringContent>) : WslValueExpression() {
        override fun getValue(context: WslContext): WslValue {
            val value = content
                .map { it.getValue(context) }
                .reduce { acc, s -> acc + s }
            return WslValue.WslString(value)
        }
    }

    data class WslLiteralExpression(val value: WslValue) : WslValueExpression() {
        override fun getValue(context: WslContext): WslValue {
            return value
        }
    }

    abstract fun getValue(context: WslContext): WslValue
}

sealed class WslStringContent {
    data class Text(val text: String) : WslStringContent() {
        override fun getValue(context: WslContext): String {
            return text
        }
    }

    data class EscapedChar(val char: String) : WslStringContent() {
        override fun getValue(context: WslContext): String {
            return char
        }
    }

    data class Variable(val name: String) : WslStringContent() {
        override fun getValue(context: WslContext): String {
            return context.lookupVariable(name).toString()
        }
    }

    abstract fun getValue(context: WslContext): String
}