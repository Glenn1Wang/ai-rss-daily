package com.rss

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel

data class AISelectedArticle(
    val title: String,
    val summary: String,
    val originalUrl: String,
    val recommendationReason: String
)

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
            .build()
    }

    fun selectAndSummarize(
        model: ChatLanguageModel,
        articles: List<RawArticle>,
        focus: String
    ): List<AISelectedArticle> {
        if (articles.isEmpty()) {
            println("没有文章可供筛选")
            return emptyList()
        }

        val newsData = articles.mapIndexed { index, article ->
            """
            |[${index + 1}]
            |标题: ${article.title}
            |来源: ${article.source}
            |链接: ${article.link}
            |摘要: ${article.description}
            """.trimMargin()
        }.joinToString("\n---\n")

        val systemPrompt = """
            你是一个资深技术主编。请仔细阅读以下抓取到的新闻列表。
            根据用户的偏好筛选标准：$focus

            挑选出最具价值的文章（最多 10 篇，不足 10 篇则全部返回）。

            你必须严格返回一个 JSON 数组，数组中每个元素包含以下字段：
            - title: 文章标题（如果是英文标题，翻译为中文）
            - summary: 50 字以内的中文总结（无论原文是什么语言，都必须用中文总结）
            - originalUrl: 原文链接
            - recommendationReason: 推荐理由（一句话，用中文）

            只返回 JSON 数组，不要包含任何其他文字、markdown 标记或代码块标记。
        """.trimIndent()

        val userMessage = "今日抓取的新闻数据如下：\n$newsData"

        println("正在调用大模型筛选 ${articles.size} 篇文章...")

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
