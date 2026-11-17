package io.github.dewfallapp.lint

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Issue

/**
 * A build file that makes lint-tests lay the project out as Gradle does, so src/main holds main
 * sources and src/test holds test sources.
 */
private val gradleModule = gradle("apply plugin: 'com.android.application'")

/**
 * lint-tests' runner for [issue], with the Gradle layout. lint-tests runs every test again in
 * several modes that rewrite the source, such as with fully qualified names, import aliases or
 * extra parentheses, and checks that the result stays the same. It skips the mode that adds a
 * suppression to each finding and expects it to go away, because nothing can suppress Dewfall's
 * issues. Each detector's own test checks that instead.
 */
internal fun lintWith(
    issue: Issue,
    vararg files: TestFile,
): TestLintTask =
    lint().files(gradleModule, *files).issues(issue).skipTestModes(TestMode.SUPPRESSIBLE)

/** A Kotlin file at [path] under src/main/kotlin. */
internal fun mainKotlin(path: String, source: String): TestFile =
    kotlin("src/main/kotlin/$path", source).indented()

/**
 * A Kotlin file at [path] under src/test/kotlin. lint-tests gives test sources no Kotlin standard
 * library, so a call into it, such as println, can't be resolved there. Tests of test sources use
 * Java's classes instead, such as System.out.
 */
internal fun testKotlin(path: String, source: String): TestFile =
    kotlin("src/test/kotlin/$path", source).indented()
