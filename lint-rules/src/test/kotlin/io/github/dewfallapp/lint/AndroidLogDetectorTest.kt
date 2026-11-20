package io.github.dewfallapp.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import kotlin.test.Test

class AndroidLogDetectorTest {
    @Test
    fun `flags a call to android dot util dot Log`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import android.util.Log

                    fun load() {
                        Log.d("Repository", "Loaded")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                    Log.d("Repository", "Loaded")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """,
            )
    }

    @Test
    fun `flags a fully qualified call`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    fun load() {
                        android.util.Log.w("Repository", "Slow")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:4: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                    android.util.Log.w("Repository", "Slow")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """,
            )
    }

    @Test
    fun `flags a call through an import alias`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import android.util.Log as Logcat

                    fun load() {
                        Logcat.e("Repository", "Failed")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                    Logcat.e("Repository", "Failed")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """,
            )
    }

    @Test
    fun `flags a callable reference, which another function can call later`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import android.util.Log

                    val printLine: (Int, String, String) -> Int = Log::println
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:5: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                val printLine: (Int, String, String) -> Int = Log::println
                                                              ~~~~~~~~~~~~
                1 error
                """,
            )
    }

    @Test
    fun `flags every method of Log, including those that only build text`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import android.util.Log

                    fun describe(error: Throwable): String? =
                        if (Log.isLoggable("Repository", Log.DEBUG)) {
                            Log.getStackTraceString(error)
                        } else {
                            null
                        }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                    if (Log.isLoggable("Repository", Log.DEBUG)) {
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:7: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                        Log.getStackTraceString(error)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors
                """,
            )
    }

    @Test
    fun `flags a call in test sources`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                testKotlin(
                    "test/pkg/RepositoryTest.kt",
                    """
                    package test.pkg

                    import android.util.Log

                    class RepositoryTest {
                        fun loads() {
                            Log.i("RepositoryTest", "Started")
                        }
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/test/kotlin/test/pkg/RepositoryTest.kt:7: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                        Log.i("RepositoryTest", "Started")
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """,
            )
    }

    @Test
    fun `allows LogcatWriter in main sources, its lambdas and default values included`() {
        lintWith(AndroidLogDetector.ISSUE, logcatWriter("src/main/kotlin")).run().expectClean()
    }

    @Test
    fun `flags Log's other methods inside LogcatWriter`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                mainKotlin(
                    "io/github/dewfallapp/logging/LogcatWriter.kt",
                    """
                    package io.github.dewfallapp.logging

                    import android.util.Log

                    class LogcatWriter {
                        fun write(tag: String, text: String) {
                            Log.w(tag, text)
                        }
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/io/github/dewfallapp/logging/LogcatWriter.kt:7: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                        Log.w(tag, text)
                        ~~~~~~~~~~~~~~~~
                1 error
                """,
            )
    }

    @Test
    fun `flags LogcatWriter's code in test sources`() {
        lintWith(AndroidLogDetector.ISSUE, logcatWriter("src/test/kotlin"))
            .run()
            .expect(
                """
                src/test/kotlin/io/github/dewfallapp/logging/LogcatWriter.kt:6: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                    private val printLine: (Int, String, String) -> Int = Log::println
                                                                          ~~~~~~~~~~~~
                src/test/kotlin/io/github/dewfallapp/logging/LogcatWriter.kt:9: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                        lines.forEach { Log.println(Log.WARN, tag, it) }
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors
                """,
            )
    }

    @Test
    fun `flags a call outside LogcatWriter in LogcatWriter's own file`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                mainKotlin(
                    "io/github/dewfallapp/logging/LogcatWriter.kt",
                    """
                    package io.github.dewfallapp.logging

                    import android.util.Log

                    class LogcatWriter

                    fun writeDirectly() {
                        Log.d("Dewfall", "Written")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/io/github/dewfallapp/logging/LogcatWriter.kt:8: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                    Log.d("Dewfall", "Written")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """,
            )
    }

    @Test
    fun `flags a class named LogcatWriter in another package`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                mainKotlin(
                    "test/pkg/LogcatWriter.kt",
                    """
                    package test.pkg

                    import android.util.Log

                    class LogcatWriter {
                        fun write(text: String) {
                            Log.d("Dewfall", text)
                        }
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/LogcatWriter.kt:7: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                        Log.d("Dewfall", text)
                        ~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """,
            )
    }

    @Test
    fun `allows Log's priority constants, which log nothing`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                testKotlin(
                    "test/pkg/PriorityTest.kt",
                    """
                    package test.pkg

                    import android.util.Log

                    val priorities = listOf(Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR)
                    """,
                ),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `can't be suppressed`() {
        lintWith(
                AndroidLogDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import android.annotation.SuppressLint
                    import android.util.Log

                    @Suppress("DewfallAndroidLog")
                    fun suppressedByKotlin() {
                        Log.d("Repository", "One")
                    }

                    @SuppressLint("DewfallAndroidLog")
                    fun suppressedByAndroid() {
                        Log.d("Repository", "Two")
                    }

                    fun suppressedByComment() {
                        //noinspection DewfallAndroidLog
                        Log.d("Repository", "Three")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Issue DewfallAndroidLog is not allowed to be suppressed [LintError]
                @Suppress("DewfallAndroidLog")
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:11: Error: Issue DewfallAndroidLog is not allowed to be suppressed [LintError]
                @SuppressLint("DewfallAndroidLog")
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:18: Error: Issue DewfallAndroidLog is not allowed to be suppressed [LintError]
                    Log.d("Repository", "Three")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:8: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                    Log.d("Repository", "One")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:13: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                    Log.d("Repository", "Two")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:18: Error: Log through the Logger from :core:logging, not android.util.Log [DewfallAndroidLog]
                    Log.d("Repository", "Three")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                6 errors
                """,
            )
    }

    /** LogcatWriter as :app has it, with its full name, in the source folder [sourceFolder]. */
    private fun logcatWriter(sourceFolder: String) =
        kotlin(
                "$sourceFolder/io/github/dewfallapp/logging/LogcatWriter.kt",
                """
                package io.github.dewfallapp.logging

                import android.util.Log

                class LogcatWriter(
                    private val printLine: (Int, String, String) -> Int = Log::println
                ) {
                    fun write(tag: String, lines: List<String>) {
                        lines.forEach { Log.println(Log.WARN, tag, it) }
                        printLine(Log.WARN, tag, "Done")
                    }
                }
                """,
            )
            .indented()
}
