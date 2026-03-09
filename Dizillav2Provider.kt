package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class Dizillav2Provider : MainAPI() {
    override var mainUrl = "https://dizilla.to"
    override var name = "Dizillav2"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    
    private val updateUrl = "https://gist.githubusercontent.com/Kuantaxx/bccb9d12400b708b081e102cb6a721f0/raw/e19596222d45baff90d8610b9dfcd71e3e4f9d29/dizillatxt"
    private var isUrlUpdated = false
    private suspend fun checkUpdate() {
        if (isUrlUpdated) return
        try {
            val fetchedUrl = app.get(updateUrl).text.trim()
            if (fetchedUrl.startsWith("http")) mainUrl = fetchedUrl
            isUrlUpdated = true
        } catch (e: Exception) { }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        checkUpdate()
        val document = app.get("$mainUrl/dizi-izle").document
        val home = document.select("div.new-added-list > a").mapNotNull { it.toSearchResult() }
        return newHomePageResponse("Ana Sayfa", home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        checkUpdate()
        // Not: Siteye göre arama URL'si değişebilir (?s= veya /arama/ gibi). Jeneratör varsayılan olarak /?s= ekler.
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.new-added-list > a").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.pt-3 > h2")?.text() ?: return null
        val hrefElement = if ("this" == "this") this else this.selectFirst("this")
        val href = fixUrl(hrefElement?.attr("href") ?: return null)
        val imgElement = this.selectFirst("img.lazyload")
        val posterUrl = fixUrl(imgElement?.attr("src") ?: "")

        val yearText = this.selectFirst("span.absolute.bottom-0.bg-mns > span.text-white.text-xs")?.text()
            val yearInt = yearText?.filter { it.isDigit() }?.toIntOrNull()
        val ratingText = this.selectFirst("span.absolute.bottom-0.bg-mns div > h4.text-xs.text-white.font-bold")?.text()

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.year = yearInt
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        checkUpdate()
        val document = app.get(url).document

        val title = document.selectFirst("h1, .title, .poster-title")?.text() ?: ""
        val plot = document.selectFirst(".text-white.text-base.mb-10.mt-10")?.text()?.trim()

        val episodes = ArrayList<Episode>()
        
        document.select(".season-lists > .grid > div").forEach { el ->
            val nameEl = if ("a.text.block > div:last-child" == "this") el else el.selectFirst("a.text.block > div:last-child")
            val epName = nameEl?.text()?.trim() ?: "Bölüm"
            
            val linkEl = if ("a.text.block" == "this") el else el.selectFirst("a.text.block")
            val epLink = fixUrl(linkEl?.attr("href") ?: "")

            if (epLink.isNotBlank()) {
                episodes.add(Episode(data = epLink, name = epName))
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.plot = plot
        }
    }
}
