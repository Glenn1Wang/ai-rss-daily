package com.rss

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jdom2.Element
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

data class RawArticle(
    val title: String,
    val link: String,
    val description: String,
    val source: String,
    val publishedDate: String,
    val imageUrl: String
)

object RSSFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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
                val htmlContent = entry.description?.value ?: entry.contents?.firstOrNull()?.value ?: ""
                RawArticle(
                    title = entry.title?.trim() ?: "无标题",
                    link = entry.link?.trim() ?: "",
                    description = htmlContent
                        .replace(Regex("<[^>]*>"), "")
                        .take(500),
                    source = sourceName,
                    publishedDate = (entry.publishedDate ?: entry.updatedDate)?.toString() ?: "",
                    imageUrl = extractImageUrl(entry, htmlContent)
                )
            }
    }

    private fun extractImageUrl(entry: SyndEntry, htmlContent: String): String {
        // 1. 从 enclosure 中提取（最常见的封面图方式）
        entry.enclosures?.firstOrNull { it.type?.startsWith("image/") == true }?.let {
            return it.url
        }

        // 2. 从 media:content 或 media:thumbnail 中提取
        for (module in entry.foreignMarkup) {
            if (module is Element) {
                val url = extractFromMediaElement(module)
                if (url.isNotEmpty()) return url
            }
        }

        // 3. 从 HTML 内容中提取第一个 <img> 的 src
        val imgMatch = Regex("""<img[^>]+src=["']([^"']+)["']""").find(htmlContent)
        if (imgMatch != null) {
            return imgMatch.groupValues[1]
        }

        return ""
    }

    private fun extractFromMediaElement(element: Element): String {
        val name = element.name.lowercase()
        if (name == "content" || name == "thumbnail") {
            val url = element.getAttributeValue("url")
            if (!url.isNullOrEmpty()) return url
        }
        for (child in element.children) {
            val url = extractFromMediaElement(child)
            if (url.isNotEmpty()) return url
        }
        return ""
    }
}
