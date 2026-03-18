package com.rss

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

data class RawArticle(
    val title: String,
    val link: String,
    val description: String,
    val source: String,
    val publishedDate: String
)

object RSSFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchAll(urls: List<String>, hoursBack: Long = 72): List<RawArticle> {
        val cutoff = Instant.now().minus(hoursBack, ChronoUnit.HOURS)
        val articles = mutableListOf<RawArticle>()

        for (url in urls) {
            try {
                println("正在抓取: $url")
                val fetched = fetchFeed(url, cutoff)
                println("  获取到 ${fetched.size} 篇文章")
                articles.addAll(fetched)
            } catch (e: Exception) {
                System.err.println("抓取失败 [$url]: ${e.message}")
            }
        }

        println("共抓取到 ${articles.size} 篇文章")
        return articles
    }

    private fun fetchFeed(url: String, cutoff: Instant): List<RawArticle> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AI-RSS-Curator/1.0")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body ?: return emptyList()

        val feed = SyndFeedInput().build(XmlReader(body.byteStream()))
        val sourceName = feed.title ?: url

        return feed.entries
            .filter { entry ->
                val pubDate = entry.publishedDate?.toInstant() ?: entry.updatedDate?.toInstant()
                pubDate == null || pubDate.isAfter(cutoff)
            }
            .map { entry ->
                RawArticle(
                    title = entry.title?.trim() ?: "无标题",
                    link = entry.link?.trim() ?: "",
                    description = (entry.description?.value ?: entry.contents?.firstOrNull()?.value ?: "")
                        .replace(Regex("<[^>]*>"), "") // 去除 HTML 标签
                        .take(500), // 截断过长摘要
                    source = sourceName,
                    publishedDate = (entry.publishedDate ?: entry.updatedDate)?.toString() ?: ""
                )
            }
    }
}
