package io.github.dewfallapp.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.binaryStub
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import kotlin.test.Test

class ConstantLogTextDetectorTest {
    @Test
    fun `allows string literals, const vals and + of those`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.LogField
                    import io.github.dewfallapp.core.logging.Logger

                    private const val AREA = "Repository"

                    class Repository(logger: Logger) {
                        private val log = logger.withTag(AREA)

                        fun load(count: Int) {
                            log.info("Loaded", LogField.count("videos", count))
                            log.info(LOADED)
                            log.info(Messages.FAILED)
                            log.info("Loaded the uploads " + "of a channel " + LOADED)
                            log.info(("Loaded"))
                            log.warn(error = null, message = "Slow")
                            log.info(${"\"\"\""}Loaded${"\"\"\""})
                        }

                        private companion object {
                            const val LOADED = "Loaded"
                        }
                    }

                    object Messages {
                        const val FAILED = "Failed"
                    }
                    """,
                ),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `allows a Java string constant, and flags a Java field that can change`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                javaMessages,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.Logger

                    fun load(log: Logger) {
                        log.info(Messages.LOADED)
                        log.info(Messages.changeable)
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:7: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info(Messages.changeable)
                             ~~~~~~~~~~~~~~~~~~~
                1 error
                """
            )
    }

    @Test
    fun `allows a string constant from a compiled class`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                binaryStub("libs/messages.jar", javaMessages),
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.Logger

                    fun load(log: Logger) {
                        log.info(Messages.LOADED)
                    }
                    """,
                ),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `flags string templates, even of const vals`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.Logger

                    private const val AREA = "Repository"

                    fun load(log: Logger, count: Int) {
                        log.info("Loaded ${'$'}count videos")
                        log.info("${'$'}AREA loaded")
                        log.info("Loaded ${'$'}{AREA.length}")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:8: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info("Loaded ＄count videos")
                             ~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:9: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info("＄AREA loaded")
                             ~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:10: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info("Loaded ＄{AREA.length}")
                             ~~~~~~~~~~~~~~~~~~~~~~~
                3 errors
                """
            )
    }

    @Test
    fun `flags vals that aren't const and values built at run time`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.Logger

                    val NOT_CONST = "Loaded"

                    fun load(log: Logger, count: Int, error: Exception, name: String) {
                        log.info(NOT_CONST)
                        val local = "Loaded"
                        log.info(local)
                        log.info(name)
                        log.info(error.message ?: "Failed")
                        log.info(name.uppercase())
                        log.info(if (count > 0) "Loaded" else "Empty")
                        log.info("Loaded " + name)
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:8: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info(NOT_CONST)
                             ~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:10: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info(local)
                             ~~~~~
                src/main/kotlin/test/pkg/Repository.kt:11: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info(name)
                             ~~~~
                src/main/kotlin/test/pkg/Repository.kt:12: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info(error.message ?: "Failed")
                             ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:13: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info(name.uppercase())
                             ~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:14: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info(if (count > 0) "Loaded" else "Empty")
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:15: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info("Loaded " + name)
                             ~~~~~~~~~~~~~~~~
                7 errors
                """
            )
    }

    @Test
    fun `checks the tag's area and every field name too`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.LogField
                    import io.github.dewfallapp.core.logging.Logger

                    fun load(logger: Logger, area: String, name: String, count: Int) {
                        logger.withTag(area).info("Loaded", LogField.count(name, count))
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:7: Error: area must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    logger.withTag(area).info("Loaded", LogField.count(name, count))
                                   ~~~~
                src/main/kotlin/test/pkg/Repository.kt:7: Error: name must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    logger.withTag(area).info("Loaded", LogField.count(name, count))
                                                                       ~~~~
                2 errors
                """
            )
    }

    @Test
    fun `lets a @ConstantText parameter pass on, and checks its callers`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                mainKotlin(
                    "test/pkg/ResultField.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.ConstantText
                    import io.github.dewfallapp.core.logging.LogField

                    class ResultField private constructor(val logField: LogField) {
                        companion object {
                            fun count(@ConstantText name: String, value: Int): ResultField =
                                ResultField(LogField.count(name, value))
                        }
                    }

                    fun results(count: Int, name: String) = listOf(
                        ResultField.count("videos", count),
                        ResultField.count("videos ${'$'}count", count),
                        ResultField.count(name, count),
                    )
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/ResultField.kt:15: Error: name must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    ResultField.count("videos ＄count", count),
                                      ~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/ResultField.kt:16: Error: name must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    ResultField.count(name, count),
                                      ~~~~
                2 errors
                """
            )
    }

    @Test
    fun `flags a parameter that passes on text without @ConstantText`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.Logger

                    fun logLoaded(log: Logger, message: String) {
                        log.info(message)
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:6: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info(message)
                             ~~~~~~~
                1 error
                """
            )
    }

    @Test
    fun `flags a callable reference, whose later calls can't be checked`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.Logger

                    fun load(log: Logger, titles: List<String>) {
                        titles.forEach(log::info)
                        val tagged: (String) -> Logger = log::withTag
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Call info directly, so its constant text arguments can be checked [DewfallLogTextNotConstant]
                    titles.forEach(log::info)
                                   ~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:7: Error: Call withTag directly, so its constant text arguments can be checked [DewfallLogTextNotConstant]
                    val tagged: (String) -> Logger = log::withTag
                                                     ~~~~~~~~~~~~
                2 errors
                """
            )
    }

    @Test
    fun `flags a template in test sources`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                testKotlin(
                    "test/pkg/RepositoryTest.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.Logger

                    class RepositoryTest {
                        fun loads(log: Logger, count: Int) {
                            log.info("Loaded")
                            log.info("Loaded ${'$'}count")
                        }
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/test/kotlin/test/pkg/RepositoryTest.kt:8: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                        log.info("Loaded ＄count")
                                 ~~~~~~~~~~~~~~~
                1 error
                """
            )
    }

    @Test
    fun `checks calls into a compiled Logger, as in another module`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                binaryStub("libs/logging.jar", compiledLoggingSources),
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.Logger

                    private const val AREA = "Repository"

                    fun load(logger: Logger, count: Int) {
                        val log = logger.withTag(AREA)
                        log.info("Loaded")
                        log.info("Loaded ${'$'}count")
                    }
                    """,
                ),
            )
            // Kotlin stubs lack module metadata, which this test doesn't need.
            .allowKotlinClassStubs(true)
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:10: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info("Loaded ＄count")
                             ~~~~~~~~~~~~~~~
                1 error
                """
            )
    }

    @Test
    fun `can't be suppressed`() {
        lintWith(
                ConstantLogTextDetector.ISSUE,
                loggingSources,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import io.github.dewfallapp.core.logging.Logger

                    @Suppress("DewfallLogTextNotConstant")
                    fun suppressedByKotlin(log: Logger, count: Int) {
                        log.info("Loaded ${'$'}count")
                    }

                    fun suppressedByComment(log: Logger, count: Int) {
                        //noinspection DewfallLogTextNotConstant
                        log.info("Loaded ${'$'}count")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:5: Error: Issue DewfallLogTextNotConstant is not allowed to be suppressed [LintError]
                @Suppress("DewfallLogTextNotConstant")
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:12: Error: Issue DewfallLogTextNotConstant is not allowed to be suppressed [LintError]
                    log.info("Loaded ＄count")
                             ~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:7: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info("Loaded ＄count")
                             ~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:12: Error: message must be constant text: a string literal, a const val, a @ConstantText parameter, or + of those, never a string template or a value built at run time [DewfallLogTextNotConstant]
                    log.info("Loaded ＄count")
                             ~~~~~~~~~~~~~~~
                4 errors
                """
            )
    }

    /** The parts of :core:logging that the check looks at, with the same names. */
    private val loggingSources =
        kotlin(
                "src/main/kotlin/io/github/dewfallapp/core/logging/Logging.kt",
                """
                package io.github.dewfallapp.core.logging

                @Target(AnnotationTarget.VALUE_PARAMETER)
                @Retention(AnnotationRetention.BINARY)
                annotation class ConstantText

                class LogField private constructor() {
                    companion object {
                        fun count(@ConstantText name: String, value: Int): LogField = LogField()
                    }
                }

                class Logger {
                    fun withTag(@ConstantText area: String): Logger = this

                    fun info(@ConstantText message: String, vararg fields: LogField) {}

                    fun warn(
                        @ConstantText message: String,
                        vararg fields: LogField,
                        error: Throwable? = null,
                    ) {}
                }
                """,
            )
            .indented()

    /**
     * Logger as another module sees it, compiled into a jar. lint-tests compiles only methods
     * without bodies, so the methods are abstract.
     */
    private val compiledLoggingSources =
        kotlin(
                "src/main/kotlin/io/github/dewfallapp/core/logging/Logging.kt",
                """
                package io.github.dewfallapp.core.logging

                @Target(AnnotationTarget.VALUE_PARAMETER)
                @Retention(AnnotationRetention.BINARY)
                annotation class ConstantText

                abstract class Logger {
                    abstract fun withTag(@ConstantText area: String): Logger

                    abstract fun info(@ConstantText message: String)
                }
                """,
            )
            .indented()

    /** A Java class with a string constant, and a static field that code can change. */
    private val javaMessages =
        java(
                "src/main/java/test/pkg/Messages.java",
                """
                package test.pkg;

                public final class Messages {
                    public static final String LOADED = "Loaded";

                    public static String changeable = "Loaded";
                }
                """,
            )
            .indented()
}
