package dev.kason.pcp

import com.lectra.koson.ObjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

@KotlinScript(
	fileExtension = "pcp.kts",
	compilationConfiguration = PCPScriptConfiguration::class
)
open class PCPScript

private val configurationLambda: ScriptCompilationConfiguration.Builder.() -> Unit = {
	defaultImports("dev.kason.pcp.*")
	jvm {
		dependenciesFromCurrentContext(wholeClasspath = true)
	}
}

object PCPScriptConfiguration : ScriptCompilationConfiguration(configurationLambda) {
	val scriptingHost = BasicJvmScriptingHost()
}

fun executePCPScript(sourceCode: String, name: String? = null): ResultWithDiagnostics<EvaluationResult> {
	val compilationConfiguration =
		createJvmCompilationConfigurationFromTemplate<PCPScript>()
	return PCPScriptConfiguration.scriptingHost.eval(
		sourceCode.toScriptSource(name = name),
		compilationConfiguration, null
	)
}

fun executePCPScript(file: File): ResultWithDiagnostics<EvaluationResult> = executePCPScript(file.readText(), file.name)

// note, launch this in a new coroutine
suspend fun pcpScriptListener() {
	logger.info { "launching script listener: " }
	val file = File(Contest.config.scripting.scriptLocation)
	if (!file.exists()) {
		withContext(Dispatchers.IO) {
			file.createNewFile()
		}
		logger.info { "created new script file at ${file.absolutePath}" }
	}
	var previousUpdateTime: Long = 0
	while (true) {
		val lastModified = file.lastModified()
		if (lastModified > previousUpdateTime) {
			previousUpdateTime = lastModified
			logger.info { "script file update detected, executing..." }
			val result = withContext(Dispatchers.IO) {
				executePCPScript(file)
			}
			if (result is ResultWithDiagnostics.Failure) {
				logger.error { "error in script: ${result.reports.joinToString("\n")}" }
			}
		}
		delay(Contest.config.scripting.period * 1000L)
	}
}

abstract class KosonOutput {

	abstract fun json(): ObjectType

	override fun toString(): String = json().pretty(spaces = 4)

}