package warlockfe.warlock3.wrayth.network

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import warlockfe.warlock3.core.sge.SgeCharacter
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeError
import warlockfe.warlock3.core.sge.SgeEvent
import warlockfe.warlock3.core.sge.SgeGame
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.Integer.min
import java.net.Socket
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

private const val charsetName = "windows-1252"
private val charset = Charset.forName(charsetName)
private const val passwordHashLength = 32

class SgeClientImpl(
    private val ioDispatcher: CoroutineDispatcher,
) : SgeClient {

    private val logger = KotlinLogging.logger {}
    private lateinit var outputStream: OutputStream
    private lateinit var inputStream: InputStream
    private var reader: BufferedReader? = null
    private var stopped = false
    private val _eventFlow = MutableSharedFlow<SgeEvent>()
    override val eventFlow = _eventFlow.asSharedFlow()
    private val buffer = ByteArray(8192)
    private val passwordHash: ByteArray = ByteArray(32)
    private var username: String? = null
    private var secure: Boolean = true

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    override suspend fun connect(settings: SgeSettings): Boolean {
        secure = settings.secure
        return withContext(ioDispatcher) {
            try {
                logger.debug { "connecting..." }

                val socket: Socket
                if (secure) {
                    // Add certificate to key store
                    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                    keyStore.load(null)
                    val certFactory = CertificateFactory.getInstance("X.509")
                    val cert = certFactory.generateCertificate(settings.certificate.inputStream())
                    keyStore.setCertificateEntry("ca", cert)
                    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    trustManagerFactory.init(keyStore)

                    // Create socket factory
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, trustManagerFactory.trustManagers, SecureRandom())

                    val sslSocket = sslContext.socketFactory.createSocket(settings.host, settings.port) as SSLSocket
                    sslSocket.startHandshake()
                    socket = sslSocket
                } else {
                    socket = Socket(settings.host, settings.port)
                }
                outputStream = socket.outputStream
                inputStream = socket.inputStream
                if (!secure) {
                    reader = BufferedReader(InputStreamReader(inputStream))
                }

                // request password hash
                send("K\n")

                val hashLength = inputStream.read(buffer)
                if (hashLength <= 0) {
                    logger.error { "Error reading password hash" }
                    return@withContext false
                }
                buffer.copyInto(passwordHash, endIndex = min(hashLength, passwordHashLength))

                scope.launch {
                    try {
                        while (!stopped) {
                            val line = readline()
                            if (line != null) {
                                handleData(line)
                            } else {
                                // connection closed by server
                                stopped = true
                            }
                        }
                    } catch (e: IOException) {
                        // not sure why, but let's retry!
                        logger.debug { "SGE  exception: ${e.message}" }
                        _eventFlow.emit(SgeEvent.SgeErrorEvent(SgeError.UNKNOWN_ERROR))
                    } finally {
                        logger.debug { "Closing socket" }
                        //reader.close()
                        socket.close()
                    }
                }
                true
            } catch (e: IOException) {
                logger.debug(e) { "SGE  exception: ${e.message}" }
                false
            }
        }
    }

    private suspend fun handleData(line: String) {
        logger.debug { "SGE receive: $line" }

        when (val response = parseServerResponse(line)) {
            is SgeResponse.SgeErrorResponse -> {
                _eventFlow.emit(SgeEvent.SgeErrorEvent(response.error))
            }

            SgeResponse.SgeLoginSucceededResponse -> _eventFlow.emit(SgeEvent.SgeLoginSucceededEvent)
            is SgeResponse.SgeGameListResponse -> _eventFlow.emit(
                SgeEvent.SgeGamesReadyEvent(
                    response.games
                )
            )

            SgeResponse.SgeGameDetailsResponse -> _eventFlow.emit(SgeEvent.SgeGameSelectedEvent)
            is SgeResponse.SgeCharacterListResponse -> _eventFlow.emit(
                SgeEvent.SgeCharactersReadyEvent(
                    response.characters
                )
            )

            is SgeResponse.SgeReadyToPlayResponse -> {
                stopped = true
                val properties = response.properties
                val credentials = SimuGameCredentials(
                    host = properties["GAMEHOST"]!!,
                    port = properties["GAMEPORT"]!!.toInt(),
                    key = properties["KEY"]!!
                )
                _eventFlow.emit(SgeEvent.SgeReadyToPlayEvent(credentials))
            }

            SgeResponse.SgeUnrecognizedResponse -> Unit // TODO: implement?
        }
    }

    private fun parseServerResponse(line: String): SgeResponse {
        return when (line[0]) {
            'A' -> {
                // response from login attempt
                logger.debug { "A line ($line)" }
                logger.debug {
                    val bytes = line.toByteArray()
                        .map { it.toInt().toString(radix = 16).padStart(2, '0') }
                    "as bytes: $bytes"
                }
                val tokens = line.split('\t')
                when {
                    tokens.size < 3 -> SgeResponse.SgeErrorResponse(SgeError.UNKNOWN_ERROR)
                    tokens[2] == "PASSWORD" -> SgeResponse.SgeErrorResponse(SgeError.INVALID_PASSWORD)
                    tokens[2] == "REJECT" -> SgeResponse.SgeErrorResponse(SgeError.ACCOUNT_REJECTED)
                    tokens[2] == "NORECORD" -> SgeResponse.SgeErrorResponse(SgeError.INVALID_ACCOUNT)
                    tokens[1].equals(username, true) -> SgeResponse.SgeLoginSucceededResponse
                    else -> SgeResponse.SgeErrorResponse(SgeError.UNKNOWN_ERROR)
                }
            }
            // response for request for games
            'M' -> {
                val games = ArrayList<SgeGame>()
                // tab delineated list of games, dropping the M
                val tokens = line.split("\t").drop(1)
                for (i in 1 until tokens.size step 2) {
                    val gameCode = tokens[i - 1]
                    val gameName = tokens[i]
                    games.add(SgeGame(gameName, gameCode, null))
                }
                SgeResponse.SgeGameListResponse(games)
            }
            // We're ignoring the details. Some might be interesting if your account is deactivated
            'G' -> SgeResponse.SgeGameDetailsResponse
            'C' -> {
                val characters = ArrayList<SgeCharacter>()
                val tokens = line.split("\t").drop(5)
                for (i in 1 until tokens.size step 2) {
                    characters.add(SgeCharacter(tokens[i], tokens[i - 1]))
                }
                SgeResponse.SgeCharacterListResponse(characters)
            }

            'L' -> {
                // status\tproperties
                val tokens = line.split("\t")
                when (tokens[1]) {
                    "OK" -> {
                        val properties = mutableMapOf<String, String>()
                        for (element in tokens.drop(2)) {
                            val property = element.split("=")
                            properties[property[0]] = property[1]
                        }
                        SgeResponse.SgeReadyToPlayResponse(properties)
                    }

                    "PROBLEM" -> SgeResponse.SgeErrorResponse(SgeError.ACCOUNT_EXPIRED)
                    else -> SgeResponse.SgeErrorResponse(SgeError.UNKNOWN_ERROR)
                }
            }

            else -> SgeResponse.SgeUnrecognizedResponse
        }
    }

    private fun readline(): String? {
        return if (secure) {
            val len = inputStream.read(buffer)
            if (len > 0) {
                buffer.decodeToString(0, len)
            } else {
                null
            }
        } else {
            reader?.readLine()
        }
    }

    private suspend fun send(string: String) {
        withContext(ioDispatcher) {
            logger.debug { "SGE send: $string" }
            outputStream.write(string.toByteArray(charset))
            outputStream.flush()
        }
    }

    private suspend fun send(bytes: ByteArray) {
        withContext(ioDispatcher) {
            logger.debug { "SGE send: ${bytes.decodeToString()}" }
            outputStream.write(bytes)
            outputStream.flush()
        }
    }

    override suspend fun login(username: String, password: String) {
        this@SgeClientImpl.username = username
        val encryptedPassword = encryptPassword(password.toByteArray(Charsets.US_ASCII))
        val output = "A\t$username\t".toByteArray(Charsets.US_ASCII) + encryptedPassword + '\n'.code.toByte()
        send(output)
    }

    private fun encryptPassword(password: ByteArray): ByteArray {
        val length = min(password.size, passwordHash.size)
        return ByteArray(length) { n ->
            ((passwordHash[n].toInt() xor (password[n].toInt() - 32)) + 32).toByte()
        }
    }

    override suspend fun requestGameList() {
        // request game list
        send("M\n")
    }

    override suspend fun requestCharacterList() {
        send("C\n")
    }

    override suspend fun selectGame(gameCode: String) {
        send("G\t${gameCode}\n")
    }

    override suspend fun selectCharacter(characterCode: String) {
        send("L\t${characterCode}\tSTORM\n")
    }

    override fun close() {
        stopped = true
        scope.cancel()
    }
}

sealed class SgeResponse {
    data object SgeLoginSucceededResponse : SgeResponse()
    data class SgeGameListResponse(val games: List<SgeGame>) : SgeResponse()
    data object SgeGameDetailsResponse : SgeResponse()
    data class SgeCharacterListResponse(val characters: List<SgeCharacter>) : SgeResponse()
    data class SgeReadyToPlayResponse(val properties: Map<String, String>) : SgeResponse()
    data object SgeUnrecognizedResponse : SgeResponse()
    data class SgeErrorResponse(val error: SgeError) : SgeResponse()
}
