package dev.kason.pcp

import com.lectra.koson.ObjectType
import com.lectra.koson.arr
import com.lectra.koson.obj
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.random.Random

@Serializable
enum class Division {
	@SerialName("novice")
	Novice,

	@SerialName("advanced")
	Advanced
	// add more divisions as needed
}

// represents a team
// Id to JSON serialize, Config to deserialize
class Team private constructor(
	val name: String,
	val division: Division,
	val school: String,
	val password: String?
) : KosonOutput() {
	// safe version of Team without password
	// deserialization does nothing, use Team.Config for that
	@Serializable
	data class Id(
		val name: String,
		val division: Division,
		val school: String
	)

	var score: Int = Contest.NotStartedScore
		internal set

	val id: Id get() = Id(name, division, school)

	// to get information from config, update as needed
	@Serializable
	data class Config(
		val name: String,
		val division: Division,
		val school: String,
		val password: String? = null,
		val sessions: List<String> = emptyList()
	)

	// team constructor from config
	@Deprecated("use Contest.createTeam()", ReplaceWith("Contest.createTeam(config)"))
	internal constructor(config: Config) : this(
		config.name,
		config.division,
		config.school,
		config.password
	) {
		for (session in config.sessions) {
			newSession(session, ignoreWarning = true)
		}
	}

	fun passwordDoesNotMatch(password: String): Boolean = this.password != null && this.password != password

	internal val sessions: MutableList<Session> = mutableListOf()

	@Suppress("DEPRECATION")
	fun newSession(
		code: String = generateCode(),
		ignoreWarning: Boolean = false // ignore if it's from a config
	): Session {
		val session = Session(this, code)
		if (!ignoreWarning && sessions.isNotEmpty()) {
			sessions.add(session)
			val sessionCodes = sessions.joinToString(prefix = "[", postfix = "]") { it.code }
			logger.warn {
				"team ${this.name} has multiple sessions (${sessions.size}): $sessionCodes, check legitimacy"
			}
		} else {
			sessions.add(session)
		}
		Contest.sessions[code] = session
		return session
	}

	val runs: MutableList<Run> = mutableListOf()

	fun addRun(run: Run) {

	}

	suspend fun sendMessage(msg: WebSocketMessage) {
		for (session in sessions.filter { it.isConnected }) {
			session.sendMessage(msg)
		}
	}

	override fun json(): ObjectType = obj {
		"name" to name
		"division" to division.name
		"school" to school
		"score" to score
		"sessions" to arr[sessions.map { it.code }]
		"runs" to arr[runs.map { it.json() }]
	}
}

class Session @Deprecated("use Team.newSession()") internal constructor(
	val team: Team,
	val code: String
) : KosonOutput() {
	internal var websocket: DefaultWebSocketServerSession? = null
	val isConnected: Boolean get() = websocket != null

	var isValid: Boolean = true
		private set

	// sends the serialized wsm to the websocket
	suspend fun sendMessage(msg: WebSocketMessage) = websocket?.sendSerialized(msg)
		?: error("session $code is not connected, cannot send message")

	// closes the websocket with the given reason
	suspend fun terminateConnection(reason: CloseReason? = null) {
		if (!isConnected) {
			error("session $code is not connected, cannot invalidate")
		}
		isValid = false
		websocket!!.close(reason ?: CloseReason(CloseReason.Codes.NORMAL, "session invalidated"))
		websocket = null
	}

	override fun json(): ObjectType = obj {
		"team" to team.name
		"code" to code
		"valid" to isValid
		"connected" to isConnected
	}

}

val base64Encoder: Base64.Encoder = Base64.getUrlEncoder()

// generates a code if not provided
fun generateCode(): String {
	val byteArray = ByteArray(18)
	Random.Default.nextBytes(byteArray)
	return base64Encoder.encodeToString(byteArray)
}