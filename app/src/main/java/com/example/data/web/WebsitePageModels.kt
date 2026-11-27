package com.example.data.web

data class WebNavLink(
  val label: String,
  val url: String,
  val isActive: Boolean = false,
)

data class WebSelectOption(
  val value: String,
  val label: String,
  val selected: Boolean = false,
)

data class WebFormField(
  val name: String,
  val label: String,
  val type: String = "text",
  val value: String = "",
  val placeholder: String = "",
  val isHidden: Boolean = false,
  val required: Boolean = false,
  val options: List<WebSelectOption> = emptyList(),
)

data class WebFormModel(
  val actionUrl: String,
  val method: String = "GET",
  val submitLabel: String = "Submit",
  val fields: List<WebFormField> = emptyList(),
)

data class WebMediaCard(
  val title: String,
  val url: String,
  val posterUrl: String? = null,
  val year: String? = null,
  val mediaType: String? = null,
  val rating: String? = null,
  val badge: String? = null,
  val subtitle: String? = null,
)

data class WebTorrentItem(
  val title: String,
  val infoHash: String? = null,
  val resolution: String? = null,
  val size: String? = null,
  val seeders: String? = null,
  val leechers: String? = null,
  val qualityScore: String? = null,
  val source: String? = null,
  val codecs: List<String> = emptyList(),
  val hdrTags: List<String> = emptyList(),
  val trueSpecBadge: String? = null,
  val magnetUrl: String? = null,
  val torrentDownloadUrl: String? = null,
  val detailUrl: String? = null,
)

data class WebSection(
  val title: String,
  val viewAllUrl: String? = null,
  val cards: List<WebMediaCard> = emptyList(),
  val torrents: List<WebTorrentItem> = emptyList(),
)

data class WebFilterChip(
  val label: String,
  val url: String,
  val isSelected: Boolean = false,
)

data class WebPagination(
  val currentPage: Int? = null,
  val prevUrl: String? = null,
  val nextUrl: String? = null,
  val pageLinks: List<WebNavLink> = emptyList(),
)

sealed interface WebBlock {
  data class Heading(val text: String, val level: Int = 2) : WebBlock

  data class Paragraph(val text: String) : WebBlock

  data class LinkList(val title: String?, val links: List<WebNavLink>) : WebBlock

  data class BadgeRow(val badges: List<String>) : WebBlock

  data class KeyValueList(val items: List<Pair<String, String>>) : WebBlock

  data class AlertBanner(val text: String, val isError: Boolean = false) : WebBlock

  data class CodeSnippet(val text: String) : WebBlock
}

sealed interface WebPageKind {
  data class Home(
    val heroTitle: String,
    val heroSubtitle: String,
    val quickLinks: List<WebNavLink> = emptyList(),
    val sections: List<WebSection> = emptyList(),
    val footerLinks: List<WebNavLink> = emptyList(),
  ) : WebPageKind

  data class Listing(
    val heading: String,
    val resultCountLabel: String? = null,
    val cards: List<WebMediaCard> = emptyList(),
    val torrents: List<WebTorrentItem> = emptyList(),
    val pagination: WebPagination? = null,
    val emptyMessage: String? = null,
  ) : WebPageKind

  data class Detail(
    val title: String,
    val originalTitle: String? = null,
    val year: String? = null,
    val mediaType: String? = null,
    val rating: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val metadataPairs: List<Pair<String, String>> = emptyList(),
    val seasonLinks: List<WebFilterChip> = emptyList(),
    val episodeLinks: List<WebFilterChip> = emptyList(),
    val torrentFilters: List<WebFilterChip> = emptyList(),
    val torrents: List<WebTorrentItem> = emptyList(),
    val relatedCards: List<WebMediaCard> = emptyList(),
  ) : WebPageKind

  data class Auth(
    val heading: String,
    val subtitle: String? = null,
    val form: WebFormModel? = null,
    val secondaryLinks: List<WebNavLink> = emptyList(),
    val statusBanner: String? = null,
    val isErrorBanner: Boolean = false,
    val isAccountDashboard: Boolean = false,
    val accountSummaryPairs: List<Pair<String, String>> = emptyList(),
    val logoutForm: WebFormModel? = null,
  ) : WebPageKind

  data class Generic(
    val heading: String,
    val blocks: List<WebBlock> = emptyList(),
    val forms: List<WebFormModel> = emptyList(),
    val footerLinks: List<WebNavLink> = emptyList(),
  ) : WebPageKind
}

data class ParsedWebsitePage(
  val url: String,
  val pageTitle: String,
  val brandTitle: String = "TorrentClaw",
  val headerLinks: List<WebNavLink> = emptyList(),
  val accountLink: WebNavLink? = null,
  val isSignedIn: Boolean = false,
  val searchForm: WebFormModel? = null,
  val activeFilters: List<WebFilterChip> = emptyList(),
  val sortOptions: List<WebFilterChip> = emptyList(),
  val kind: WebPageKind,
)

data class MagnetTarget(
  val title: String,
  val magnetUrl: String? = null,
  val torrentDownloadUrl: String? = null,
  val infoHash: String? = null,
  val resolution: String? = null,
  val size: String? = null,
  val seeders: String? = null,
  val source: String? = null,
) {
  val primaryCopyUrl: String
    get() = magnetUrl ?: torrentDownloadUrl.orEmpty()
}
