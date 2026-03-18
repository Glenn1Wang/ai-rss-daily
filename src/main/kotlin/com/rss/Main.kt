package com.rss

fun main() {
    println("=== AI RSS 每日精选 ===")
    println()

    // 1. 加载配置
    val config = Config.load()
    println("已加载 ${config.rssUrls.size} 个 RSS 源")
    println("筛选偏好: ${config.promptFocus}")
    println()

    // 2. 抓取 RSS
    val articles = RSSFetcher.fetchAll(config.rssUrls)
    if (articles.isEmpty()) {
        println("未抓取到任何文章，退出")
        return
    }
    println()

    // 3. AI 筛选
    val model = AIAgent.createModel()
    val selected = AIAgent.selectAndSummarize(model, articles, config.promptFocus)
    if (selected.isEmpty()) {
        println("AI 未筛选出文章，退出")
        return
    }
    println()

    // 4. 生成 RSS 输出
    FeedGenerator.generate(selected)
    println()
    println("=== 完成 ===")
}
