package io.github.dewfallapp.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/**
 * Lint's libraries add 23 to AGP's major version, so AGP 9.4.1 comes with lint 32.4.1:
 * https://github.com/googlesamples/android-custom-lint-rules/blob/5751b4f0b7d6c5af67b990e7e06571179baa1e07/README.md#lint-version
 */
private const val LINT_MAJOR_VERSION_OFFSET = 23

/**
 * Lint's own checks for code that implements lint checks, such as LintImplTextFormat. Lint turns
 * them off by default, so this module turns them on. These are all that lint-checks registers. It
 * doesn't register MissingVendor, so DewfallIssueRegistryTest checks the vendor instead.
 */
private val lintCheckCodeIssues =
    setOf(
        "LintDocExample",
        "LintImplBadUrl",
        "LintImplDollarEscapes",
        "LintImplIdFormat",
        "LintImplPsiEquals",
        "LintImplTextFormat",
        "LintImplTrimIndent",
        "LintImplUnexpectedDomain",
        "LintImplUseKotlin",
        "LintImplUseUast",
        "UElementAsPsi",
        "UastImplementation",
    )

/**
 * Settings for :lint-rules, the plain Kotlin/JVM module that holds Dewfall's own lint checks. Its
 * code runs inside lint during the build and never ships in the app. See
 * docs/architecture.md#dewfalls-lint-checks.
 */
class LintRulesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            // Lint checks this module too, with its checks for lint check code turned on below.
            pluginManager.apply("com.android.lint")
            pluginManager.apply(DetektConventionPlugin::class.java)

            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    configureDewfallKotlin()
                    // Lint runs the checks on its own Kotlin standard library, 2.2.10 for lint
                    // 32.4.1, so the checks may only call the standard library of Kotlin 2.2.
                    apiVersion = KotlinVersion.KOTLIN_2_2
                }
            }

            configureJavaToolchain()
            configureJvmUnitTests()
            extensions.configure<Lint> {
                configureDewfallLint()
                enable += lintCheckCodeIssues
            }

            val lintVersion = lintVersionForAgp()
            dependencies {
                // Lint itself provides lint-api's classes when it runs the checks.
                add("compileOnly", "com.android.tools.lint:lint-api:$lintVersion")
                // The tests use lint-api's types too, and run the checks with lint-tests.
                add("testImplementation", "com.android.tools.lint:lint-api:$lintVersion")
                add("testImplementation", "com.android.tools.lint:lint-tests:$lintVersion")
            }
        }
    }
}

/**
 * The version of lint's libraries that comes with the catalog's AGP version. It follows AGP, so a
 * Renovate update of AGP updates lint too, and the two never differ.
 */
private fun Project.lintVersionForAgp(): String {
    val agpVersion =
        extensions
            .getByType<VersionCatalogsExtension>()
            .named("libs")
            .findVersion("agp")
            .get()
            .requiredVersion
    val agpMajorVersion = agpVersion.substringBefore('.').toInt()
    return "${agpMajorVersion + LINT_MAJOR_VERSION_OFFSET}.${agpVersion.substringAfter('.')}"
}
