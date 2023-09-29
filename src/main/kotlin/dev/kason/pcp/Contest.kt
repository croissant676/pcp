package dev.kason.pcp

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

object Contest {
	val teams: MutableMap<String, Team> = mutableMapOf()
	val sessions: MutableMap<String, Session> = mutableMapOf()

	val settings = Config(File("config.json")).parse()

	fun ktorModule(application: Application) {
		createTeams()
		application.apply {
			install(ContentNegotiation) {
				json()
			}
			routing {
				addLoginRoutes()
				addScoreRoute()
				addWebsocketRoute()
			}
		}
	}

	private fun createTeams() {
		for (teamConfig in settings.teams) {
			val team = Team(
				teamConfig.name,
				teamConfig.division,
				teamConfig.school,
				teamConfig.password
			)
			teams[team.name] = team
		}
	}
}

class Config(private val source: String) {
	constructor(file: File) : this(file.readText())

	@Serializable
	data class Settings(
		val teams: List<TeamConfig>,
		val timing: TimingConfig
	)

	@Serializable
	data class TeamConfig(
		val name: String,
		val division: Team.Division,
		val school: String,
		val password: String?
	)

	@Serializable
	data class TimingConfig (
		val contest: Int,
		@SerialName("hide_scoreboard")
		val hideScoreboard: Int
	)

	fun parse(): Settings = ConfigJson.decodeFromString(source)

	companion object {
		val ConfigJson = Json {
			ignoreUnknownKeys = true
			isLenient = true
		}
	}
}

fun main() {
	embeddedServer(Netty, host = "127.0.0.1", module = Contest::ktorModule)
		.start(wait = true)
}