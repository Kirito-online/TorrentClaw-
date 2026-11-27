package com.example.data.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Deterministic HTML-to-Compose parser for torrentclaw.com web pages.
 * Extracts header navigation, search/auth forms, media cards, torrent rows, magnet/.torrent links,
 * embedded JSON-LD/hydration data, and semantic fallback blocks without calling REST APIs.
 */
object HtmlPageParser {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private val resolutionRegex = Regex("\\b(2160p|4K|UHD|1440p|1080p|720p|480p|360p)\\b", RegexOption.IGNORE_CASE)
  private val sizeRegex = Regex("\\b(\\d+(?:\\.\\d+)?\\s*(?:TB|TiB|GB|GiB|MB|MiB|KB|KiB))\\b", RegexOption.IGNORE_CASE)
  private val hashRegex = Regex("\\b([a-fA-F0-9]{40}|[A-Z2-7]{32})\\b")
  private val codecRegex = Regex("\\b(x265|HEVC|H\\.?265|x264|H\\.?264|AV1|VP9|TrueHD|Atmos|DTS-HD|DTS|EAC3|AC3|AAC|FLAC|Remux|BluRay|WEB-DL|WEBRip|HDRip)\\b", RegexOption.IGNORE_CASE)
  private val hdrRegex = Regex("\\b(Dolby\\s*Vision|DoVi|DV|HDR10\\+|HDR10|HDR|HLG)\\b", RegexOption.IGNORE_CASE)
  private val scoreRegex = Regex("(?:score|quality|q)[:\\s]*([0-9]{1,3})(?:/100)?", RegexOption.IGNORE_CASE)
  private val yearRegex = Regex("\\b(19\\d{2}|20[0-3]\\d)\\b")
  private val ratingRegex = Regex("\\b([0-9]\\.[0-9])\\s*(?:/\\s*10|★)?")

  fun needsClientDomSnapshot(html: String): Boolean {
    if (html.isBlank()) return true
    val doc = Jsoup.parse(html)
    doc.select("script, style, noscript, svg").remove()
    val visibleTextLen = doc.body().text().trim().length
    val hasRichElements =
      doc.select("a[href^=magnet:], table tr, article, form input, [class*=card], [class*=torrent]").isNotEmpty()
    val hasSpaRoot =
      html.contains("id=\"root\"", ignoreCase = true) ||
        html.contains("id=\"__next\"", ignoreCase = true) ||
        html.contains("id=\"app\"", ignoreCase = true)
    return hasSpaRoot && visibleTextLen < 120 && !hasRichElements
  }

  fun parse(url: String, html: String): ParsedWebsitePage {
    val doc = Jsoup.parse(html, url)
    val pageTitle =
      doc.title().trim().ifBlank {
        doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty().ifBlank { "TorrentClaw" }
      }

    val ldObjects = extractJsonLdObjects(doc)
    val headerLinks = extractHeaderLinks(doc, url)
    val accountLink =
      headerLinks.firstOrNull {
        it.label.contains("sign in", true) ||
          it.label.contains("login", true) ||
          it.label.contains("account", true) ||
          it.label.contains("profile", true) ||
          it.url.contains("/login", true) ||
          it.url.contains("/account", true)
      }
    val isSignedIn = detectSignedIn(doc, headerLinks)
    val searchForm = extractSearchForm(doc, url)
    val filters = extractFilterChips(doc, url, listOf("filter", "quality", "type", "genre", "resolution", "tab"))
    val sortOptions = extractFilterChips(doc, url, listOf("sort", "order"))
    val footerLinks = extractFooterLinks(doc, url)

    val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase().orEmpty()
    val kind: WebPageKind =
      when {
        isAuthPage(path, doc) -> parseAuthPage(url, doc, footerLinks)
        isDetailPage(path, doc, ldObjects) -> parseDetailPage(url, doc, ldObjects, filters)
        isHomePage(path, doc) -> parseHomePage(url, doc, headerLinks, footerLinks)
        isListingPage(path, doc) -> parseListingPage(url, doc)
        else -> parseGenericPage(url, doc, footerLinks)
      }

    return ParsedWebsitePage(
      url = url,
      pageTitle = pageTitle,
      brandTitle = "TorrentClaw",
      headerLinks = headerLinks,
      accountLink = accountLink,
      isSignedIn = isSignedIn,
      searchForm = searchForm,
      activeFilters = filters,
      sortOptions = sortOptions,
      kind = kind,
    )
  }

  private fun extractHeaderLinks(doc: Document, baseUrl: String): List<WebNavLink> {
    val navContainers = doc.select("header nav a[href], header a[href], nav a[href]")
    val seen = linkedSetOf<String>()
    val result = mutableListOf<WebNavLink>()
    for (a in navContainers) {
      val label = a.text().trim()
      if (label.isBlank() || label.length > 36) continue
      val resolved = a.absUrl("href").ifBlank { a.attr("href") }
      val decision = NavigationGuard.classify(resolved, baseUrl)
      if (decision is UrlDecision.InternalPage && seen.add(decision.normalizedUrl)) {
        val isActive =
          decision.normalizedUrl == baseUrl ||
            a.attr("aria-current").equals("page", ignoreCase = true) ||
            a.classNames().any { it.contains("active", ignoreCase = true) }
        result.add(WebNavLink(label = label, url = decision.normalizedUrl, isActive = isActive))
      }
      if (result.size >= 10) break
    }
    if (result.isEmpty()) {
      result.addAll(
        listOf(
          WebNavLink("Home", "${NavigationGuard.BASE_ORIGIN}/", baseUrl == NavigationGuard.HOME_URL),
          WebNavLink("Movies", "${NavigationGuard.BASE_ORIGIN}/search?type=movie", baseUrl.contains("type=movie")),
          WebNavLink("TV Shows", "${NavigationGuard.BASE_ORIGIN}/search?type=tv", baseUrl.contains("type=tv")),
          WebNavLink("Browse", "${NavigationGuard.BASE_ORIGIN}/browse", baseUrl.contains("/browse")),
          WebNavLink("Login", "${NavigationGuard.BASE_ORIGIN}/login", baseUrl.contains("/login")),
        )
      )
    }
    return result
  }

  private fun extractFooterLinks(doc: Document, baseUrl: String): List<WebNavLink> {
    val links = doc.select("footer a[href]")
    val seen = linkedSetOf<String>()
    val out = mutableListOf<WebNavLink>()
    for (a in links) {
      val label = a.text().trim()
      if (label.isBlank() || label.length > 40) continue
      val resolved = a.absUrl("href").ifBlank { a.attr("href") }
      val decision = NavigationGuard.classify(resolved, baseUrl)
      if (decision is UrlDecision.InternalPage && seen.add(decision.normalizedUrl)) {
        out.add(WebNavLink(label = label, url = decision.normalizedUrl))
      }
      if (out.size >= 12) break
    }
    return out
  }

  private fun detectSignedIn(doc: Document, headerLinks: List<WebNavLink>): Boolean {
    if (headerLinks.any { it.label.contains("logout", true) || it.label.contains("sign out", true) }) {
      return true
    }
    if (doc.select("form[action*=logout], button:contains(Sign out), button:contains(Log out)").isNotEmpty()) {
      return true
    }
    val hasAccount = headerLinks.any { it.label.contains("account", true) || it.label.contains("profile", true) }
    val hasLogin = headerLinks.any { it.label.contains("sign in", true) || it.label.contains("login", true) }
    return hasAccount && !hasLogin
  }

  private fun extractSearchForm(doc: Document, baseUrl: String): WebFormModel {
    val form =
      doc.select("form").firstOrNull { f ->
        f.select("input[type=search], input[name=q], input[name=query], input[name=search]").isNotEmpty()
      }
    if (form != null) {
      return parseFormElement(form, baseUrl, defaultAction = "${NavigationGuard.BASE_ORIGIN}/search", defaultSubmit = "Search")
    }
    return WebFormModel(
      actionUrl = "${NavigationGuard.BASE_ORIGIN}/search",
      method = "GET",
      submitLabel = "Search",
      fields =
        listOf(
          WebFormField(
            name = "q",
            label = "Search",
            type = "search",
            value = baseUrl.toHttpUrlOrNull()?.queryParameter("q").orEmpty(),
            placeholder = "Search movies, TV shows, IMDb/TMDB URLs…",
          )
        ),
    )
  }

  private fun extractFilterChips(doc: Document, baseUrl: String, classHints: List<String>): List<WebFilterChip> {
    val out = mutableListOf<WebFilterChip>()
    val seen = linkedSetOf<String>()
    val selector = classHints.joinToString(", ") { hint -> "[class*=$hint] a[href], [data-$hint] a[href]" }
    for (a in doc.select(selector)) {
      val label = a.text().trim()
      if (label.isBlank() || label.length > 32) continue
      val resolved = a.absUrl("href").ifBlank { a.attr("href") }
      val decision = NavigationGuard.classify(resolved, baseUrl)
      if (decision is UrlDecision.InternalPage && seen.add(decision.normalizedUrl)) {
        val selected =
          decision.normalizedUrl == baseUrl ||
            a.classNames().any { it.contains("active", true) || it.contains("selected", true) } ||
            a.attr("aria-pressed").equals("true", true)
        out.add(WebFilterChip(label = label, url = decision.normalizedUrl, isSelected = selected))
      }
      if (out.size >= 14) break
    }
    return out
  }

  private fun isAuthPage(path: String, doc: Document): Boolean {
    if (path.contains("/login") || path.contains("/signin") || path.contains("/register") ||
      path.contains("/signup") || path.contains("/account") || path.contains("/profile")
    ) {
      return true
    }
    return doc.select("main form input[type=password], form[action*=login], form[action*=signin], form[action*=register]").isNotEmpty()
  }

  private fun isDetailPage(path: String, doc: Document, ldObjects: List<JsonObject>): Boolean {
    if (path.startsWith("/movie/") || path.startsWith("/tv/") || path.startsWith("/show/") ||
      path.startsWith("/title/") || path.startsWith("/content/")
    ) {
      return true
    }
    val hasMediaLd =
      ldObjects.any { obj ->
        val type = obj["@type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        type.equals("Movie", true) || type.equals("TVSeries", true)
      }
    if (hasMediaLd) return true
    val hasMagnets = doc.select("a[href^=magnet:]").isNotEmpty()
    val hasOverview = doc.select("[class*=overview], [class*=synopsis], [class*=description], main p").isNotEmpty()
    val hasSingleHero = doc.select("h1").size == 1 && doc.select("img[src]").isNotEmpty()
    return hasMagnets && hasOverview && hasSingleHero && !path.contains("search")
  }

  private fun isHomePage(path: String, doc: Document): Boolean {
    if (path.isEmpty() || path == "/") return true
    return doc.select("section").size >= 2 && !path.contains("search")
  }

  private fun isListingPage(path: String, doc: Document): Boolean {
    if (path.contains("search") || path.contains("browse") || path.contains("movies") ||
      path.contains("shows") || path.contains("trending") || path.contains("popular") || path.contains("recent")
    ) {
      return true
    }
    return extractMediaCards(doc, NavigationGuard.HOME_URL).isNotEmpty() ||
      extractTorrents(doc, NavigationGuard.HOME_URL).isNotEmpty()
  }

  private fun parseHomePage(
    url: String,
    doc: Document,
    headerLinks: List<WebNavLink>,
    footerLinks: List<WebNavLink>,
  ): WebPageKind.Home {
    val heroTitle =
      doc.selectFirst("main h1, [class*=hero] h1, h1")?.text()?.trim().orEmpty().ifBlank { "TorrentClaw" }
    val heroSubtitle =
      doc.selectFirst("[class*=hero] p, main > p, meta[name=description]")?.let { el ->
        if (el.tagName() == "meta") el.attr("content") else el.text()
      }?.trim().orEmpty().ifBlank {
        "Verified multi-source movie & TV torrent index with TrueSpec quality scoring."
      }

    val sections = mutableListOf<WebSection>()
    for (sectionEl in doc.select("main section, section")) {
      val title = sectionEl.selectFirst("h2, h3, [class*=section-title]")?.text()?.trim().orEmpty()
      val viewAll =
        sectionEl.selectFirst("a:contains(View all), a:contains(See all), a:contains(More)")?.let { a ->
          val res = a.absUrl("href").ifBlank { a.attr("href") }
          (NavigationGuard.classify(res, url) as? UrlDecision.InternalPage)?.normalizedUrl
        }
      val cards = extractMediaCards(sectionEl, url)
      val torrents = extractTorrents(sectionEl, url)
      if (cards.isNotEmpty() || torrents.isNotEmpty()) {
        sections.add(
          WebSection(
            title = title.ifBlank { if (cards.isNotEmpty()) "Featured" else "Recent Releases" },
            viewAllUrl = viewAll,
            cards = cards,
            torrents = torrents,
          )
        )
      }
    }

    if (sections.isEmpty()) {
      val allCards = extractMediaCards(doc.body(), url)
      val allTorrents = extractTorrents(doc.body(), url)
      if (allCards.isNotEmpty()) {
        sections.add(WebSection("Featured Titles", cards = allCards))
      }
      if (allTorrents.isNotEmpty()) {
        sections.add(WebSection("Indexed Releases", torrents = allTorrents))
      }
    }

    val quickLinks =
      headerLinks.filter {
        !it.label.equals("Home", true) && !it.label.contains("login", true) && !it.label.contains("sign in", true)
      }

    return WebPageKind.Home(
      heroTitle = heroTitle,
      heroSubtitle = heroSubtitle,
      quickLinks = quickLinks,
      sections = sections,
      footerLinks = footerLinks,
    )
  }

  private fun parseListingPage(url: String, doc: Document): WebPageKind.Listing {
    val heading =
      doc.selectFirst("main h1, h1, h2")?.text()?.trim().orEmpty().ifBlank { "Browse Results" }
    val countLabel =
      doc.selectFirst("[class*=count], [class*=result], [class*=total]")?.text()?.trim()
    val cards = extractMediaCards(doc.body(), url)
    val torrents = extractTorrents(doc.body(), url)
    val pagination = extractPagination(doc, url)
    val emptyMsg =
      if (cards.isEmpty() && torrents.isEmpty()) {
        doc.selectFirst("[class*=empty], main p")?.text()?.trim().orEmpty()
          .ifBlank { "No matching results found on this page." }
      } else {
        null
      }
    return WebPageKind.Listing(
      heading = heading,
      resultCountLabel = countLabel,
      cards = cards,
      torrents = torrents,
      pagination = pagination,
      emptyMessage = emptyMsg,
    )
  }

  private fun parseDetailPage(
    url: String,
    doc: Document,
    ldObjects: List<JsonObject>,
    filters: List<WebFilterChip>,
  ): WebPageKind.Detail {
    val ld = ldObjects.firstOrNull {
      val t = it["@type"]?.jsonPrimitive?.contentOrNull.orEmpty()
      t.equals("Movie", true) || t.equals("TVSeries", true)
    }
    val rawTitle =
      doc.selectFirst("main h1, h1")?.text()?.trim()
        ?: ld?.get("name")?.jsonPrimitive?.contentOrNull
        ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        ?: "Title Details"

    val heroText = doc.selectFirst("[class*=hero], main header, main")?.text().orEmpty()
    val year =
      yearRegex.find(rawTitle)?.groupValues?.get(1)
        ?: ld?.get("datePublished")?.jsonPrimitive?.contentOrNull?.take(4)
        ?: yearRegex.find(heroText.take(300))?.groupValues?.get(1)
    val cleanTitle = rawTitle.replace(Regex("\\s*\\((?:19|20)\\d{2}\\)\\s*$"), "").trim()

    val posterUrl =
      doc.selectFirst("[class*=poster] img, main img[src], meta[property=og:image]")?.let { el ->
        if (el.tagName() == "meta") el.attr("content") else el.absUrl("src").ifBlank { el.attr("src") }
      }?.takeIf { it.startsWith("http") }
        ?: ld?.get("image")?.jsonPrimitive?.contentOrNull

    val backdropUrl =
      doc.selectFirst("[class*=backdrop] img")?.let { it.absUrl("src").ifBlank { it.attr("src") } }
        ?.takeIf { it.startsWith("http") }

    val overview =
      doc.selectFirst("[class*=overview], [class*=synopsis], [class*=description], main p")?.text()?.trim()
        ?: ld?.get("description")?.jsonPrimitive?.contentOrNull

    val rating =
      ratingRegex.find(heroText.take(400))?.groupValues?.get(1)
        ?: ld?.get("aggregateRating")?.jsonObject?.get("ratingValue")?.jsonPrimitive?.contentOrNull

    val mediaType =
      when {
        url.contains("/tv/", true) || url.contains("/show/", true) ||
          ld?.get("@type")?.jsonPrimitive?.contentOrNull.equals("TVSeries", true) -> "TV Series"
        else -> "Movie"
      }

    val genres =
      doc.select("[class*=genre] a, [class*=genre] span, a[href*=genre=]").map { it.text().trim() }
        .filter { it.isNotBlank() && it.length <= 24 }
        .distinct()
        .ifEmpty {
          ld?.get("genre")?.let { g ->
            if (g is JsonArray) g.mapNotNull { it.jsonPrimitive.contentOrNull } else listOfNotNull(g.jsonPrimitive.contentOrNull)
          }.orEmpty()
        }

    val seasonLinks = extractFilterChips(doc, url, listOf("season"))
    val episodeLinks = extractFilterChips(doc, url, listOf("episode"))
    val torrents = extractTorrents(doc.body(), url, fallbackTitle = cleanTitle)

    val relatedCards =
      extractMediaCards(
        doc.selectFirst("[class*=related], [class*=similar], [class*=recommend]") ?: Element("div"),
        url,
      )

    val metadataPairs = mutableListOf<Pair<String, String>>()
    year?.let { metadataPairs.add("Year" to it) }
    rating?.let { metadataPairs.add("Rating" to "$it / 10") }
    metadataPairs.add("Type" to mediaType)
    if (torrents.isNotEmpty()) metadataPairs.add("Releases" to "${torrents.size} indexed")

    return WebPageKind.Detail(
      title = cleanTitle,
      year = year,
      mediaType = mediaType,
      rating = rating,
      genres = genres,
      posterUrl = posterUrl,
      backdropUrl = backdropUrl,
      overview = overview,
      metadataPairs = metadataPairs,
      seasonLinks = seasonLinks,
      episodeLinks = episodeLinks,
      torrentFilters = filters,
      torrents = torrents,
      relatedCards = relatedCards,
    )
  }

  private fun parseAuthPage(url: String, doc: Document, footerLinks: List<WebNavLink>): WebPageKind.Auth {
    val heading =
      doc.selectFirst("main h1, h1, h2")?.text()?.trim().orEmpty().ifBlank { "Account" }
    val subtitle = doc.selectFirst("main p, [class*=subtitle]")?.text()?.trim()
    val statusBanner =
      doc.selectFirst("[role=alert], [class*=alert], [class*=error], [class*=flash], [class*=notice]")
        ?.text()?.trim()?.takeIf { it.isNotBlank() }
    val isErrorBanner =
      doc.selectFirst("[class*=error], [class*=danger], [aria-invalid=true]") != null

    val forms =
      doc.select("main form, form").filter { f ->
        f.select("input[type=search], input[name=q]").isEmpty()
      }
    val logoutForm =
      forms.firstOrNull { f ->
        f.attr("action").contains("logout", true) ||
          f.text().contains("sign out", true) ||
          f.text().contains("log out", true)
      }?.let { parseFormElement(it, url, defaultAction = url, defaultSubmit = "Sign Out") }

    val primaryForm =
      forms.firstOrNull { f -> f.attr("action").contains("logout", true).not() }
        ?.let { parseFormElement(it, url, defaultAction = url, defaultSubmit = "Continue") }

    val secondaryLinks =
      doc.select("main a[href]").mapNotNull { a ->
        val label = a.text().trim()
        if (label.isBlank() || label.length > 48) return@mapNotNull null
        val resolved = a.absUrl("href").ifBlank { a.attr("href") }
        val decision = NavigationGuard.classify(resolved, url)
        (decision as? UrlDecision.InternalPage)?.let { WebNavLink(label, it.normalizedUrl) }
      }.distinctBy { it.url }.take(6)

    val summaryPairs = mutableListOf<Pair<String, String>>()
    for (row in doc.select("dl, [class*=account] li, table tr")) {
      val cells = row.select("dt, dd, th, td, strong, span")
      if (cells.size >= 2) {
        val k = cells[0].text().trim()
        val v = cells[1].text().trim()
        if (k.isNotBlank() && v.isNotBlank() && k != v && k.length <= 32) {
          summaryPairs.add(k to v)
        }
      }
    }

    val isAccountDashboard =
      (url.contains("/account", true) || url.contains("/profile", true) || logoutForm != null) &&
        primaryForm == null

    return WebPageKind.Auth(
      heading = heading,
      subtitle = subtitle,
      form = primaryForm,
      secondaryLinks = secondaryLinks.ifEmpty { footerLinks.take(4) },
      statusBanner = statusBanner,
      isErrorBanner = isErrorBanner,
      isAccountDashboard = isAccountDashboard,
      accountSummaryPairs = summaryPairs,
      logoutForm = logoutForm,
    )
  }

  private fun parseGenericPage(url: String, doc: Document, footerLinks: List<WebNavLink>): WebPageKind.Generic {
    val root = doc.selectFirst("main") ?: doc.body()
    val heading =
      root.selectFirst("h1")?.text()?.trim().orEmpty().ifBlank { doc.title().ifBlank { "TorrentClaw" } }
    val blocks = mutableListOf<WebBlock>()

    for (el in root.select("h2, h3, p, pre, [role=alert], ul, ol")) {
      when (el.tagName().lowercase()) {
        "h2", "h3" -> {
          val t = el.text().trim()
          if (t.isNotBlank()) blocks.add(WebBlock.Heading(t, if (el.tagName() == "h2") 2 else 3))
        }
        "p" -> {
          if (el.parents().any { it.tagName() == "header" || it.tagName() == "footer" }) continue
          val t = el.text().trim()
          if (t.isNotBlank()) blocks.add(WebBlock.Paragraph(t))
        }
        "pre" -> {
          val t = el.text().trim()
          if (t.isNotBlank()) blocks.add(WebBlock.CodeSnippet(t))
        }
        "ul", "ol" -> {
          if (el.parents().any { it.tagName() == "header" || it.tagName() == "nav" || it.tagName() == "footer" }) continue
          val links =
            el.select("a[href]").mapNotNull { a ->
              val label = a.text().trim()
              val resolved = a.absUrl("href").ifBlank { a.attr("href") }
              (NavigationGuard.classify(resolved, url) as? UrlDecision.InternalPage)?.let {
                WebNavLink(label.ifBlank { it.normalizedUrl }, it.normalizedUrl)
              }
            }
          if (links.isNotEmpty()) {
            blocks.add(WebBlock.LinkList(null, links.take(15)))
          } else {
            val items = el.select("> li").map { it.text().trim() }.filter { it.isNotBlank() }
            if (items.isNotEmpty()) {
              blocks.add(WebBlock.Paragraph(items.joinToString("\n") { "• $it" }))
            }
          }
        }
        else -> {
          val t = el.text().trim()
          if (t.isNotBlank()) blocks.add(WebBlock.AlertBanner(t, el.classNames().any { it.contains("error", true) }))
        }
      }
      if (blocks.size >= 40) break
    }

    val forms =
      root.select("form")
        .filter { it.select("input[type=search], input[name=q]").isEmpty() }
        .map { parseFormElement(it, url, defaultAction = url, defaultSubmit = "Submit") }

    return WebPageKind.Generic(
      heading = heading,
      blocks = blocks,
      forms = forms,
      footerLinks = footerLinks,
    )
  }

  private fun parseFormElement(
    form: Element,
    baseUrl: String,
    defaultAction: String,
    defaultSubmit: String,
  ): WebFormModel {
    val rawAction = form.absUrl("action").ifBlank { form.attr("action").ifBlank { defaultAction } }
    val actionUrl =
      (NavigationGuard.classify(rawAction, baseUrl) as? UrlDecision.InternalPage)?.normalizedUrl
        ?: defaultAction
    val method = form.attr("method").trim().uppercase().ifBlank { "GET" }
    val submitLabel =
      form.selectFirst("button[type=submit], input[type=submit], button")
        ?.let { btn -> if (btn.tagName() == "input") btn.attr("value") else btn.text() }
        ?.trim()
        ?.ifBlank { defaultSubmit }
        ?: defaultSubmit

    val fields = mutableListOf<WebFormField>()
    for (input in form.select("input, select, textarea")) {
      val name = input.attr("name").trim()
      val type =
        when (input.tagName().lowercase()) {
          "select" -> "select"
          "textarea" -> "textarea"
          else -> input.attr("type").trim().lowercase().ifBlank { "text" }
        }
      if (type == "submit" || type == "button" || type == "reset") continue
      if (name.isBlank() && type != "search") continue
      val id = input.attr("id")
      val explicitLabel =
        if (id.isNotBlank()) form.selectFirst("label[for=$id]")?.text()?.trim() else null
      val label =
        explicitLabel
          ?: input.attr("aria-label").trim().ifBlank {
            input.attr("placeholder").trim().ifBlank { name.replace('_', ' ').replaceFirstChar { it.uppercase() } }
          }
      val options =
        if (type == "select") {
          input.select("option").map { opt ->
            WebSelectOption(
              value = opt.attr("value").ifBlank { opt.text().trim() },
              label = opt.text().trim(),
              selected = opt.hasAttr("selected"),
            )
          }
        } else {
          emptyList()
        }
      val value =
        if (type == "select") {
          options.firstOrNull { it.selected }?.value ?: options.firstOrNull()?.value.orEmpty()
        } else if (input.tagName().equals("textarea", true)) {
          input.text()
        } else {
          input.attr("value")
        }
      fields.add(
        WebFormField(
          name = name.ifBlank { "q" },
          label = label,
          type = type,
          value = value,
          placeholder = input.attr("placeholder"),
          isHidden = type == "hidden",
          required = input.hasAttr("required"),
          options = options,
        )
      )
    }

    return WebFormModel(
      actionUrl = actionUrl,
      method = method,
      submitLabel = submitLabel,
      fields = fields,
    )
  }

  private fun extractMediaCards(container: Element, baseUrl: String): List<WebMediaCard> {
    val candidates =
      container.select(
        "article, [class*=card], [class*=movie], [class*=show], [class*=media-item], a[href*=/movie/], a[href*=/tv/], a[href*=/title/], a[href*=/content/]",
      )
    val seenUrls = linkedSetOf<String>()
    val cards = mutableListOf<WebMediaCard>()

    for (el in candidates) {
      if (el.select("a[href^=magnet:]").isNotEmpty() && el.tagName() == "tr") continue
      val anchor = if (el.tagName() == "a") el else el.selectFirst("a[href]") ?: continue
      val rawHref = anchor.absUrl("href").ifBlank { anchor.attr("href") }
      val decision = NavigationGuard.classify(rawHref, baseUrl)
      if (decision !is UrlDecision.InternalPage) continue
      val targetUrl = decision.normalizedUrl
      if (!seenUrls.add(targetUrl)) continue

      // Skip navigation/filter links that aren't content items
      val path = targetUrl.toHttpUrlOrNull()?.encodedPath.orEmpty().lowercase()
      val looksLikeMediaLink =
        path.contains("/movie") || path.contains("/tv") || path.contains("/show") ||
          path.contains("/title") || path.contains("/content") ||
          el.selectFirst("img") != null
      if (!looksLikeMediaLink) continue

      val titleEl = el.selectFirst("h2, h3, h4, [class*=title], [class*=name]") ?: anchor
      val title =
        titleEl.text().trim()
          .ifBlank { anchor.attr("title").trim() }
          .ifBlank { el.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
      if (title.isBlank()) continue

      val poster =
        el.selectFirst("img")?.let { img ->
          img.absUrl("src").ifBlank { img.absUrl("data-src").ifBlank { img.attr("src") } }
        }?.takeIf { it.startsWith("http") }

      val fullText = el.text()
      val year = yearRegex.find(fullText)?.groupValues?.get(1)
      val rating = ratingRegex.find(fullText)?.groupValues?.get(1)
      val type =
        when {
          path.contains("/tv") || path.contains("/show") || fullText.contains("TV", false) -> "TV"
          else -> "Movie"
        }

      cards.add(
        WebMediaCard(
          title = title,
          url = targetUrl,
          posterUrl = poster,
          year = year,
          mediaType = type,
          rating = rating,
        )
      )
      if (cards.size >= 40) break
    }
    return cards
  }

  private fun extractTorrents(
    container: Element,
    baseUrl: String,
    fallbackTitle: String? = null,
  ): List<WebTorrentItem> {
    val rows = linkedSetOf<Element>()
    for (magnetLink in container.select("a[href^=magnet:]")) {
      val row =
        magnetLink.closest("tr, li, article, [class*=torrent], [class*=release], [class*=row], [class*=item], div")
          ?: magnetLink.parent()
      if (row != null) rows.add(row)
    }
    for (torrentEl in container.select("tr:has(a[href*=.torrent]), [class*=torrent-row], [class*=release-row]")) {
      rows.add(torrentEl)
    }

    val seenKeys = linkedSetOf<String>()
    val items = mutableListOf<WebTorrentItem>()

    for (row in rows) {
      val magnetUrl =
        row.selectFirst("a[href^=magnet:]")?.attr("href")?.trim()?.takeIf { it.isNotBlank() }
      val torrentFileUrl =
        row.select("a[href]").firstNotNullOfOrNull { a ->
          val resolved = a.absUrl("href").ifBlank { a.attr("href") }
          (NavigationGuard.classify(resolved, baseUrl) as? UrlDecision.TorrentFileAction)?.torrentUrl
        }
      if (magnetUrl == null && torrentFileUrl == null) continue

      val rowText = row.text()
      val hash =
        magnetUrl?.let { Regex("btih:([a-zA-Z0-9]{32,40})", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
          ?: hashRegex.find(rowText)?.groupValues?.get(1)

      val dedupeKey = (hash?.lowercase() ?: magnetUrl ?: torrentFileUrl)!!
      if (!seenKeys.add(dedupeKey)) continue

      val resolution = resolutionRegex.find(rowText)?.groupValues?.get(1)?.uppercase()
        ?.let { if (it == "4K" || it == "UHD") "2160p" else it.lowercase() }
      val size = sizeRegex.find(rowText)?.groupValues?.get(1)
      val score = scoreRegex.find(rowText)?.groupValues?.get(1)
      val codecs = codecRegex.findAll(rowText).map { it.value }.distinct().toList()
      val hdrTags = hdrRegex.findAll(rowText).map { it.value }.distinct().toList()
      val trueSpec =
        if (rowText.contains("verified", true) || rowText.contains("truespec", true)) {
          "TrueSpec Verified"
        } else {
          null
        }

      // Extract seeders/leechers cleanly from dedicated cells or arrows
      val seedMatch = Regex("(?:seed(?:er)?s?[:\\s]*|▲\\s*|↑\\s*)(\\d+)", RegexOption.IGNORE_CASE).find(rowText)
      val leechMatch = Regex("(?:leech(?:er)?s?[:\\s]*|▼\\s*|↓\\s*)(\\d+)", RegexOption.IGNORE_CASE).find(rowText)

      val magnetDn = magnetUrl?.let { NavigationGuard.extractMagnetDisplayName(it) }
      val domName =
        row.selectFirst("[class*=title], [class*=name], td:first-child, strong, h3, h4")?.text()?.trim()
      val candidateName =
        listOfNotNull(domName, magnetDn).firstOrNull { candidate ->
          candidate.isNotBlank() &&
            !candidate.equals(hash, ignoreCase = true) &&
            !candidate.equals("magnet", ignoreCase = true) &&
            !candidate.equals("download", ignoreCase = true)
        }
      val synthesized =
        candidateName
          ?: listOfNotNull(fallbackTitle, resolution?.uppercase(), codecs.firstOrNull())
            .joinToString(" • ")
            .ifBlank { hash ?: "Torrent Release" }

      val detailUrl =
        row.select("a[href]").firstNotNullOfOrNull { a ->
          val resolved = a.absUrl("href").ifBlank { a.attr("href") }
          val d = NavigationGuard.classify(resolved, baseUrl)
          if (d is UrlDecision.InternalPage && d.normalizedUrl != baseUrl) d.normalizedUrl else null
        }

      val trackerSource =
        row.selectFirst("[class*=source], [class*=tracker]")?.text()?.trim()?.takeIf { it.isNotBlank() }

      items.add(
        WebTorrentItem(
          title = synthesized,
          infoHash = hash,
          resolution = resolution,
          size = size,
          seeders = seedMatch?.groupValues?.get(1),
          leechers = leechMatch?.groupValues?.get(1),
          qualityScore = score,
          source = trackerSource,
          codecs = codecs,
          hdrTags = hdrTags,
          trueSpecBadge = trueSpec,
          magnetUrl = magnetUrl,
          torrentDownloadUrl = torrentFileUrl,
          detailUrl = detailUrl,
        )
      )
      if (items.size >= 150) break
    }
    return items
  }

  private fun extractPagination(doc: Document, baseUrl: String): WebPagination? {
    val pagEl = doc.selectFirst("[class*=pagination], nav[aria-label*=pagination i]") ?: return null
    val links = mutableListOf<WebNavLink>()
    var prevUrl: String? = null
    var nextUrl: String? = null
    for (a in pagEl.select("a[href]")) {
      val label = a.text().trim()
      val resolved = a.absUrl("href").ifBlank { a.attr("href") }
      val internal = (NavigationGuard.classify(resolved, baseUrl) as? UrlDecision.InternalPage)?.normalizedUrl ?: continue
      val lower = label.lowercase()
      when {
        lower.contains("prev") || lower == "‹" || lower == "«" -> prevUrl = internal
        lower.contains("next") || lower == "›" || lower == "»" -> nextUrl = internal
        label.toIntOrNull() != null -> links.add(WebNavLink(label, internal, internal == baseUrl))
      }
    }
    if (prevUrl == null && nextUrl == null && links.isEmpty()) return null
    val currentPage = baseUrl.toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull()
    return WebPagination(currentPage = currentPage, prevUrl = prevUrl, nextUrl = nextUrl, pageLinks = links)
  }

  private fun extractJsonLdObjects(doc: Document): List<JsonObject> {
    val out = mutableListOf<JsonObject>()
    for (script in doc.select("script[type=application/ld+json]")) {
      val raw = script.data().trim()
      if (raw.isBlank()) continue
      runCatching {
        val elem: JsonElement = json.parseToJsonElement(raw)
        when (elem) {
          is JsonObject -> out.add(elem)
          is JsonArray -> elem.forEach { if (it is JsonObject) out.add(it) }
          else -> Unit
        }
      }
    }
    return out
  }
}
