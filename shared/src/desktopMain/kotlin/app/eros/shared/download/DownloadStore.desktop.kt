// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun root(): File {
    val dir = File(System.getProperty("user.home"), ".eros/downloads")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun galleryDir(gid: Long): File {
    val dir = File(root(), gid.toString())
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun manifestFile(gid: Long): File = File(galleryDir(gid), DownloadManifest.MANIFEST_NAME)

internal fun pageFileName(index: Int, extension: String): String =
    String.format("%03d.%s", index + 1, extension)

private val pageFileCache = HashMap<Long, Map<Int, File>>()

private fun pageFiles(gid: Long): Map<Int, File> = synchronized(pageFileCache) {
    pageFileCache[gid] ?: buildMap {
        val files = File(root(), gid.toString()).listFiles() ?: emptyArray()
        for (f in files) {
            if (!f.isFile) continue
            val index = f.nameWithoutExtension.toIntOrNull() ?: continue
            put(index - 1, f)
        }
    }.also { pageFileCache[gid] = it }
}

private fun invalidatePageFiles(gid: Long) {
    synchronized(pageFileCache) { pageFileCache.remove(gid) }
}

private fun atomicReplace(tmp: File, target: File) {
    try {
        Files.move(
            tmp.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

actual object DownloadStore {

    actual fun listManifests(): List<DownloadManifest> = runCatching {
        root().listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val f = File(dir, DownloadManifest.MANIFEST_NAME)
            if (!f.exists()) return@mapNotNull null
            runCatching {
                json.decodeFromString(DownloadManifest.serializer(), f.readText())
            }.getOrNull()
        } ?: emptyList()
    }.getOrDefault(emptyList())

    actual fun readManifest(gid: Long): DownloadManifest? = runCatching {
        val f = manifestFile(gid)
        if (!f.exists()) null
        else json.decodeFromString(DownloadManifest.serializer(), f.readText())
    }.getOrNull()

    actual fun writeManifest(manifest: DownloadManifest) {
        runCatching {
            val f = manifestFile(manifest.gid)
            val tmp = File(f.parentFile, "${DownloadManifest.MANIFEST_NAME}.tmp")
            tmp.writeText(json.encodeToString(DownloadManifest.serializer(), manifest))
            atomicReplace(tmp, f)
        }
    }

    actual fun writeManifestIfChanged(manifest: DownloadManifest): Boolean {
        val text = json.encodeToString(DownloadManifest.serializer(), manifest)
        return runCatching {
            val f = manifestFile(manifest.gid)
            if (f.exists() && f.readText() == text) return@runCatching false
            val tmp = File(f.parentFile, "${DownloadManifest.MANIFEST_NAME}.tmp")
            tmp.writeText(text)
            atomicReplace(tmp, f)
            true
        }.getOrDefault(false)
    }

    actual fun deleteGallery(gid: Long) {
        runCatching { File(root(), gid.toString()).deleteRecursively() }
        invalidatePageFiles(gid)
    }

    actual fun writePage(gid: Long, index: Int, extension: String, bytes: ByteArray): Long = runCatching {
        val dir = galleryDir(gid)
        val target = File(dir, pageFileName(index, extension))
        val tmp = File(dir, "${target.name}.part")
        tmp.writeBytes(bytes)
        if (tmp.length() < MIN_VALID_PAGE_BYTES || !looksLikeImage(bytes)) {
            tmp.delete()
            return@runCatching -1L
        }
        dir.listFiles()?.filter {
            it != target && it.nameWithoutExtension == target.nameWithoutExtension
        }?.forEach { it.delete() }
        atomicReplace(tmp, target)
        invalidatePageFiles(gid)
        target.length()
    }.getOrDefault(-1L)

    actual fun pageFileSize(gid: Long, index: Int): Long =
        runCatching { pageFiles(gid)[index]?.length() ?: 0L }.getOrDefault(0L)

    actual fun pageFileSizes(gid: Long): Map<Int, Long> =
        runCatching { pageFiles(gid).mapValues { it.value.length() } }.getOrDefault(emptyMap())

    actual fun readPage(gid: Long, index: Int): ByteArray? = runCatching {
        val f = pageFiles(gid)[index] ?: return@runCatching null
        if (f.length() > 0) f.readBytes() else null
    }.getOrNull()

    actual fun totalSize(): Long = runCatching {
        root().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)
}
