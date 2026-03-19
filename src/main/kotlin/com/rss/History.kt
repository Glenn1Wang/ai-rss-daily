package com.rss

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class HistoryRecord(
    val urls: MutableSet<String> = mutableSetOf()
)

object History {

    private val gson = Gson()
    private const val HISTORY_FILE = "history.json"
    private const val MAX_HISTORY = 500 // 最多记录 500 条，避免文件过大

    private var record: HistoryRecord = load()

    private fun load(): HistoryRecord {
        val file = File(HISTORY_FILE)
        if (!file.exists()) return HistoryRecord()
        return try {
            val type = object : TypeToken<HistoryRecord>() {}.type
            gson.fromJson(file.readText(), type) ?: HistoryRecord()
        } catch (e: Exception) {
            System.err.println("读取历史记录失败: ${e.message}")
            HistoryRecord()
        }
    }

    fun isNew(url: String): Boolean {
        return url.isNotEmpty() && url !in record.urls
    }

    fun filterNew(articles: List<RawArticle>): List<RawArticle> {
        val filtered = articles.filter { isNew(it.link) }
        val removed = articles.size - filtered.size
        if (removed > 0) {
            println("历史去重：移除 $removed 篇已推送文章")
        }
        return filtered
    }

    fun markPushed(urls: List<String>) {
        record.urls.addAll(urls)
        // 超出上限时移除最早的记录
        if (record.urls.size > MAX_HISTORY) {
            val excess = record.urls.size - MAX_HISTORY
            val iterator = record.urls.iterator()
            repeat(excess) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }

    fun save() {
        File(HISTORY_FILE).writeText(gson.toJson(record))
        println("已保存历史记录（共 ${record.urls.size} 条）")
    }
}
