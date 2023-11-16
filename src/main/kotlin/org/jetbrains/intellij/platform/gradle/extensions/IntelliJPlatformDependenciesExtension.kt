// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.GradleException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.VERSION_LATEST
import org.jetbrains.intellij.platform.gradle.model.IvyModule
import org.jetbrains.intellij.platform.gradle.model.IvyModule.*
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import org.jetbrains.intellij.platform.gradle.model.bundledPlugins
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.utils.LatestVersionResolver
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.io.path.*
import kotlin.math.absoluteValue

internal typealias DependencyAction = (Dependency.() -> Unit)

@Suppress("unused", "MemberVisibilityCanBePrivate")
@IntelliJPlatform
abstract class IntelliJPlatformDependenciesExtension @Inject constructor(
    private val configurations: ConfigurationContainer,
    private val repositories: RepositoryHandler,
    private val dependencies: DependencyHandler,
    private val providers: ProviderFactory,
    private val gradle: Gradle,
) {

    fun create(
        type: Provider<*>,
        version: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = addIntelliJPlatformDependency(type, version, configurationName, action)

    fun create(
        type: Provider<*>,
        version: String,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = addIntelliJPlatformDependency(type, providers.provider { version }, configurationName, action)

    fun create(
        type: IntelliJPlatformType,
        version: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = addIntelliJPlatformDependency(providers.provider { type }, version, configurationName, action)

    fun create(
        type: String,
        version: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = addIntelliJPlatformDependency(providers.provider { IntelliJPlatformType.fromCode(type) }, version, configurationName, action)

    fun create(
        type: IntelliJPlatformType,
        version: String,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = addIntelliJPlatformDependency(providers.provider { type }, providers.provider { version }, configurationName, action)

    fun create(
        type: String,
        version: String,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = addIntelliJPlatformDependency(providers.provider { IntelliJPlatformType.fromCode(type) }, providers.provider { version }, configurationName, action)

    fun androidStudio(version: String) = create(IntelliJPlatformType.AndroidStudio, version)
    fun androidStudio(version: Provider<String>) = create(IntelliJPlatformType.AndroidStudio, version)

    fun clion(version: String) = create(IntelliJPlatformType.CLion, version)
    fun clion(version: Provider<String>) = create(IntelliJPlatformType.CLion, version)

    fun gateway(version: String) = create(IntelliJPlatformType.Gateway, version)
    fun gateway(version: Provider<String>) = create(IntelliJPlatformType.Gateway, version)

    fun goland(version: String) = create(IntelliJPlatformType.GoLand, version)
    fun goland(version: Provider<String>) = create(IntelliJPlatformType.GoLand, version)

    fun intellijIdeaCommunity(version: String) = create(IntelliJPlatformType.IntellijIdeaCommunity, version)
    fun intellijIdeaCommunity(version: Provider<String>) = create(IntelliJPlatformType.IntellijIdeaCommunity, version)

    fun intellijIdeaUltimate(version: String) = create(IntelliJPlatformType.IntellijIdeaUltimate, version)
    fun intellijIdeaUltimate(version: Provider<String>) = create(IntelliJPlatformType.IntellijIdeaUltimate, version)

    fun phpstorm(version: String) = create(IntelliJPlatformType.PhpStorm, version)
    fun phpstorm(version: Provider<String>) = create(IntelliJPlatformType.PhpStorm, version)

    fun pycharmProfessional(version: String) = create(IntelliJPlatformType.PyCharmProfessional, version)
    fun pycharmProfessional(version: Provider<String>) = create(IntelliJPlatformType.PyCharmProfessional, version)

    fun pycharmCommunity(version: String) = create(IntelliJPlatformType.PyCharmCommunity, version)
    fun pycharmCommunity(version: Provider<String>) = create(IntelliJPlatformType.PyCharmCommunity, version)

    fun rider(version: String) = create(IntelliJPlatformType.Rider, version)
    fun rider(version: Provider<String>) = create(IntelliJPlatformType.Rider, version)

    fun local(
        localPath: Provider<*>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL_INSTANCE,
        action: DependencyAction = {},
    ) = addIntelliJPlatformLocalDependency(localPath, configurationName, action)

    fun local(
        localPath: String,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL_INSTANCE,
        action: DependencyAction = {},
    ) = local(providers.provider { localPath }, configurationName, action)

    fun local(
        localPath: File,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL_INSTANCE,
        action: DependencyAction = {},
    ) = local(providers.provider { localPath.absolutePath }, configurationName, action)

    fun jetbrainsRuntime(
        explicitVersion: Provider<String>,
        configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
        action: DependencyAction = {},
    ) = addJbrDependency(explicitVersion, configurationName, action)

    fun jetbrainsRuntime(
        explicitVersion: String,
        configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
        action: DependencyAction = {},
    ) = addJbrDependency(providers.provider { explicitVersion }, configurationName, action)

    fun jetbrainsRuntime(
        version: Provider<String>,
        variant: Provider<String>,
        architecture: Provider<String>,
        configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
        action: DependencyAction = {},
    ) = jetbrainsRuntime(providers.provider { from(version.get(), variant.orNull, architecture.orNull) }, configurationName, action)

    fun jetbrainsRuntime(
        version: String,
        variant: String,
        architecture: String,
        configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
        action: DependencyAction = {},
    ) = jetbrainsRuntime(providers.provider { from(version, variant, architecture) }, configurationName, action)

    fun jetbrainsRuntime(
        version: String,
        variant: String,
        architecture: String,
    ) = jetbrainsRuntime(from(version, variant, architecture))

    fun plugin(
        id: Provider<String>,
        version: Provider<String>,
        channel: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGINS,
        action: DependencyAction = {},
    ) = addIntelliJPlatformPlugin(id, version, channel, configurationName, action)

    fun plugin(
        id: String,
        version: String,
        channel: String = "",
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGINS,
        action: DependencyAction = {},
    ) = plugin(providers.provider { id }, providers.provider { version }, providers.provider { channel }, configurationName, action)

    fun bundledPlugin(
        id: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
        action: DependencyAction = {},
    ) = addIntelliJPlatformBundledPlugin(id, configurationName, action)

    fun bundledPlugin(
        id: String,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
        action: DependencyAction = {},
    ) = bundledPlugin(providers.provider { id }, configurationName, action)

    fun pluginVerifier(
        version: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER,
        action: DependencyAction = {},
    ) = addPluginVerifier(version, configurationName, action)

    fun pluginVerifier(
        version: String = VERSION_LATEST,
        configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER,
        action: DependencyAction = {},
    ) = pluginVerifier(providers.provider { version }, configurationName, action)

    private fun addIntelliJPlatformDependency(
        typeProvider: Provider<*>,
        versionProvider: Provider<String>,
        configurationName: String,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        typeProvider.map {
            when (it) {
                is IntelliJPlatformType -> it
                is String -> IntelliJPlatformType.fromCode(it)
                else -> throw IllegalArgumentException("Invalid argument type: ${it.javaClass}. Supported types: String or IntelliJPlatformType")
            }
        }.zip(versionProvider) { type, version ->
            dependencies.create(
                group = type.groupId,
                name = type.artifactId,
                version = version,
            )
        },
        action,
    )

    private fun addIntelliJPlatformLocalDependency(
        localPathProvider: Provider<*>,
        configurationName: String,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        localPathProvider.map { localPath ->
            val artifactPath = when (localPath) {
                is String -> localPath
                is File -> localPath.absolutePath
                else -> throw IllegalArgumentException("Invalid argument type: ${localPath.javaClass}. Supported types: String or File")
            }.let {
                Path.of(it)
            }.let {
                it.takeUnless { OperatingSystem.current().isMacOsX && it.extension == "app" } ?: it.resolve("Contents")
            }

            if (!artifactPath.exists() || !artifactPath.isDirectory()) {
                throw BuildException("Specified localPath '$localPath' doesn't exist or is not a directory")
            }

            val productInfo = artifactPath.productInfo()
            val type = IntelliJPlatformType.fromCode(productInfo.productCode)
            if (Version.parse(productInfo.buildNumber) < Version.parse(MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION)) {
                throw GradleException("The minimal supported IDE version is $MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION+, the provided version is too low: ${productInfo.version} (${productInfo.buildNumber})")
            }
            val path = artifactPath.pathString
            val version = "${productInfo.version}:local+${path.hashCode()}"

            dependencies.create(
                group = type.groupId,
                name = type.artifactId,
                version = version,
            ).also {
                it.createIvyDependency(
                    publications = listOf(
                        Publication(path, "directory", null, "default")
                    )
                )
            }
        },
        action,
    )

    private fun addJbrDependency(
        explicitVersionProvider: Provider<String>,
        configurationName: String,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        explicitVersionProvider.map { explicitVersion ->
            val dependency = dependencies.create(
                group = "com.jetbrains",
                name = "jbr",
                version = explicitVersion,
                ext = "tar.gz",
            )

            repositories.ivy {
                url = URI(Locations.JETBRAINS_RUNTIME_REPOSITORY)
                patternLayout { artifact("[revision].tar.gz") }
                metadataSources { artifact() }
            }

            dependency
        },
        action,
    )

    private fun addIntelliJPlatformPlugin(
        idProvider: Provider<String>,
        versionProvider: Provider<String>,
        channelProvider: Provider<String>,
        configurationName: String,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        providers.provider {
            val channel = channelProvider.orNull?.trim()
            val id = idProvider.get()
            val version = versionProvider.get()

            val group = when (channel) {
                "default", "", null -> JETBRAINS_MARKETPLACE_MAVEN_GROUP
                else -> "$channel.$JETBRAINS_MARKETPLACE_MAVEN_GROUP"
            }

            dependencies.create(
                group = group,
                name = id,
                version = version,
            )
        },
        action,
    )

    private fun addIntelliJPlatformBundledPlugin(
        idProvider: Provider<String>,
        configurationName: String,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        idProvider.map { id ->
            val productInfo = configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO).single().toPath().productInfo()
            val type = IntelliJPlatformType.fromCode(productInfo.productCode)
            val bundledPluginsList = configurations.getByName(Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST).single().toPath().bundledPlugins()
            val bundledPlugin = bundledPluginsList.plugins.find { it.id == id }.throwIfNull { throw Exception("Bundled plugin '$id' does not exist") }
            val artifactPath = bundledPlugin.let { Path.of(it.path) }
            val jars = artifactPath.resolve("lib").listDirectoryEntries("*.jar")

            dependencies.create(
                group = type.groupId,
                name = "${type.artifactId}:${productInfo.version}",
                version = "$id:bundled+${artifactPath.hashCode().absoluteValue}",
            ).also {
                it.createIvyDependency(publications = jars.map { jar ->
                    Publication(jar.pathString, "jar", "jar", "default")
                })
            }
        },
        action,
    )

    private fun addPluginVerifier(
        versionProvider: Provider<String>,
        configurationName: String,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        versionProvider.map { version ->
            dependencies.create(
                group = "org.jetbrains.intellij.plugins",
                name = "verifier-cli",
                version = when (version) {
                    VERSION_LATEST -> LatestVersionResolver.fromMaven(
                        "IntelliJ Plugin Verifier",
                        "${Locations.PLUGIN_VERIFIER_REPOSITORY}/org/jetbrains/intellij/plugins/verifier-cli/maven-metadata.xml",
                    )

                    else -> version
                },
                classifier = "all",
                ext = "jar",
            )
        },
        action,
    )

    private fun ExternalModuleDependency.createIvyDependency(publications: List<Publication>) {
        val ivyDirectory = gradle.projectCacheDir.resolve("intellijPlatform/ivy").toPath()
        val ivyFileName = "$group-$name-$version.xml"
        val ivyFile = ivyDirectory.resolve(ivyFileName).takeUnless { it.exists() } ?: return

        val extractor = XmlExtractor<IvyModule>()
        val ivyModule = IvyModule(
            info = Info(
                organisation = group,
                module = name,
                revision = version,
                publication = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
            ),
            configurations = listOf(
                Configuration(
                    name = "default",
                    visibility = "public",
                ),
            ),
            publications = publications,
        )

        ivyFile.parent.createDirectories()
        ivyFile.createFile()
        extractor.marshal(ivyModule, ivyFile)
    }
}

// TODO: cleanup JBR helper functions:
private fun from(jbrVersion: String, jbrVariant: String?, jbrArch: String?, operatingSystem: OperatingSystem = OperatingSystem.current()): String {
    val version = "8".takeIf { jbrVersion.startsWith('u') }.orEmpty() + jbrVersion
    var prefix = getPrefix(version, jbrVariant)
    val lastIndexOfB = version.lastIndexOf('b')
    val lastIndexOfDash = version.lastIndexOf('-') + 1
    val majorVersion = when (lastIndexOfB > -1) {
        true -> version.substring(lastIndexOfDash, lastIndexOfB)
        false -> version.substring(lastIndexOfDash)
    }
    val buildNumberString = when (lastIndexOfB > -1) {
        (lastIndexOfDash == lastIndexOfB) -> version.substring(0, lastIndexOfDash - 1)
        true -> version.substring(lastIndexOfB + 1)
        else -> ""
    }
    val buildNumber = Version.parse(buildNumberString)
    val isJava8 = majorVersion.startsWith("8")
    val isJava17 = majorVersion.startsWith("17")

    val oldFormat = prefix == "jbrex" || isJava8 && buildNumber < Version.parse("1483.24")
    if (oldFormat) {
        return "jbrex${majorVersion}b${buildNumberString}_${platform(operatingSystem)}_${arch(false)}"
    }

    val arch = jbrArch ?: arch(isJava8)
    if (prefix.isEmpty()) {
        prefix = when {
            isJava17 -> "jbr_jcef-"
            isJava8 -> "jbrx-"
            operatingSystem.isMacOsX && arch == "aarch64" -> "jbr_jcef-"
            buildNumber < Version.parse("1319.6") -> "jbr-"
            else -> "jbr_jcef-"
        }
    }

    return "$prefix$majorVersion-${platform(operatingSystem)}-$arch-b$buildNumberString"
}

private fun getPrefix(version: String, variant: String?) = when {
    !variant.isNullOrEmpty() -> when (variant) {
        "sdk" -> "jbrsdk-"
        else -> "jbr_$variant-"
    }

    version.startsWith("jbrsdk-") -> "jbrsdk-"
    version.startsWith("jbr_jcef-") -> "jbr_jcef-"
    version.startsWith("jbr_dcevm-") -> "jbr_dcevm-"
    version.startsWith("jbr_fd-") -> "jbr_fd-"
    version.startsWith("jbr_nomod-") -> "jbr_nomod-"
    version.startsWith("jbr-") -> "jbr-"
    version.startsWith("jbrx-") -> "jbrx-"
    version.startsWith("jbrex8") -> "jbrex"
    else -> ""
}

internal fun platform(operatingSystem: OperatingSystem) = when {
    operatingSystem.isWindows -> "windows"
    operatingSystem.isMacOsX -> "osx"
    else -> "linux"
}

internal fun arch(newFormat: Boolean): String {
    val arch = System.getProperty("os.arch")
    if ("aarch64" == arch || "arm64" == arch) {
        return "aarch64"
    }
    if ("x86_64" == arch || "amd64" == arch) {
        return "x64"
    }
    val name = System.getProperty("os.name")
    if (name.contains("Windows") && System.getenv("ProgramFiles(x86)") != null) {
        return "x64"
    }
    return when (newFormat) {
        true -> "i586"
        false -> "x86"
    }
}
