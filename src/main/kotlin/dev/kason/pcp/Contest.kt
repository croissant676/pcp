package dev.kason.pcp

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.*
import java.io.File
import java.time.Duration
import java.util.*

val logger: KLogger = KotlinLogging.logger("dev.kason.pcp.Contest")

object Contest : CoroutineScope by CoroutineScope(Dispatchers.Default) {

	const val NotStartedScore = -1
	const val NotStartedTime = 0L
	const val DefaultDuration = 3 * 60 * 60 // 3 hours

	const val DefaultHideScoreboard = 30 * 60 // 30 minutes
	const val UnansweredClarification = "no answer yet."

	const val DefaultAnnouncementTitle = "Announcement"

	val teams: MutableMap<String, Team> = mutableMapOf()

	val sessions: MutableMap<String, Session> = mutableMapOf()
	var startTime: Long = NotStartedTime
		private set

	val hasStarted: Boolean get() = startTime != NotStartedTime

	val config = Config.read(File("config.json"))

	val clarifications: MutableList<Clarification> = mutableListOf()
	val submissions: MutableList<Submission> = mutableListOf()

	fun module(application: Application) {
		application.apply {
			installPlugins()
			routing {
				addLoginRouting()
				addWebsocketRoute()
				addGeneralRouting()
			}
		}
		for (teamConfig in config.teams) {
			createTeam(teamConfig)
		}
		loadQuestionConfig()
		launch {
			pcpScriptListener()
		}
	}

	fun Application.installPlugins() {
		install(WebSockets) {
			pingPeriod = Duration.ofSeconds(15)
			timeout = Duration.ofSeconds(15)
			maxFrameSize = Long.MAX_VALUE
			masking = false
			contentConverter = KotlinxWebsocketSerializationConverter(Json)
		}
		install(ContentNegotiation) {
			json()
		}
		install(ShutDownUrl.ApplicationCallPlugin) {
			shutDownUrl = "/api/shutdown"
			exitCodeSupplier = { 0 }
		}
		routing {
			singlePageApplication {
				react("frontend/build")
			}
		}
		install(CORS) {
			anyHost()
			allowHeaders { true }
			HttpMethod.DefaultMethods.forEach { allowMethod(it) }
			allowNonSimpleContentTypes = true
			allowCredentials = true
			allowSameOrigin = true
			exposeHeader(HttpHeaders.AccessControlAllowOrigin)
		}
		install(CallLogging) {
			level = Level.INFO
			this.format {
				"${it.response.status()} ${it.request.httpMethod.value} ${it.request.path()}"
			}
		}
	}

	@Suppress("DEPRECATION")
	fun createTeam(teamConfig: Team.Config) {
		val team = Team(teamConfig)
		teams[team.name] = team
	}

	lateinit var contestStart: ContestStart
		private set

	suspend fun start() {
		startTime = System.currentTimeMillis()
		logger.info { "starting contest @ $startTime" }
		for (team in teams.values) {
			team.score = 0 // reset score
		}
		contestStart = contestStart()
		sendEverybody(contestStart)
	}

	fun contestStart(): ContestStart {
		val start = startTime
		val end = start + (config.timing.contest * 1000)
		return ContestStart(start, end, questions.map { it.toRepresentation() })
	}

	// returns whether the session was invalidated
	suspend fun invalidateSession(code: String): Boolean {
		val session = sessions[code] ?: return false
		if (session.isConnected) session.terminateConnection()
		sessions.remove(code)
		session.team.sessions.remove(session)
		return true
	}

	fun currentTime(): ContestTime {
		if (!hasStarted) return ContestTime(NotStartedTime, 0, 0)
		val now = System.currentTimeMillis()
		val elapsed = (now - startTime).toInt()
		val left = (config.timing.contest * 1000) - elapsed
		return ContestTime(now, elapsed, left)
	}

	val questions: MutableList<Question> = mutableListOf()

	fun questionFor(number: Int): Question? = questions.getOrNull(number - 1)

	fun loadQuestionConfig() {
		val questionConfigs = config.questions
		for ((index, questionConfig) in questionConfigs.withIndex()) {
			val question = questionConfig.toQuestion(index + 1)
			questions.add(question)
		}
		logger.info { "loaded ${questions.size} questions" }
	}

	suspend fun sendEverybody(message: WebSocketMessage) {
		for (session in sessions.values.filter { it.isConnected }) {
			session.sendMessage(message)
			logger.info { "sent $message to ${session.code}" }
		}
	}
}

@Serializable
data class Config(
	val teams: List<Team.Config>,
	val timing: Timing,
	val questions: List<Question.Config>,
	val scripting: Scripting,
	val jex: Jex
) {
	@Serializable
	data class Timing(
		// duration of contest in seconds
		val contest: Int = Contest.DefaultDuration,
		@SerialName("hide_scoreboard")
		// number of seconds before contest ends to hide scoreboard
		val hideScoreboard: Int = Contest.DefaultHideScoreboard,
	)

	@Serializable
	data class Scripting(
		val period: Int = 1,
		@SerialName("script_location")
		val scriptLocation: String = "control.pcp.kts"
	)

	@Serializable
	data class Jex(
		val host: String,
		val port: Int
	)

	companion object {
		val ConfigJson = Json {
			ignoreUnknownKeys = true
			isLenient = true
			prettyPrint = true
		}

		fun read(string: String): Config = ConfigJson.decodeFromString(string)
		fun read(file: File): Config = read(file.readText())
	}
}


fun main() {
	embeddedServer(Netty, port = 8080, host = "127.0.0.1", module = Contest::module).start(wait = true)
}
