package org.fcitx.fcitx5.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.appContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

typealias SHA256 = String

object DataManager {

    @Serializable
    data class DataDescriptor(
        val sha256: SHA256,
        val files: Map<String, SHA256>
    )

    sealed class Diff {
        abstract val key: String
        abstract val order: Int

        data class New(override val key: String, val new: String) : Diff() {
            override val order: Int
                get() = 3
        }

        data class Update(override val key: String, val old: String, val new: String) : Diff() {
            override val order: Int
                get() = 2
        }

        data class Delete(override val key: String, val old: String) : Diff() {
            override val order: Int
                get() = 0
        }

        data class DeleteDir(override val key: String) : Diff() {
            override val order: Int
                get() = 1
        }
    }

    val dataDir = File(appContext.applicationInfo.dataDir)
    private val destDescriptorFile = File(dataDir, Const.dataDescriptorName)

    private val lock = ReentrantLock()

    // should be consistent with the deserialization in build.gradle.kts (:app)
    private fun deserialize(raw: String) = runCatching {
        Json.decodeFromString<DataDescriptor>(raw)
    }

    private fun diff(old: DataDescriptor, new: DataDescriptor): List<Diff> =
        if (old.sha256 == new.sha256)
            listOf()
        else
            new.files.mapNotNull {
                when {
                    // empty sha256 -> dir
                    it.key !in old.files && it.value.isNotBlank() -> Diff.New(it.key, it.value)
                    old.files[it.key] != it.value ->
                        // if the new one is not a dir
                        if (it.value.isNotBlank())
                            Diff.Update(
                                it.key,
                                old.files.getValue(it.key),
                                it.value
                            )
                        else null
                    else -> null
                }
            }.toMutableList().apply {
                addAll(old.files.filterKeys { it !in new.files }
                    .map {
                        if (it.value.isNotBlank())
                            Diff.Delete(it.key, it.value)
                        else
                            Diff.DeleteDir(it.key)
                    })
            }

    fun sync() = lock.withLock {
        val destDescriptor =
            destDescriptorFile
                .takeIf { it.exists() && it.isFile }
                ?.runCatching { readText() }
                ?.getOrNull()
                ?.let { deserialize(it) }
                ?.getOrNull()
                ?: DataDescriptor("", mapOf())

        val bundledDescriptor =
            appContext.assets
                .open(Const.dataDescriptorName)
                .bufferedReader()
                .use { it.readText() }
                .let { deserialize(it) }
                .getOrThrow()

        val d = diff(destDescriptor, bundledDescriptor).sortedBy { it.order }
        d.forEach {
            Timber.d("Diff: $it")
            when (it) {
                is Diff.Delete -> deleteFile(it.key)
                is Diff.DeleteDir -> deleteDir(it.key)
                is Diff.New -> copyFile(it.key)
                is Diff.Update -> copyFile(it.key)
            }
        }

        copyFile(Const.dataDescriptorName)

        Timber.i("DataManager Synced!")
    }

    fun deleteAndSync() {
        dataDir.deleteRecursively()
        sync()
    }

    private fun deleteFile(path: String) {
        val file = File(dataDir, path)
        if (file.exists() && file.isFile)
            file.delete()
    }

    private fun deleteDir(path: String) {
        val dir = File(dataDir, path)
        if (dir.exists() && dir.isDirectory)
            dir.deleteRecursively()
    }

    private fun copyFile(filename: String) {
        appContext.assets.open(filename).use { i ->
            File(dataDir, filename)
                .also { it.parentFile?.mkdirs() }
                .outputStream()
                .use { o -> i.copyTo(o) }
        }
    }

}
