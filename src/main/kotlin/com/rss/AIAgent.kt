package com.rss

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import java.util.Date

data class AISelectedArticle(
    val title: String,
    val summary: String,
    val originalUrl: String,
    val recommendationReason: String,
    val imageUrl: String = "",
    val category: String = "",
    val rating: Int = 3,
    var publishedDate: Date? = null
) {
    companion object {
        fun enrichFromRaw(selected: List<AISelectedArticle>, rawArticles: List<RawArticle>): List<AISelectedArticle> {
            val rawByUrl = rawArticles.associateBy { it.link }
            for (article in selected) {
                val raw = rawByUrl[article.originalUrl] ?: continue
                if (raw.publishedDate.isNotEmpty()) {
                    try {
                        article.publishedDate = java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.ENGLISH)
                            .parse(raw.publishedDate)
                    } catch (_: Exception) {}
                }
            }
            return selected
        }
    }

    fun ratingStars(): String = "★".repeat(rating) + "☆".repeat((5 - rating).coerceAtLeast(0))
}

object AIAgent {

    private val gson = Gson()

    fun createModel(): ChatLanguageModel {
        val apiKey = System.getenv("OPENAI_API_KEY")
            ?: throw IllegalStateException("请设置环境变量 OPENAI_API_KEY")

        val baseUrl = System.getenv("OPENAI_BASE_URL") ?: "https://api.openai.com/v1"
        val modelName = System.getenv("AI_MODEL_NAME") ?: "gpt-4o-mini"

        println("使用模型: $modelName @ $baseUrl")

        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(0.3)
            .maxTokens(4096)
            .timeout(java.time.Duration.ofMinutes(3))
            .build()
    }

    private const val BATCH_SIZE = 50

    fun selectAndSummarize(
        model: ChatLanguageModel,
        articles: List<RawArticle>,
        focus: String,
        topN: Int
    ): List<AISelectedArticle> {
        if (articles.isEmpty()) {
            println("没有文章可供筛选")
            return emptyList()
        }

        if (articles.size <= BATCH_SIZE) {
            return callModel(model, articles, focus, topN)
        }

        println("文章数量较多(${articles.size})，分批筛选...")
        val candidates = mutableListOf<AISelectedArticle>()
        val batches = articles.chunked(BATCH_SIZE)

        for ((i, batch) in batches.withIndex()) {
            println("处理第 ${i + 1}/${batches.size} 批（${batch.size} 篇）...")
            val topFromBatch = callModel(model, batch, focus, topN)
            candidates.addAll(topFromBatch)
        }

        if (candidates.size <= topN) {
            return candidates
        }

        println("从 ${candidates.size} 篇候选中做最终精选...")
        return callModelFromCandidates(model, candidates, focus, topN)
    }

    fun generateDailyBriefing(
        model: ChatLanguageModel,
        articles: List<AISelectedArticle>
    ): String {
        if (articles.isEmpty()) return ""

        val articleList = articles.joinToString("\n") { "- [${it.category}] ${it.title}: ${it.summary}" }

        val prompt = """
            基于以下今日精选的 ${articles.size} 篇技术文章，生成一段 3-5 句话的「今日技术动态概览」。
            要求：简洁、信息密度高、用中文、像晨报一样概括今天的技术热点趋势。
            不要逐篇罗列，而是提炼共性趋势和亮点。

            今日精选文章：
            $articleList
        """.trimIndent()

        return try {
            val response = model.generate(
                listOf(
                    dev.langchain4j.data.message.SystemMessage.from("你是一个技术晨报编辑，擅长用简洁的语言概括技术趋势。"),
                    dev.langchain4j.data.message.UserMessage.from(prompt)
                )
            )
            response.content().text().trim()
        } catch (e: Exception) {
            System.err.println("生成每日简报失败: ${e.message}")
            ""
        }
    }

    private fun callModel(
        model: ChatLanguageModel,
        articles: List<RawArticle>,
        focus: String,
        topN: Int
    ): List<AISelectedArticle> {
        val newsData = articles.mapIndexed { index, article ->
            """
            |[${index + 1}]
            |标题: ${article.title}
            |来源: ${article.source}
            |链接: ${article.link}
            |封面图: ${article.imageUrl}
            |摘要: ${article.description}
            """.trimMargin()
        }.joinToString("\n---\n")

        val systemPrompt = buildPrompt(focus, topN)
        val userMessage = "今日抓取的新闻数据如下：\n$newsData"
        println("正在调用大模型筛选 ${articles.size} 篇文章...")

        return parseModelResponse(model, systemPrompt, userMessage)
    }

    private fun callModelFromCandidates(
        model: ChatLanguageModel,
        candidates: List<AISelectedArticle>,
        focus: String,
        topN: Int
    ): List<AISelectedArticle> {
        val candidateData = candidates.mapIndexed { index, article ->
            """
            |[${index + 1}]
            |标题: ${article.title}
            |链接: ${article.originalUrl}
            |封面图: ${article.imageUrl}
            |摘要: ${article.summary}
            |分类: ${article.category}
            |推荐理由: ${article.recommendationReason}
            """.trimMargin()
        }.joinToString("\n---\n")

        val systemPrompt = """
            你是一个资深技术主编。以下是从多个信息源中预筛出的候选文章。
            根据用户的偏好筛选标准：$focus

            请从中精选出最终最具价值的 $topN 篇文章。
            评估标准：技术深度、实用价值、话题热度、与用户偏好的匹配度。去重，避免内容高度相似的文章。

            你必须严格返回一个 JSON 数组，数组中每个元素包含以下字段：
            - title: 文章标题（中文）
            - summary: 50 字以内的中文总结
            - originalUrl: 原文链接
            - recommendationReason: 推荐理由（一句话，用中文）
            - imageUrl: 原文封面图链接（直接使用原始数据中的封面图链接，没有则留空字符串）
            - category: 分类标签，从以下选项中选择一个：Android、Kotlin、AI、开源、前端、后端、架构、DevOps、其他
            - rating: 推荐指数，1-5 的整数，5 为最高

            只返回 JSON 数组，不要包含任何其他文字、markdown 标记或代码块标记。
        """.trimIndent()

        val userMessage = "候选文章列表如下：\n$candidateData"
        println("正在做最终精选...")

        return parseModelResponse(model, systemPrompt, userMessage)
    }

    private fun buildPrompt(focus: String, topN: Int): String {
        return """
            你是一个资深技术主编。请仔细阅读以下抓取到的新闻列表。
            根据用户的偏好筛选标准：$focus

            从中挑选出最具价值的 $topN 篇文章（不足则全部返回）。
            评估标准：技术深度、实用价值、话题热度、与用户偏好的匹配度。

            你必须严格返回一个 JSON 数组，数组中每个元素包含以下字段：
            - title: 文章标题（如果是英文标题，翻译为中文）
            - summary: 50 字以内的中文总结（无论原文是什么语言，都必须用中文总结）
            - originalUrl: 原文链接
            - recommendationReason: 推荐理由（一句话，用中文）
            - imageUrl: 原文封面图链接（直接使用原始数据中的封面图链接，没有则留空字符串）
            - category: 分类标签，从以下选项中选择一个：Android、Kotlin、AI、开源、前端、后端、架构、DevOps、其他
            - rating: 推荐指数，1-5 的整数，5 为最高

            只返回 JSON 数组，不要包含任何其他文字、markdown 标记或代码块标记。
        """.trimIndent()
    }

    private fun parseModelResponse(
        model: ChatLanguageModel,
        systemPrompt: String,
        userMessage: String
    ): List<AISelectedArticle> {
        val response = model.generate(
            listOf(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                dev.langchain4j.data.message.UserMessage.from(userMessage)
            )
        )

        val content = response.content().text()
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val type = object : TypeToken<List<AISelectedArticle>>() {}.type
            val result: List<AISelectedArticle> = gson.fromJson(content, type)
            println("大模型精选了 ${result.size} 篇文章")
            result
        } catch (e: Exception) {
            System.err.println("解析大模型返回失败: ${e.message}")
            System.err.println("原始返回: $content")
            emptyList()
        }
    }
}
