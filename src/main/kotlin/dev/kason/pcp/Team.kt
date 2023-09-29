package dev.kason.pcp

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

val logger = KotlinLogging.logger {}

class Team(
	var name: String,
	var division: Division,
	var school: String,
	var password: String? = null
) {
	@Serializable
	enum class Division {
		@SerialName("novice")
		Novice,

		@SerialName("advanced")
		Advanced
	}

	@Serializable
	class Id(val name: String, val division: Division, val school: String)

	val id: Id get() = Id(name, division, school)
	val sessions: MutableList<Session> = mutableListOf()

	fun addSession(): Session {
		val session = Session(this)
		if (sessions.isNotEmpty()) {
			logger.warn { "Team '$name' has multiple sessions, check legitimacy." }
		}
		sessions.add(session)
		Contest.sessions[session.code] = session
		return session
	}

	var score: Int = 0

	fun invalidateSessions() = sessions.forEach { it.valid = false }
}

@Serializable
class LoginInput(val username: String, val password: String)

@Serializable
class LoginResponse(val team: Team.Id, val token: String)

fun Routing.addLoginRoutes() {
	route("/api/auth") {
		post {
			val input = call.receive<LoginInput>()
			val team = Contest.teams[input.username]
			if (team == null || team.password != input.password) {
				call.respond(HttpStatusCode.Unauthorized)
				return@post
			}
			val session = team.addSession()
			call.respond(LoginResponse(team.id, session.code))
		}
	}
}

@Serializable
data class TeamScoreInformation(val team: Team.Id, val score: Int)

fun Team.toTeamScoreInformation(): TeamScoreInformation {
	return TeamScoreInformation(id, score)
}

fun Routing.addScoreRoute() {
	get("/api/score") {
		val teamScores = Contest.teams.values.map { it.toTeamScoreInformation() }
			.sortedBy { it.score }
			.reversed()
		call.respond(teamScores)
	}
}

fun Routing.addWebsocketRoute() {
	webSocket("/api/ws") {
		val session = call.session() ?: return@webSocket
		session.webSocketSession = this
	}
}

fun ApplicationCall.session(): Session? {
	val token = request.header("X-Team") ?: return null
	return Contest.sessions[token]
}

data class Session(
	val team: Team, val code: String = randomCode()
) {
	var webSocketSession: DefaultWebSocketSession? = null
	val connected: Boolean get() = webSocketSession != null
	var valid: Boolean = true

	suspend fun sendInformation() {
	}
}

fun randomCode(): String {
	val bytes = ByteArray(18)
	Random.nextBytes(bytes)
	return bytes.encodeBase64()
}