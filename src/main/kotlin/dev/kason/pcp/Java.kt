@file:Suppress("MemberVisibilityCanBePrivate")

package dev.kason.pcp

import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.Writer
import java.net.URI
import java.security.SecureClassLoader
import javax.tools.*
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes


class JavaStringSource(className: String, private val code: String) : SimpleJavaFileObject(
	URI("string:///${className.replace('.', '/')}.java"),
	JavaFileObject.Kind.SOURCE
) {
	constructor(code: String) : this("contest", code)

	override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = code
}

class JavaClassObject(name: String, kind: JavaFileObject.Kind) : SimpleJavaFileObject(
	URI("string:///${name.replace('.', '/')}${kind.extension}"), kind
) {
	private val byteArrayOutputStream = ByteArrayOutputStream()
	fun bytes(): ByteArray = byteArrayOutputStream.toByteArray()
	override fun openOutputStream() = byteArrayOutputStream
}

class ClassFileManager(standardJavaFileManager: StandardJavaFileManager) :
	ForwardingJavaFileManager<StandardJavaFileManager>(standardJavaFileManager) {
	private lateinit var javaClassObject: JavaClassObject

	override fun getClassLoader(location: JavaFileManager.Location?): ClassLoader =
		object : SecureClassLoader() {
			override fun findClass(name: String): Class<*> {
				val bytes = javaClassObject.bytes()
				return defineClass(name, bytes, 0, bytes.size)
			}
		}

	override fun getJavaFileForOutput(
		location: JavaFileManager.Location?,
		className: String,
		kind: JavaFileObject.Kind,
		sibling: FileObject?
	): JavaFileObject {
		javaClassObject = JavaClassObject(className, kind)
		return javaClassObject
	}
}

class DynamicCompiler(val className: String, val sourceCode: String) {
	private val javaFileManager: JavaFileManager

	init {
		val compiler = ToolProvider.getSystemJavaCompiler()
		val standardFileManager = compiler.getStandardFileManager(null, null, null)
		javaFileManager = ClassFileManager(standardFileManager)
	}

	fun compile() {
		val compiler = ToolProvider.getSystemJavaCompiler()
		val compilationTask = compiler.getTask(
			NOOPWriter,
			javaFileManager,
			null,
			null,
			null,
			listOf(JavaStringSource(className, sourceCode))
		)
		compilationTask.call()
	}

	fun run() {
		val classLoader = javaFileManager.getClassLoader(null)
		val mainClass = classLoader.loadClass(className)
		val mainMethod = mainClass.getMethod("main", Array<String>::class.java)
		mainMethod.invoke(null, emptyArray<String>())
	}
}

object NOOPWriter : Writer() {
	override fun close() = Unit
	override fun flush() = Unit
	override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
}

fun forbidExit() {
	val securityManager = object : SecurityManager() {
		override fun checkExit(status: Int) {
			throw SecurityException()
		}
	}
	System.setSecurityManager(securityManager)
}

suspend fun executeSafe(source: String, name: String, input: String): ExecutionResult = withContext(Dispatchers.IO) {
	val dynamicCompiler: DynamicCompiler
	try {
		dynamicCompiler = DynamicCompiler(name, source)
		dynamicCompiler.compile()
	} catch (e: Exception) {
		return@withContext ExecutionResult(compiled = false, exception = true)
	}
	val environment = Environment(input)
	environment.activate()
	val start: Long = System.currentTimeMillis()
	try {
		val result = withTimeoutOrNull(1.minutes) {
			dynamicCompiler.run()
		}
		ExecutionResult(
			compiled = true,
			time = System.currentTimeMillis() - start,
			out = environment.out,
			err = environment.err,
			timedOut = result == null
		)
	} catch (e: Exception) {
		ExecutionResult(compiled = true, exception = true)
	} finally {
		environment.restore()
	}
}

class Environment(val `in`: String) {
	private var outContent: ByteArrayOutputStream = ByteArrayOutputStream()
	private var errContent: ByteArrayOutputStream = ByteArrayOutputStream()

	val out: String
		get() = outContent.toString()
	val err: String
		get() = errContent.toString()

	private var originalIn = System.`in`
	private var originalOut = System.out
	private var originalErr = System.err

	fun activate() {
		System.setIn(`in`.byteInputStream())
		System.setOut(PrintStream(outContent))
		System.setErr(PrintStream(errContent))
	}

	fun restore() {
		System.setIn(originalIn)
		System.setOut(originalOut)
		System.setErr(originalErr)
	}
}

data class ExecutionResult(
	val time: Long = 0,
	val exception: Boolean = false,
	val compiled: Boolean = false,
	val timedOut: Boolean = false,
	val out: String? = null,
	val err: String? = null
)

suspend fun main() {
	println("asdf")
	println(measureTimeMillis {
		val results = executeSafe(
			"""
		import java.util.Scanner;
		public class Main {
			public static void main(String[] args) {
				Scanner scanner = new Scanner(System.in);
				while (scanner.hasNext()) {
					System.out.println(scanner.next());
				}
			}
		}
	""".trimIndent(), "Main", "1 2 3 4 5"
		)
		println(results)
	})
}