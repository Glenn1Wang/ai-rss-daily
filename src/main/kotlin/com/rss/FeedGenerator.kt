package com.rss

import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.SyndFeedOutput
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FeedGenerator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun generate(
        articles: List<AISelectedArticle>,
        feedName: String = "AI 每日技术精选",
        outputPath: String = "public/feed.xml",
        dailyBriefing: String = ""
    ) {
        val feed = SyndFeedImpl().apply {
            feedType = "rss_2.0"
            title = feedName
            link = "https://github.com"
            description = "由 AI 每日自动筛选的技术文章精选"
            publishedDate = Date()
        }

        val entries = mutableListOf<SyndEntry>()

        // 插入每日简报作为第一条
        if (dailyBriefing.isNotEmpty()) {
            entries.add(SyndEntryImpl().apply {
                title = "📋 今日技术动态概览 (${dateFormat.format(Date())})"
                link = "https://github.com"
                publishedDate = Date()
                description = SyndContentImpl().apply {
                    type = "text/html"
                    value = "<p>$dailyBriefing</p>"
                }
                categories = listOf(SyndCategoryImpl().apply { name = "简报" })
            })
        }

        // 按评分降序排列
        val sorted = articles.sortedByDescending { it.rating }

        entries.addAll(sorted.map { article ->
            SyndEntryImpl().apply {
                title = "${article.ratingStars()} [${article.category}] ${article.title}"
                link = article.originalUrl
                publishedDate = article.publishedDate ?: Date()

                // 分类标签
                categories = listOf(SyndCategoryImpl().apply { name = article.category })

                // 封面图通过 enclosure 传递给阅读器
                if (article.imageUrl.isNotEmpty()) {
                    enclosures = listOf(SyndEnclosureImpl().apply {
                        url = article.imageUrl
                        type = "image/jpeg"
                    })
                }

                description = SyndContentImpl().apply {
                    type = "text/html"
                    value = buildString {
                        if (article.imageUrl.isNotEmpty()) {
                            append("<img src=\"${article.imageUrl}\" alt=\"${article.title}\" /><br/>")
                        }
                        append("<p><strong>推荐指数：</strong>${article.ratingStars()}</p>")
                        append("<p><strong>分类：</strong>${article.category}</p>")
                        append("<p><strong>摘要：</strong>${article.summary}</p>")
                        append("<p><strong>推荐理由：</strong>${article.recommendationReason}</p>")
                        append("<p><a href=\"${article.originalUrl}\">阅读原文</a></p>")
                    }
                }
            }
        })

        feed.entries = entries

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        FileWriter(outputFile).use { writer ->
            SyndFeedOutput().output(feed, writer)
        }

        println("已生成 RSS 订阅源: $outputPath (${articles.size} 篇文章)")
    }
}
