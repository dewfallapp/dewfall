package io.github.dewfallapp.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.binaryStub
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import kotlin.test.Test

class ExtractorLoggerDetectorTest {
    @Test
    fun `flags setLogger, imported or fully qualified`() {
        lintWith(
                ExtractorLoggerDetector.ISSUE,
                newPipeExtractor,
                mainKotlin(
                    "test/pkg/NewPipeSetup.kt",
                    """
                    package test.pkg

                    import org.schabi.newpipe.extractor.utils.ExtractorLogger
                    import org.schabi.newpipe.extractor.utils.Logger

                    fun setUp(logger: Logger) {
                        ExtractorLogger.setLogger(logger)
                        org.schabi.newpipe.extractor.utils.ExtractorLogger.setLogger(logger)
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/NewPipeSetup.kt:7: Error: Never use NewPipeExtractor's logger, whose lines hold URLs, titles and IDs [DewfallExtractorLogger]
                    ExtractorLogger.setLogger(logger)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/NewPipeSetup.kt:8: Error: Never use NewPipeExtractor's logger, whose lines hold URLs, titles and IDs [DewfallExtractorLogger]
                    org.schabi.newpipe.extractor.utils.ExtractorLogger.setLogger(logger)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors
                """
            )
    }

    @Test
    fun `flags setLogger through an import alias`() {
        lintWith(
                ExtractorLoggerDetector.ISSUE,
                newPipeExtractor,
                mainKotlin(
                    "test/pkg/NewPipeSetup.kt",
                    """
                    package test.pkg

                    import org.schabi.newpipe.extractor.utils.ExtractorLogger as NewPipeLog
                    import org.schabi.newpipe.extractor.utils.Logger

                    fun setUp(logger: Logger) {
                        NewPipeLog.setLogger(logger)
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/NewPipeSetup.kt:7: Error: Never use NewPipeExtractor's logger, whose lines hold URLs, titles and IDs [DewfallExtractorLogger]
                    NewPipeLog.setLogger(logger)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """
            )
    }

    @Test
    fun `flags a callable reference to setLogger`() {
        lintWith(
                ExtractorLoggerDetector.ISSUE,
                newPipeExtractor,
                mainKotlin(
                    "test/pkg/NewPipeSetup.kt",
                    """
                    package test.pkg

                    import org.schabi.newpipe.extractor.utils.ExtractorLogger
                    import org.schabi.newpipe.extractor.utils.Logger

                    val install: (Logger) -> Unit = ExtractorLogger::setLogger
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/NewPipeSetup.kt:6: Error: Never use NewPipeExtractor's logger, whose lines hold URLs, titles and IDs [DewfallExtractorLogger]
                val install: (Logger) -> Unit = ExtractorLogger::setLogger
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """
            )
    }

    @Test
    fun `flags setLogger in test sources`() {
        lintWith(
                ExtractorLoggerDetector.ISSUE,
                newPipeExtractor,
                testKotlin(
                    "test/pkg/NewPipeSetupTest.kt",
                    """
                    package test.pkg

                    import org.schabi.newpipe.extractor.utils.ExtractorLogger

                    class NewPipeSetupTest {
                        fun setsUp() {
                            ExtractorLogger.setLogger(null)
                        }
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/test/kotlin/test/pkg/NewPipeSetupTest.kt:7: Error: Never use NewPipeExtractor's logger, whose lines hold URLs, titles and IDs [DewfallExtractorLogger]
                        ExtractorLogger.setLogger(null)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """
            )
    }

    @Test
    fun `flags ExtractorLogger's logging calls and classes that implement its Logger`() {
        lintWith(
                ExtractorLoggerDetector.ISSUE,
                newPipeExtractor,
                mainKotlin(
                    "test/pkg/NewPipeSetup.kt",
                    """
                    package test.pkg

                    import org.schabi.newpipe.extractor.utils.ExtractorLogger
                    import org.schabi.newpipe.extractor.utils.Logger

                    class Recorder : Logger {
                        override fun debug(tag: String, message: String) = Unit
                    }

                    val bridge = object : Logger {
                        override fun debug(tag: String, message: String) = Unit
                    }

                    fun logs() {
                        ExtractorLogger.d("Tag", "Unset, so it writes nothing")
                        ExtractorLogger.w("Tag", "Unset, so it writes nothing")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/NewPipeSetup.kt:6: Error: Don't implement NewPipeExtractor's Logger, which only its setLogger takes [DewfallExtractorLogger]
                class Recorder : Logger {
                      ~~~~~~~~
                src/main/kotlin/test/pkg/NewPipeSetup.kt:10: Error: Don't implement NewPipeExtractor's Logger, which only its setLogger takes [DewfallExtractorLogger]
                val bridge = object : Logger {
                             ~~~~~~
                src/main/kotlin/test/pkg/NewPipeSetup.kt:15: Error: Never use NewPipeExtractor's logger, whose lines hold URLs, titles and IDs [DewfallExtractorLogger]
                    ExtractorLogger.d("Tag", "Unset, so it writes nothing")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/NewPipeSetup.kt:16: Error: Never use NewPipeExtractor's logger, whose lines hold URLs, titles and IDs [DewfallExtractorLogger]
                    ExtractorLogger.w("Tag", "Unset, so it writes nothing")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                4 errors
                """
            )
    }

    @Test
    fun `allows methods with the same names on other classes`() {
        lintWith(
                ExtractorLoggerDetector.ISSUE,
                newPipeExtractor,
                mainKotlin(
                    "test/pkg/NewPipeSetup.kt",
                    """
                    package test.pkg

                    object Settings {
                        fun setLogger(name: String) = Unit

                        fun d(name: String) = Unit
                    }

                    fun setUp() {
                        Settings.setLogger("Dewfall")
                        Settings.d("Dewfall")
                    }
                    """,
                ),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `can't be suppressed`() {
        lintWith(
                ExtractorLoggerDetector.ISSUE,
                newPipeExtractor,
                mainKotlin(
                    "test/pkg/NewPipeSetup.kt",
                    """
                    package test.pkg

                    import org.schabi.newpipe.extractor.utils.ExtractorLogger
                    import org.schabi.newpipe.extractor.utils.Logger

                    @Suppress("DewfallExtractorLogger")
                    fun setUp(logger: Logger) {
                        ExtractorLogger.setLogger(logger)
                    }

                    fun setUpAgain(logger: Logger) {
                        //noinspection DewfallExtractorLogger
                        ExtractorLogger.setLogger(logger)
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/NewPipeSetup.kt:6: Error: Issue DewfallExtractorLogger is not allowed to be suppressed [LintError]
                @Suppress("DewfallExtractorLogger")
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/NewPipeSetup.kt:13: Error: Issue DewfallExtractorLogger is not allowed to be suppressed [LintError]
                    ExtractorLogger.setLogger(logger)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/NewPipeSetup.kt:8: Error: Never use NewPipeExtractor's logger, whose lines hold URLs, titles and IDs [DewfallExtractorLogger]
                    ExtractorLogger.setLogger(logger)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/NewPipeSetup.kt:13: Error: Never use NewPipeExtractor's logger, whose lines hold URLs, titles and IDs [DewfallExtractorLogger]
                    ExtractorLogger.setLogger(logger)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                4 errors
                """
            )
    }

    /** NewPipeExtractor's ExtractorLogger, with the same name and the methods the tests call. */
    private val extractorLogger =
        java(
                "src/main/java/org/schabi/newpipe/extractor/utils/ExtractorLogger.java",
                """
                package org.schabi.newpipe.extractor.utils;

                public final class ExtractorLogger {
                    public static void setLogger(Logger logger) {}

                    public static void d(String tag, String message) {}

                    public static void w(String tag, String message) {}

                    public static void e(String tag, String message) {}
                }
                """,
            )
            .indented()

    /** NewPipeExtractor's Logger interface, which setLogger takes, with one of its methods. */
    private val newPipeLogger =
        java(
                "src/main/java/org/schabi/newpipe/extractor/utils/Logger.java",
                """
                package org.schabi.newpipe.extractor.utils;

                public interface Logger {
                    void debug(String tag, String message);
                }
                """,
            )
            .indented()

    /** The stubs above in a jar, as the YouTube layer gets NewPipeExtractor. */
    private val newPipeExtractor =
        binaryStub("libs/newpipe-extractor.jar", extractorLogger, newPipeLogger)
}
