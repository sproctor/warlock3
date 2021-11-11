package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.parser.WslLexer
import cc.warlock.warlock3.core.parser.WslParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.io.FileReader
import java.math.BigDecimal

class WslScript(
    val name: String,
    private val file: File,
) {

    fun parse(): List<WslLine> {
        val reader = FileReader(file)
        val input: CharStream = CaseChangingCharStream(CharStreams.fromReader(reader))
        val lexer = WslLexer(input)
        val parser = WslParser(CommonTokenStream(lexer))
        val script = parser.script()
        return script.line().map { parseLine(it) }
    }

    private fun parseLine(line: WslParser.LineContext): WslLine {
        val labels = line.Label()?.map { it.text.dropLast(1) } // drop ending :
        val statement = parseStatement(line.statement())
        return WslLine(lineNumber = line.start.line, labels = labels ?: emptyList(), statement = statement)
    }

    private fun parseStatement(statement: WslParser.StatementContext): WslStatement {
        val ifExpression = statement.ifExpression()
        return if (ifExpression != null) {
            parseIfExpression(ifExpression)
        } else {
            parseCommand(statement.command())
        }
    }

    private fun parseIfExpression(ifExpression: WslParser.IfExpressionContext): WslStatement.ConditionalStatement {
        return WslStatement.ConditionalStatement(
            parseExpression(ifExpression.expression()),
            parseCommand(ifExpression.command(0)),
            if (ifExpression.ELSE() != null) parseCommand(ifExpression.command(1)) else null
        )
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
        commandContent.TEXT()?.let { return WslCommandContent.Text(it.text) }
        commandContent.VARIABLE_NAME()?.let { return WslCommandContent.Variable(it.text) }
        commandContent.expression()?.let { return WslCommandContent.Expression(parseExpression(it)) }
        commandContent.DOUBLE_PERCENT()?.let { return WslCommandContent.Text("%") }
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
            prefixUnaryExpression = parsePrefixUnaryExpression(multiplicativeExpression.prefixUnaryExpression(0)),
            otherUnaryExpressions = multiplicativeExpression.multiplicativeOperator().mapIndexed { index, opContext ->
                val operator = when {
                    opContext.MULT() != null -> WslMultiplicativeOperator.MULT
                    opContext.DIV() != null -> WslMultiplicativeOperator.DIV
                    else -> throw WslParseException("Unhandled multiplicative operator")
                }
                Pair(operator, parsePrefixUnaryExpression(multiplicativeExpression.prefixUnaryExpression(index + 1)))
            }
        )
    }

    private fun parsePrefixUnaryExpression(
        prefixUnaryExpression: WslParser.PrefixUnaryExpressionContext
    ): WslPrefixUnaryExpression {
        return WslPrefixUnaryExpression(
            operators = prefixUnaryExpression.prefixUnaryOperator().map {
                parsePrefixUnaryOperator(it)
            },
            postfixUnaryExpression = parsePostfixUnaryExpression(prefixUnaryExpression.postfixUnaryExpression())
        )
    }

    private fun parsePrefixUnaryOperator(
        prefixUnaryOperator: WslParser.PrefixUnaryOperatorContext
    ): WslPrefixUnaryOperator {
        return when {
            prefixUnaryOperator.EXISTS() != null -> WslPrefixUnaryOperator.EXISTS
            prefixUnaryOperator.NOT() != null -> WslPrefixUnaryOperator.NOT
            else -> throw WslParseException("Unhandled unary operator")
        }
    }

    private fun parsePostfixUnaryExpression(
        postfixUnaryExpression: WslParser.PostfixUnaryExpressionContext
    ): WslPostfixUnaryExpression {
        return WslPostfixUnaryExpression(
            primaryExpression = parsePrimaryExpression(postfixUnaryExpression.primaryExpression()),
            indexingSuffixes = postfixUnaryExpression.indexingSuffix().map {
                parseIndexingSuffix(it)
            }
        )
    }

    private fun parseIndexingSuffix(
        indexingSuffix: WslParser.IndexingSuffixContext
    ): WslExpression {
        return parseExpression(indexingSuffix.expression())
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
        valueExpression.variableExpression()?.let { return parseVariableExpression(it) }
        valueExpression.stringLiteral()?.let { stringLiteralContext ->
            val contents = stringLiteralContext.stringContent().map { parseStringContent(it) }
            return WslValueExpression.WslStringExpression(contents)
        }
        val numberLiteral = valueExpression.NUMBER()
        val value = when {
            valueExpression.FALSE() != null -> WslBoolean(false)
            valueExpression.TRUE() != null -> WslBoolean(true)
            numberLiteral != null -> WslNumber(
                numberLiteral.text.toBigDecimalOrNull() ?: throw WslParseException("Could not parse number")
            )
            else -> throw WslParseException("Unhandled alternative in value expression")
        }
        return WslValueExpression.WslLiteralExpression(value)
    }

    private fun parseVariableExpression(variableExpression: WslParser.VariableExpressionContext): WslValueExpression.WslVariableExpression {
        variableExpression.IDENTIFIER()?.let { return WslValueExpression.WslVariableExpression(it.text) }
        variableExpression.VARIABLE_NAME()?.let { return WslValueExpression.WslVariableExpression(it.text) }
        throw WslParseException("Unhandled alternative in variable expression")
    }

    private fun parseStringContent(stringLiteral: WslParser.StringContentContext): WslStringContent {
        stringLiteral.StringText()?.let { return WslStringContent.Text(it.text) }
        stringLiteral.StringEscapedChar()?.let { return WslStringContent.EscapedChar(it.text) }
        stringLiteral.VARIABLE_NAME()?.let { return WslStringContent.Variable(it.text) }
        stringLiteral.DOUBLE_PERCENT()?.let { return WslStringContent.Text("%") }
        throw WslParseException("Unhandled string content alternative")
    }
}

open class WslRuntimeException(val reason: String) : Exception(reason)
open class WslParseException(val reason: String) : Exception(reason)

data class WslLine(val lineNumber: Int, val labels: List<String>, val statement: WslStatement)

sealed class WslStatement {
    data class ConditionalStatement(
        val condition: WslExpression,
        val ifCommand: WslCommand,
        val elseCommand: WslCommand?,
    ) : WslStatement() {
        override suspend fun execute(context: WslContext) {
            if (condition.getValue(context).toBoolean()) {
                ifCommand.execute(context)
            } else {
                elseCommand?.execute(context)
            }
        }
    }

    data class WslCommand(val contents: List<WslCommandContent>) : WslStatement() {
        override suspend fun execute(context: WslContext) {
            val commandLine = contents.map { it.getValue(context) }.reduceOrNull { acc, n -> acc + n }
            if (commandLine == null || commandLine.isBlank()) {
                return
            }
            context.executeCommand(commandLine)
        }
    }

    abstract suspend fun execute(context: WslContext)
}

sealed class WslCommandContent {
    data class Text(val text: String) : WslCommandContent() {
        override fun getValue(context: WslContext): String {
            return text
        }
    }

    data class Variable(val name: String) : WslCommandContent() {
        override fun getValue(context: WslContext): String {
            return context.lookupVariable(name)?.toString() ?: ""
        }
    }

    data class Expression(val expression: WslExpression) : WslCommandContent() {
        override fun getValue(context: WslContext): String {
            return expression.getValue(context).toString()
        }
    }

    abstract fun getValue(context: WslContext): String
}

data class WslExpression(val disjunction: WslDisjunction) {
    fun getValue(context: WslContext): WslValue {
        return disjunction.getValue(context)
    }
}

data class WslDisjunction(val conjunctions: List<WslConjunction>) {
    fun getValue(context: WslContext): WslValue {
        return conjunctions
            .map { it.getValue(context) }
            .reduce { acc, next -> WslBoolean(acc.toBoolean() || next.toBoolean()) }
    }
}

data class WslConjunction(val equalities: List<WslEquality>) {
    fun getValue(context: WslContext): WslValue {
        return equalities
            .map { it.getValue(context) }
            .reduce { acc, unit -> WslBoolean(acc.toBoolean() && unit.toBoolean()) }
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
                WslEqualityOperator.EQ -> WslBoolean(acc == other)
                WslEqualityOperator.NEQ -> WslBoolean(acc != other)
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
            acc = WslBoolean(acc.compareWith(op, other.getValue(context)))
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
            if (value1.isMap()) {
                return WslBoolean(value1.getProperty(value2.toString()) != WslNull)
            }
            return WslBoolean(value1.toString().contains(value2.toString()))
        }
    },
    CONTAINSRE {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            return WslBoolean(value1.toString().contains(value2.toString().toRegex()))
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
            return if (value1.isNumeric() && value2.isNumeric()) {
                WslNumber(value1.toNumber() + value2.toNumber())
            } else {
                WslString(value1.toString() + value2.toString())
            }
        }
    },
    SUB {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            return WslNumber(value1.toNumber() - value2.toNumber())
        }
    };

    abstract fun getValue(value1: WslValue, value2: WslValue): WslValue
}

data class WslMultiplicativeExpression(
    val prefixUnaryExpression: WslPrefixUnaryExpression,
    val otherUnaryExpressions: List<Pair<WslMultiplicativeOperator, WslPrefixUnaryExpression>>
) {
    fun getValue(context: WslContext): WslValue {
        var acc = prefixUnaryExpression.getValue(context)
        otherUnaryExpressions.forEach { (op, exp) ->
            acc = op.getValue(acc, exp.getValue(context))
        }
        return acc
    }
}

enum class WslMultiplicativeOperator {
    MULT {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            if (!value2.isNumeric())
                throw WslRuntimeException("Second argument to multiplication operator must be numeric")
            return if (value1.isNumeric()) {
                WslNumber(value1.toNumber() * value2.toNumber())
            } else {
                WslString(value1.toString().repeat(value2.toNumber().toInt()))
            }
        }
    },
    DIV {
        override fun getValue(value1: WslValue, value2: WslValue): WslValue {
            val divisor = value2.toNumber()
            if (divisor == BigDecimal.ZERO) {
                throw WslRuntimeException("Cannot divide by 0")
            }
            return WslNumber(value1.toNumber() / value2.toNumber())
        }
    };

    abstract fun getValue(value1: WslValue, value2: WslValue): WslValue
}

data class WslPrefixUnaryExpression(
    val operators: List<WslPrefixUnaryOperator>,
    val postfixUnaryExpression: WslPostfixUnaryExpression,
) {
    fun getValue(context: WslContext): WslValue {
        return operators.foldRight(
            initial = postfixUnaryExpression.getValue(context),
            operation = { v, acc ->
                v.getValue(acc, context)
            }
        )
    }
}

enum class WslPrefixUnaryOperator {
    NOT {
        override fun getValue(value: WslValue, context: WslContext): WslValue {
            return WslBoolean(!value.toBoolean())
        }
    },
    EXISTS {
        override fun getValue(value: WslValue, context: WslContext): WslValue {
            return WslBoolean(value != WslNull)
        }
    };

    abstract fun getValue(value: WslValue, context: WslContext): WslValue
}

data class WslPostfixUnaryExpression(
    val primaryExpression: WslPrimaryExpression,
    val indexingSuffixes: List<WslExpression>
) {
    fun getValue(context: WslContext): WslValue {
        var acc = primaryExpression.getValue(context)
        indexingSuffixes.forEach {
            val key = it.getValue(context).toString()
            acc = acc.getProperty(key)
        }
        return acc
    }
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
            return context.lookupVariable(name) ?: WslNull
        }
    }

    data class WslStringExpression(val content: List<WslStringContent>) : WslValueExpression() {
        override fun getValue(context: WslContext): WslValue {
            val value = content
                .map { it.getValue(context) }
                .reduce { acc, s -> acc + s }
            return WslString(value)
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