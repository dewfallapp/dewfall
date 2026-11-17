package io.github.dewfallapp.lint

import kotlin.test.Test

class OtherLoggerDetectorTest {
    @Test
    fun `flags java dot util dot logging's Logger and LogManager`() {
        lintWith(
                OtherLoggerDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import java.util.logging.LogManager
                    import java.util.logging.Logger

                    fun load() {
                        Logger.getLogger("Repository").info("Loaded")
                        java.util.logging.Logger.getGlobal()
                        LogManager.getLogManager().reset()
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:7: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    Logger.getLogger("Repository").info("Loaded")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:7: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    Logger.getLogger("Repository").info("Loaded")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:8: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    java.util.logging.Logger.getGlobal()
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:9: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    LogManager.getLogManager().reset()
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:9: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    LogManager.getLogManager().reset()
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                5 errors
                """
            )
    }

    @Test
    fun `flags java dot util dot logging's handlers, which write without a Logger`() {
        lintWith(
                OtherLoggerDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import java.util.logging.ConsoleHandler
                    import java.util.logging.FileHandler

                    fun handlers() = listOf(ConsoleHandler(), FileHandler("repository.log"))
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                fun handlers() = listOf(ConsoleHandler(), FileHandler("repository.log"))
                                        ~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:6: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                fun handlers() = listOf(ConsoleHandler(), FileHandler("repository.log"))
                                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors
                """
            )
    }

    @Test
    fun `flags LogPrinter and EventLog`() {
        lintWith(
                OtherLoggerDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    import android.util.EventLog
                    import android.util.Log
                    import android.util.LogPrinter

                    fun load() {
                        LogPrinter(Log.INFO, "Repository").println("Loaded")
                        EventLog.writeEvent(EventLog.getTagCode("loaded"), "Loaded")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:8: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    LogPrinter(Log.INFO, "Repository").println("Loaded")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:9: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    EventLog.writeEvent(EventLog.getTagCode("loaded"), "Loaded")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:9: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    EventLog.writeEvent(EventLog.getTagCode("loaded"), "Loaded")
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                3 errors
                """
            )
    }

    @Test
    fun `flags Java's System dot Logger in a plain Kotlin JVM module`() {
        lintJvmWith(
                OtherLoggerDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    fun load() {
                        System.getLogger("Repository").log(System.Logger.Level.INFO, "Loaded")
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:4: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    System.getLogger("Repository").log(System.Logger.Level.INFO, "Loaded")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:4: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    System.getLogger("Repository").log(System.Logger.Level.INFO, "Loaded")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors
                """
            )
    }

    @Test
    fun `flags another logger in test sources`() {
        lintWith(
                OtherLoggerDetector.ISSUE,
                testKotlin(
                    "test/pkg/RepositoryTest.kt",
                    """
                    package test.pkg

                    class RepositoryTest {
                        fun loads() {
                            java.util.logging.Logger.getLogger("RepositoryTest").severe("Failed")
                        }
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/test/kotlin/test/pkg/RepositoryTest.kt:5: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                        java.util.logging.Logger.getLogger("RepositoryTest").severe("Failed")
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/kotlin/test/pkg/RepositoryTest.kt:5: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                        java.util.logging.Logger.getLogger("RepositoryTest").severe("Failed")
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors
                """
            )
    }

    @Test
    fun `allows methods with the same names on Dewfall's own Logger`() {
        lintWith(
                OtherLoggerDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    class Logger {
                        fun withTag(area: String): Logger = this

                        fun info(message: String) {}
                    }

                    fun load(logger: Logger) {
                        logger.withTag("Repository").info("Loaded")
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
                OtherLoggerDetector.ISSUE,
                mainKotlin(
                    "test/pkg/Repository.kt",
                    """
                    package test.pkg

                    @Suppress("DewfallOtherLogger")
                    fun load() {
                        java.util.logging.Logger.getGlobal()
                    }

                    fun loadAgain() {
                        //noinspection DewfallOtherLogger
                        java.util.logging.Logger.getGlobal()
                    }
                    """,
                ),
            )
            .run()
            .expect(
                """
                src/main/kotlin/test/pkg/Repository.kt:3: Error: Issue DewfallOtherLogger is not allowed to be suppressed [LintError]
                @Suppress("DewfallOtherLogger")
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:10: Error: Issue DewfallOtherLogger is not allowed to be suppressed [LintError]
                    java.util.logging.Logger.getGlobal()
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:5: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    java.util.logging.Logger.getGlobal()
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/kotlin/test/pkg/Repository.kt:10: Error: Log through the Logger from :core:logging, not another logger [DewfallOtherLogger]
                    java.util.logging.Logger.getGlobal()
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                4 errors
                """
            )
    }
}
