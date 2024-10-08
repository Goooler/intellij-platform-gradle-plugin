// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.*
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.attributes.ComposedJarRule
import org.jetbrains.intellij.platform.gradle.attributes.DistributionRule
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.companion.JarCompanion
import org.jetbrains.intellij.platform.gradle.tasks.companion.TestCompanion
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.create

@Suppress("unused")
abstract class IntelliJPlatformModulePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.MODULE}")

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        with(project.configurations) configurations@{
            val intellijPlatformComposedJarConfiguration = create(Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR) {
                extendsFrom(
                    this@configurations[Configurations.External.IMPLEMENTATION],
                    this@configurations[Configurations.External.RUNTIME_ONLY],
                )
            }
            val intellijPlatformDistributionConfiguration = create(Configurations.INTELLIJ_PLATFORM_DISTRIBUTION)

            listOf(
                intellijPlatformComposedJarConfiguration,
                intellijPlatformDistributionConfiguration,
            ).forEach {
                it.isCanBeConsumed = true
                it.isCanBeResolved = false

                it.attributes {
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EXTERNAL))
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
                    attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, project.provider {
                        project.the<JavaPluginExtension>().targetCompatibility.majorVersion.toInt()
                    })
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
                    attributes.attribute(Attribute.of("org.gradle.jvm.environment", String::class.java), "standard-jvm")
                    attributes.attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java), "jvm")
                }
            }

            val intellijPlatformPluginModuleConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
                description = "IntelliJ Platform plugin module",
            ) { isTransitive = false }

            named(Configurations.INTELLIJ_PLATFORM_DEPENDENCIES) {
                extendsFrom(intellijPlatformPluginModuleConfiguration)
            }


            listOf(
                Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR,
                Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH,
                Configurations.INTELLIJ_PLATFORM_RUNTIME_CLASSPATH,
                Configurations.External.COMPILE_CLASSPATH,
                Configurations.External.TEST_COMPILE_CLASSPATH,
                // TODO: required for test fixtures?
                //       Configurations.External.TEST_FIXTURES_COMPILE_CLASSPATH,
                Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
            ).forEach {
                named(it) {
                    attributes {
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(Attributes.COMPOSED_JAR_NAME))
                    }
                }
            }

            listOf(
                Configurations.INTELLIJ_PLATFORM_DISTRIBUTION,
                Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
                Configurations.INTELLIJ_PLATFORM_PLUGIN,
            ).forEach {
                named(it) {
                    attributes {
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(Attributes.DISTRIBUTION_NAME))
                    }
                }
            }
        }

        with(project.dependencies) {
            attributesSchema {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE) {
                    compatibilityRules.add(ComposedJarRule::class)
                    compatibilityRules.add(DistributionRule::class)
                }
            }
        }

        IntelliJPlatformTestingExtension.register(project, target = project)

        listOf(
            // Build Module
            GenerateManifestTask,
            JarCompanion,
            InstrumentCodeTask,
            InstrumentedJarTask,
            ComposedJarTask,
            PrepareSandboxTask,

            // Test Module
            PrepareTestTask,
            TestCompanion,
            TestIdeTask,

            // Verify Module
            VerifyPluginProjectConfigurationTask,
        ).forEach {
            it.register(project)
        }
    }
}
