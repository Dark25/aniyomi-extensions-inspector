package inspector.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import inspector.util.PackageTools.EXTENSION_FEATURE
import inspector.util.PackageTools.LIB_VERSION_MAX
import inspector.util.PackageTools.LIB_VERSION_MIN
import inspector.util.PackageTools.METADATA_SOURCE_CLASS
import inspector.util.PackageTools.dex2jar
import inspector.util.PackageTools.getPackageInfo
import inspector.util.PackageTools.loadExtensionSources
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

object AnimeExtension {
    private val logger = KotlinLogging.logger {}

    suspend fun installApk(
        tmpDir: File,
        fetcher: suspend () -> File,
    ): Pair<String, List<AnimeHttpSource>> {
        val apkFile = fetcher()

        val jarFile = File(tmpDir, "${apkFile.nameWithoutExtension}.jar")
            .also(File::deleteOnExit)

        val packageInfo = getPackageInfo(apkFile.absolutePath)

        if (!packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }) {
            throw Exception("This apk is not a Aniyomi extension")
        }

        // Validate lib version
        val libVersion = packageInfo.versionName.substringBeforeLast('.').toDouble()
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            throw Exception(
                "Lib version is $libVersion, while only versions " +
                    "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed",
            )
        }

        val className = packageInfo.packageName + packageInfo.applicationInfo.metaData.getString(
            METADATA_SOURCE_CLASS,
        )

        logger.trace { "Main class for extension is $className" }

        dex2jar(apkFile, jarFile)

        // collect sources from the extension
        return packageInfo.packageName to when (val instance = loadExtensionSources(jarFile, className)) {
            is AnimeSource -> listOf(instance).filterIsInstance<AnimeHttpSource>()
            is AnimeSourceFactory -> instance.createSources().filterIsInstance<AnimeHttpSource>()
            else -> throw RuntimeException("Unknown source class type! ${instance.javaClass}")
        }
    }
}
