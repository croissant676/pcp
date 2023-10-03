package dev.kason.pcp

import com.lectra.koson.ObjectType
import com.lectra.koson.obj
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Language {
	@SerialName("java")
	Java,

	@SerialName("python3")
	Python3
}

class Run(val submission: Submission) : KosonOutput() {

	var status: Status = Status.Pending
		private set
	val time: ContestTime = submission.time

	@Serializable
	data class Representation(
		val question: Int,
		val status: Status,
		val time: ContestTime,
		val language: Language
	)

	fun toRepresentation(): Representation = Representation(
		submission.question.number,
		status,
		time,
		submission.language
	)

	@Serializable
	enum class Status {
		@SerialName("pass")
		Pass,

		@SerialName("fail")
		Fail,

		@SerialName("pending")
		Pending,

		@SerialName("invalid")
		Invalid;
	}

	override fun json(): ObjectType = obj {
		"question" to submission.question.number
		"status" to status
		"time" to time
		"language" to submission.language
	}
}

@Serializable
data class ContestTime(
	// unix timestamp
	val timestamp: Long,
	// milliseconds elapsed since start
	val elapsed: Int,
	// milliseconds left
	val left: Int
) : Comparable<ContestTime> {
	override fun compareTo(other: ContestTime): Int = timestamp.compareTo(other.timestamp)
}

@Serializable
@SerialName("contest_start")
data class ContestStart(
	// take from contest
	val start: Long,
	val end: Long,
	val questions: List<Question.Representation>
): WebSocketMessage