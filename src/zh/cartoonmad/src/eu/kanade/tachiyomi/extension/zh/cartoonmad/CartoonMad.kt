package eu.kanade.tachiyomi.extension.zh.cartoonmad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class CartoonMad : ParsedHttpSource() {
    override val baseUrl = "https://www.cartoonmad.com"
    override val lang: String
        get() = "zh"
    override val name: String
        get() = "動漫狂"
    override val supportsLatest: Boolean
        get() = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .followRedirects(false)
        .addInterceptor(::handleRedirectInterceptor)
        .build()

    fun handleRedirectInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val response = chain.proceed(request)
        if (response.code == 302) {
            val location = response.header("Location")
            if (location == "http://www.cartoonmad.com/m/" || location == "$baseUrl/m/") {
                val newPath = when {
                    path == "/newcm.html" -> "/?act=1"
                    path == "/hotrank.html" -> "/?act=2"
                    path.startsWith("/comic/") -> path
                    else -> return response
                }
                return client.newCall(GET("$baseUrl/m$newPath")).execute()
            } else if (location?.contains("/post/") == true) {
                return client.newCall(GET(location)).execute()
            }
        }
        if (path.contains("/comic/")) {
            // need change response encoding charset
            return response.newBuilder()
                .body(
                    response.body.source()
                        .asResponseBody("text/html; charset=big5hkscs".toMediaType()),
                )
                .build()
        }
        return response
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/newcm.html")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val content = document.select(content_selector)
        return SManga.create().apply {
            content.select("> table:nth-child(1) > tbody").let {
                author = it.select("> tr > td:contains(作者：)").text().substringAfter("作者：").trim()
                genre = it.select("> tr > td:contains(分類：) td:has(img[src=/image/start.gif])").text()
                    .substringAfter("分類：").trim()
                thumbnail_url =
                    it.select("span.cover + img, span.covers + img").attr("abs:src").ifEmpty { thumbnail_url }
            }
            description = content.select("> table:nth-child(2) legend + table").text()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val content = doc.select(content_selector)
        val chapters = content.select("> table:nth-child(3) legend + table > tbody > tr > td > a")
        return chapters.map {
            SChapter.create().apply {
                name = it.text()
                url = it.attr("href")
            }
        }.reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        val ret = mutableListOf<Page>()
        var doc = document
        do {
            val imageUrl =
                doc.selectFirst("body > table > tbody > tr:nth-child(3) img")!!.absUrl("src")
            ret.add(Page(ret.size, imageUrl = imageUrl))
            val nextPage =
                doc.selectFirst("body > table > tbody > tr:nth-child(5) > td > a:last-child")
                    ?.absUrl("href")
                    ?: return ret
            if (nextPage.contains("thendm.asp")) {
                return ret
            }
            doc = client.newCall(GET(nextPage)).execute().asJsoup()
        } while (true)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.selectFirst("a.a1")!!.attr("href")
            title = element.selectFirst("a.a1")!!.text()
            thumbnail_url = element.selectFirst("img")!!.absUrl("src")
        }
    }

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/hotrank.html")

    override fun popularMangaSelector(): String {
        //                                                            page   row  item
        return "td[background=/image/content_box4.gif] + td > table > tbody > tr > td" +
            // For mobile
            ", div#container div.comic_prev"
    }

    override fun searchMangaFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun searchMangaNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaSelector(): String {
        TODO("Not yet implemented")
    }
}

private const val content_selector =
    // For PC
    "body > table > tbody > tr:nth-child(1) > td:nth-child(2) > table > tbody > tr:nth-child(4) > td > table > tbody > tr:nth-child(2) > td:nth-child(2)" +
        // For Mobile
        ", body > table:nth-of-type(2) > tbody > tr:nth-child(2) > td"
