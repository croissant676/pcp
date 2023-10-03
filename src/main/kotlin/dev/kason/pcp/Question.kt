package dev.kason.pcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

class Question(
	val number: Int,
	val name: String,
	val inputFile: File,
	val judgeFile: File
) {

	val inputText: String by lazy(LazyThreadSafetyMode.NONE) {
		inputFile.readText()
	}

	val judgeText: String by lazy(LazyThreadSafetyMode.NONE) {
		judgeFile.readText()
	}

	@Serializable
	data class Config(
		val name: String,
		@SerialName("input_file")
		val inputFile: String,
		@SerialName("judge_file")
		val judgeFile: String
	) {
		fun toQuestion(number: Int): Question = Question(number, name, File(inputFile), File(judgeFile))
	}

	@Serializable
	data class Representation(
		val name: String,
		val number: Int
	)

	fun toRepresentation(): Representation = Representation(name, number)
}


class Submission(
	val session: Session,
	val question: Question,
	val language: Language,
	val code: String,
	val fileName: String,
	val id: Int = Contest.submissions.size
) {

	init {
		Contest.submissions.add(this)
	}

	val time = Contest.currentTime()
	val team: Team get() = session.team

	@Serializable
	data class Representation( // from client
		val question: Int,
		val language: Language,
		val code: String,
		@SerialName("file_name")
		val fileName: String
	) {
		fun toSubmission(session: Session): Submission? {
			val question = Contest.questionFor(question) ?: return null
			return Submission(session, question, language, code, fileName)
		}
	}
}

class Clarification internal constructor(
	val session: Session,
	val question: Question,
	val questionText: String,
	var id: Int = Contest.clarifications.size + 1
) {

	init {
		Contest.clarifications.add(this)
	}

	var answer: String? = null
		private set

	val isAnswered: Boolean get() = answer != null

	val time = Contest.currentTime()
	val team: Team get() = session.team

	@Serializable
	@SerialName("clarification_notification")
	data class Representation(
		val id: Int,
		val question: Int,
		@SerialName("question_text")
		val questionText: String,
		val answer: String,
		val time: ContestTime
	) : WebSocketMessage

	@Serializable
	data class Request(
		val question: Int,
		@SerialName("question_text")
		val questionText: String
	) {
		fun toClarification(session: Session): Clarification? {
			val question = Contest.questionFor(question) ?: return null
			return Clarification(session, question, questionText)
		}
	}

	fun toRepresentation(): Representation = Representation(
		id,
		question.number,
		questionText,
		answer ?: Contest.UnansweredClarification,
		time
	)

	suspend fun respond(text: String) {
		answer = text
		Contest.sendEverybody(toRepresentation())
	}
}
