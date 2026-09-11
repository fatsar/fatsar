package com.fatsar.hermes.core.store

import java.io.File

/** Basit anahtar/dosya deposu. Android tarafında uygulamanın özel klasörüne yazar. */
interface Storage {
    fun read(name: String): String?

    fun write(name: String, content: String)

    fun delete(name: String)

    fun list(prefix: String): List<String>
}

/** Testler ve önizleme için bellek içi depo. */
class MemoryStorage(initial: Map<String, String> = emptyMap()) : Storage {
    private val map = LinkedHashMap<String, String>(initial)

    override fun read(name: String): String? = map[name]

    override fun write(name: String, content: String) {
        map[name] = content
    }

    override fun delete(name: String) {
        map.remove(name)
    }

    override fun list(prefix: String): List<String> = map.keys.filter { it.startsWith(prefix) }.sorted()

    fun snapshot(): Map<String, String> = LinkedHashMap(map)
}

/**
 * Dosya tabanlı depo. Yazma işlemi önce .tmp dosyasına yapılıp taşınır;
 * böylece uygulama yazarken kapanırsa veri bozulmaz.
 */
class FileStorage(private val dir: File) : Storage {

    init {
        if (!dir.exists()) dir.mkdirs()
    }

    private fun fileOf(name: String) = File(dir, safe(name))

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    override fun read(name: String): String? = runCatching {
        val f = fileOf(name)
        if (f.exists()) f.readText() else null
    }.getOrNull()

    override fun write(name: String, content: String) {
        runCatching {
            val target = fileOf(name)
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(content)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        }
    }

    override fun delete(name: String) {
        runCatching { fileOf(name).delete() }
    }

    override fun list(prefix: String): List<String> =
        dir.listFiles()?.map { it.name }?.filter { it.startsWith(prefix) }?.sorted() ?: emptyList()
}
