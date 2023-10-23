package dev.kason.pcp

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JavaGradingServerRequest(
	@SerialName("file_name")
	val fileName: String,
	val code: String,
	@SerialName("question_number")
	val questionNumber: Int,
	val id: Int
)

fun Submission.toJGSRequest(): JavaGradingServerRequest = JavaGradingServerRequest(
	fileName,
	code,
	question.number,
	id
)

suspend fun initialize() {
	val selectorManager = SelectorManager(Dispatchers.IO)
	val socket = aSocket(selectorManager).tcp().connect(
		Contest.config.jex.host,
		Contest.config.jex.port
	)


}

suspend fun Submission.run(): Run {
	TODO()
}