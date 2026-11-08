package com.opportunitytracker.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL

object WebScraper {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Redmi 12 5G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    data class ScrapedPage(
        val url: String,
        val title: String,
        val textSnippet: String,
        val formLinks: List<String>
    )

    fun containsUrl(text: String): Boolean {
        return text.contains("http://") || text.contains("https://")
    }

    fun extractUrls(text: String): List<String> {
        val urlPattern = """(https?://[^\s]+)""".toRegex()
        return urlPattern.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', ';') }.toList()
    }

    suspend fun fetchUrlContent(targetUrl: String): ScrapedPage = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(targetUrl)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .followRedirects(true)
                .get()

            val title = doc.title().ifBlank {
                doc.select("meta[property=og:title]").attr("content")
            }

            val metaDesc = doc.select("meta[property=og:description]").attr("content")

            // Remove non-content elements
            doc.select("script, style, nav, footer, header, noscript, svg").remove()

            // Extract application/registration form links if present
            val formLinks = doc.select("a[href]")
                .map { it.attr("abs:href") }
                .filter { link ->
                    val lower = link.lowercase()
                    lower.contains("form") || lower.contains("apply") || lower.contains("register") ||
                            lower.contains("docs.google.com/forms") || lower.contains("airtable") ||
                            lower.contains("typeform") || lower.contains("notion.site")
                }.distinct()

            val bodyText = doc.body().text()
            val combinedSnippet = buildString {
                if (title.isNotBlank()) append("Title: ").append(title).append("\n")
                if (metaDesc.isNotBlank()) append("Summary: ").append(metaDesc).append("\n")
                append(bodyText.take(4000)) // Take first 4000 chars for LLM context
            }

            ScrapedPage(
                url = targetUrl,
                title = title,
                textSnippet = combinedSnippet,
                formLinks = formLinks
            )
        } catch (e: Exception) {
            // If connection fails, return fallback with url
            ScrapedPage(
                url = targetUrl,
                title = URL(targetUrl).host,
                textSnippet = "URL: $targetUrl",
                formLinks = listOf(targetUrl)
            )
        }
    }
}
