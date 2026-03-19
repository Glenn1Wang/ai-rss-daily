package com.rss

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

data class FeedConfig(
    val name: String,
    val output: String = "feed.xml",
    @SerializedName("article_count")
    val articleCount: Int = 10,
    @SerializedName("rss_urls")
    val rssUrls: List<String>,
    @SerializedName("prompt_focus")
    val promptFocus: String
)

data class AppConfig(
    val feeds: List<FeedConfig>
)

object Config {
    private val gson = Gson()

    fun load(path: String = "config.json"): AppConfig {
        val file = File(path)
        require(file.exists()) { "配置文件不存在: $path" }
        return gson.fromJson(file.readText(), AppConfig::class.java)
    }
}
