package com.rss

fun main() {
    println("=== AI RSS 每日精选 ===")
    println()

    // 1. 加载配置
    val config = Config.load()
    println("已加载 ${config.feeds.size} 个 Feed 配置")
    println()

    // 2. 初始化大模型（所有 feed 共用一个）
    val model = AIAgent.createModel()
    println()

    // 3. 逐个处理每个 feed
    for (feedConfig in config.feeds) {
        println("========================================")
        println("处理 Feed: ${feedConfig.name}")
        println("输出文件: ${feedConfig.output}")
        println("目标篇数: ${feedConfig.articleCount}")
        println("RSS 源数: ${feedConfig.rssUrls.size}")
        println("========================================")
        println()

        processFeed(model, feedConfig)
        println()
    }

    // 4. 保存历史记录
    History.save()

    println("=== 全部完成 ===")
}

private fun processFeed(model: dev.langchain4j.model.chat.ChatLanguageModel, feedConfig: FeedConfig) {
    // 抓取 RSS
    val articles = RSSFetcher.fetchAll(feedConfig.rssUrls)
    if (articles.isEmpty()) {
        println("未抓取到任何文章，跳过")
        return
    }
    println()

    // 历史去重
    val newArticles = History.filterNew(articles)
    if (newArticles.isEmpty()) {
        println("所有文章都已推送过，跳过")
        return
    }
    println()

    // AI 筛选
    val selected = AIAgent.selectAndSummarize(model, newArticles, feedConfig.promptFocus, feedConfig.articleCount)
    if (selected.isEmpty()) {
        println("AI 未筛选出文章，跳过")
        return
    }

    // 回填发布日期
    val enriched = AISelectedArticle.enrichFromRaw(selected, articles)

    // 生成每日简报
    println("正在生成每日简报...")
    val briefing = AIAgent.generateDailyBriefing(model, enriched)
    if (briefing.isNotEmpty()) {
        println("每日简报: $briefing")
    }
    println()

    // 生成 RSS 输出
    FeedGenerator.generate(
        articles = enriched,
        feedName = feedConfig.name,
        outputPath = "public/${feedConfig.output}",
        dailyBriefing = briefing
    )

    // 记录已推送
    History.markPushed(enriched.map { it.originalUrl })
}
