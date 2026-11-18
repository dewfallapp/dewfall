package io.github.dewfallapp.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import kotlin.test.Test

class ConsoleOutputDetectorTest {
    @Test
    fun `flags Kotlin's println and print`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    fun load(count: Int) {
                        println("Loaded")
                        print(count)
                        kotlin.io.println()
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:4: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    println("Loaded")
                    ~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:5: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    print(count)
                    ~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    kotlin.io.println()
                    ~~~~~~~~~~~~~~~~~~~
                3 errors
                """
            )
    }

    @Test
    fun `flags println through an import alias`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import kotlin.io.println as say

                    fun load() {
                        say("Loaded")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    say("Loaded")
                    ~~~~~~~~~~~~~
                1 error
                """
            )
    }

    @Test
    fun `flags a callable reference to println`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    fun load(titles: List<String>) {
                        titles.forEach(::println)
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:4: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    titles.forEach(::println)
                                   ~~~~~~~~~
                1 error
                """
            )
    }

    @Test
    fun `flags every use of System dot out and System dot err`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import java.io.PrintStream

                    fun load(): PrintStream {
                        System.out.println("Loaded")
                        java.lang.System.err.print("Failed")
                        val stream = System.err
                        return stream
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    System.out.println("Loaded")
                           ~~~
                src/main/kotlin/test/pkg/Repository.kt:7: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    java.lang.System.err.print("Failed")
                                     ~~~
                src/main/kotlin/test/pkg/Repository.kt:8: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    val stream = System.err
                                        ~~~
                3 errors
                """
            )
    }

    @Test
    fun `flags every printStackTrace and Thread dot dumpStack`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import java.io.IOException
                    import java.io.PrintWriter
                    import java.io.StringWriter

                    fun describe(error: IOException): String {
                        error.printStackTrace()
                        Thread.dumpStack()
                        val text = StringWriter()
                        error.printStackTrace(PrintWriter(text))
                        return text.toString()
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:8: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    error.printStackTrace()
                    ~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:9: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    Thread.dumpStack()
                    ~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:11: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    error.printStackTrace(PrintWriter(text))
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                3 errors
                """
            )
    }

    @Test
    fun `allows println on a writer that isn't the console`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Report.kt",
                    """
                    package test.pkg

                    import java.io.PrintWriter
                    import java.io.StringWriter

                    fun report(lines: List<String>): String {
                        val text = StringWriter()
                        PrintWriter(text).use { writer -> lines.forEach { writer.println(it) } }
                        return text.toString()
                    }
                    """,
                ),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `flags console output in test sources`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                testKotlin(
                    "test/pkg/RepositoryTest.kt",
                    """
                    package test.pkg

                    import java.io.IOException

                    class RepositoryTest {
                        fun loads(error: IOException) {
                            System.out.println("Started")
                            error.printStackTrace()
                        }
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/test/kotlin/test/pkg/RepositoryTest.kt:7: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                        System.out.println("Started")
                               ~~~
                src/test/kotlin/test/pkg/RepositoryTest.kt:8: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                        error.printStackTrace()
                        ~~~~~~~~~~~~~~~~~~~~~~~
                2 errors
                """
            )
    }

    @Test
    fun `allows ConsoleLogWriter in test sources`() {
        lintWith(ConsoleOutputDetector.ISSUE, consoleLogWriter("src/test/kotlin"))
            .run()
            .expectClean()
    }

    @Test
    fun `flags standard error, printStackTrace and dumpStack inside ConsoleLogWriter`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                testKotlin(
                    "io/github/dewfallapp/core/youtube/newpipe/saved/ConsoleLogWriter.kt",
                    """
                    package io.github.dewfallapp.core.youtube.newpipe.saved

                    import java.io.IOException

                    object ConsoleLogWriter {
                        fun write(text: String, error: IOException) {
                            System.err.println(text)
                            error.printStackTrace()
                            Thread.dumpStack()
                        }
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/test/kotlin/io/github/dewfallapp/core/youtube/newpipe/saved/ConsoleLogWriter.kt:7: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                        System.err.println(text)
                               ~~~
                src/test/kotlin/io/github/dewfallapp/core/youtube/newpipe/saved/ConsoleLogWriter.kt:8: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                        error.printStackTrace()
                        ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/kotlin/io/github/dewfallapp/core/youtube/newpipe/saved/ConsoleLogWriter.kt:9: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                        Thread.dumpStack()
                        ~~~~~~~~~~~~~~~~~~
                3 errors
                """
            )
    }

    @Test
    fun `flags ConsoleLogWriter's code in main sources`() {
        lintWith(ConsoleOutputDetector.ISSUE, consoleLogWriter("src/main/kotlin"))
            .run()
            .expect(
                """
                src/main/kotlin/io/github/dewfallapp/core/youtube/newpipe/saved/ConsoleLogWriter.kt:5: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                        System.out.println(level + " " + tag + " " + text)
                               ~~~
                1 error
                """
            )
    }

    @Test
    fun `flags a class named ConsoleLogWriter in another package`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                testKotlin(
                    "test/pkg/ConsoleLogWriter.kt",
                    """
                    package test.pkg

                    object ConsoleLogWriter {
                        fun write(text: String) {
                            System.out.println(text)
                        }
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/test/kotlin/test/pkg/ConsoleLogWriter.kt:5: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                        System.out.println(text)
                               ~~~
                1 error
                """
            )
    }

    @Test
    fun `can't be suppressed`() {
        lintWith(
                ConsoleOutputDetector.ISSUE,
                testKotlin(
                    "test/pkg/RepositoryTest.kt",
                    """
                    package test.pkg

                    @Suppress("DewfallConsoleOutput")
                    fun suppressedByKotlin() {
                        System.out.println("One")
                    }

                    fun suppressedByComment() {
                        //noinspection DewfallConsoleOutput
                        System.out.println("Two")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/test/kotlin/test/pkg/RepositoryTest.kt:3: Error: Issue DewfallConsoleOutput is not allowed to be suppressed [LintError]
                @Suppress("DewfallConsoleOutput")
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/kotlin/test/pkg/RepositoryTest.kt:10: Error: Issue DewfallConsoleOutput is not allowed to be suppressed [LintError]
                    System.out.println("Two")
                           ~~~
                src/test/kotlin/test/pkg/RepositoryTest.kt:5: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    System.out.println("One")
                           ~~~
                src/test/kotlin/test/pkg/RepositoryTest.kt:10: Error: Write through the Logger from :core:logging, not to the console [DewfallConsoleOutput]
                    System.out.println("Two")
                           ~~~
                4 errors
                """
            )
    }

    /**
     * ConsoleLogWriter with its full name, in the source folder [sourceFolder]. It writes through
     * System.out, because test sources here have no Kotlin standard library for println.
     */
    private fun consoleLogWriter(sourceFolder: String) =
        kotlin(
                "$sourceFolder/io/github/dewfallapp/core/youtube/newpipe/saved/ConsoleLogWriter.kt",
                """
                package io.github.dewfallapp.core.youtube.newpipe.saved

                object ConsoleLogWriter {
                    fun write(level: String, tag: String, text: String) {
                        System.out.println(level + " " + tag + " " + text)
                    }
                }
                """,
            )
            .indented()
}
