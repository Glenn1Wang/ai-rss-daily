package com.rss

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

data class SourcesConfig(
    @SerializedName("rss_urls")
    val rssUrls: List<String>,
    @SerializedName("prompt_focus")
    val promptFocus: String
)

object Config {
    private val gson = Gson()

    fun load(path: String = "sources.json"): SourcesConfig {
        val file = File(path)
        require(file.exists()) { "配置文件不存在: $path" }
        return gson.fromJson(file.readText(), SourcesConfig::class.java)
    }
}
