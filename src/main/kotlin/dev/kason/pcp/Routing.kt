package dev.kason.pcp

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.hours

// login

@Serializable
data class LoginRequest(
	val username: String,
	val password: String
)

@Serializable
data class LoginResponse(
	val token: String,
	val team: Team.Id
)

fun Routing.addLoginRouting() {
	post("/api/auth") {
		val loginRequest: LoginRequest = call.receive()
		val team = Contest.teams[loginRequest.username]
		if (team == null || team.passwordDoesNotMatch(loginRequest.password)) {
			call.respond(HttpStatusCode.Unauthorized)
			return@post
		}
		val session = team.newSession()
		call.respond(LoginResponse(session.code, team.id))
	}
}

fun ApplicationCall.session(): Session? {
	val token = request.header("X-Team") ?: return null
	return Contest.sessions[token]
}

// WS

@Serializable
sealed interface WebSocketMessage

fun Routing.addWebsocketRoute() {
	get("/api/ws") {
		val session = call.session() ?: return@get call.respond(HttpStatusCode.Unauthorized)
		call.respondRedirect {
			protocol = URLProtocol.WS
			encodedPath = "/api/ws"
			parameters.append("token", session.code)
		}
	}
	webSocket("/api/ws") {
		val token = call.parameters["token"]
		if (token == null) {
			close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No token"))
			return@webSocket
		}
		val session = Contest.sessions[token]
		if (session == null || session.isConnected || !session.isValid) {
			close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid session token"))
			return@webSocket
		}
		session.websocket = this
		logger.info { "session $token linked to websocket!" }
		delay(15.hours)
	}
}

@Serializable
data class TeamScoreRepresentation(
	val team: Team.Id,
	val score: Int
)

fun Routing.addGeneralRouting() {
	// scoreboard
	get("/api/score") {
		val teams = Contest.teams.values
			.sortedByDescending { it.score }
			.map { TeamScoreRepresentation(it.id, it.score) }
		call.respond(teams)
	}
	get("/api/runs") {
		val session = call.session()
			?: return@get call.respond(HttpStatusCode.Unauthorized)
		if (!Contest.hasStarted) {
			call.respond(HttpStatusCode.TooEarly)
			return@get
		}
		val team = session.team
		val runs = team.runs
			.map { it.toRepresentation() }
		call.respond(runs)
	}
	post("/api/submit") {
		val session = call.session()
			?: return@post call.respond(HttpStatusCode.Unauthorized)
		if (!Contest.hasStarted) {
			call.respond(HttpStatusCode.TooEarly)
			return@post
		}
		val submission: Submission.Representation = call.receive()
		val submissionObj = submission.toSubmission(session)
		if (submissionObj == null) {
			call.respond(HttpStatusCode.BadRequest)
			return@post
		}
		call.respond(HttpStatusCode.OK, submissionObj.id)
	}
	post("/api/clarifications") {
		val session = call.session()
			?: return@post call.respond(HttpStatusCode.Unauthorized)
		if (!Contest.hasStarted) {
			call.respond(HttpStatusCode.TooEarly)
			return@post
		}
		val clarificationRequest: Clarification.Request = call.receive()
		val clarification = clarificationRequest.toClarification(session)
		if (clarification == null) {
			call.respond(HttpStatusCode.BadRequest)
			return@post
		}
		call.respond(clarification.toRepresentation())
	}
	get("/api/clarifications") {
		val session = call.session()
			?: return@get call.respond(HttpStatusCode.Unauthorized)
		if (!Contest.hasStarted) {
			call.respond(HttpStatusCode.TooEarly)
			return@get
		}
		val clarifications = Contest.clarifications
			.filter { it.session == session }
			.map { it.toRepresentation() }
		call.respond(clarifications)
	}
	get("/api/time") {
		call.respond(Contest.currentTime())
	}
	get("/api/contest_start") {
		if (!Contest.hasStarted) {
			call.respond(HttpStatusCode.TooEarly)
			return@get
		}
		call.respond(Contest.contestStart)
	}
	get("/api/logout") {
		val session = call.session()
			?: return@get call.respond(HttpStatusCode.Unauthorized)
		session.terminateConnection()
		call.respond(HttpStatusCode.OK)
	}
}

@Serializable
@SerialName("announcement")
data class Announcement(
	val title: String,
	val body: String
): WebSocketMessage

suspend fun Session.sendMessage(text:String, title: String = Contest.DefaultAnnouncementTitle) {
	val announcement = Announcement(title, text)
	sendMessage(announcement)
}

suspend fun Team.sendMessage(text: String, title: String = Contest.DefaultAnnouncementTitle) {
	val announcement = Announcement(title, text)
	sendMessage(announcement)
}

suspend fun Contest.sendEverybody(text: String, title: String = DefaultAnnouncementTitle) {
	val announcement = Announcement(title, text)
	sendEverybody(announcement)
}
