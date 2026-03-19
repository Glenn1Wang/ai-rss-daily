package com.rss

import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.SyndFeedOutput
import java.io.File
import java.io.FileWriter
import java.util.Date
import com.rometools.rome.feed.module.DCModuleImpl

object FeedGenerator {

    fun generate(articles: List<AISelectedArticle>, outputPath: String = "public/feed.xml") {
        val feed = SyndFeedImpl().apply {
            feedType = "rss_2.0"
            title = "AI 每日技术精选"
            link = "https://github.com"
            description = "由 AI 每日自动筛选的技术文章精选"
            publishedDate = Date()
        }

        feed.entries = articles.map { article ->
            SyndEntryImpl().apply {
                title = article.title
                link = article.originalUrl
                publishedDate = article.publishedDate ?: Date()

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
                        append("<p><strong>摘要：</strong>${article.summary}</p>")
                        append("<p><strong>推荐理由：</strong>${article.recommendationReason}</p>")
                        append("<p><a href=\"${article.originalUrl}\">阅读原文</a></p>")
                    }
                }
            }
        }

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        FileWriter(outputFile).use { writer ->
            SyndFeedOutput().output(feed, writer)
        }

        println("已生成 RSS 订阅源: $outputPath (${articles.size} 篇文章)")
    }
}
