package org.cyclingcommons.scout.help

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HelpPage(
    val title: String,
    val sections: List<HelpSection>,
)

data class HelpSection(
    val heading: String,
    val body: List<String>,
    val links: List<HelpLink> = emptyList(),
    /** Built-in image key, e.g. `instance` → instance_logo drawable. */
    val image: String? = null,
)

data class HelpLink(
    val label: String,
    val url: String,
)

/** Loads rider-facing help from bundled JSON (see Android/help/). */
object HelpContent {
    private const val HELP_ASSET = "help.json"
    private const val LEGAL_ASSET = "legal.json"

    fun load(context: Context): HelpPage? = runCatching {
        val help = loadAsset(context, HELP_ASSET)?.let(::parse) ?: return@runCatching null
        val legal = loadLegalSections(context)
        if (legal.isEmpty()) {
            help
        } else {
            mergeLegal(help, legal)
        }
    }.getOrNull()

    /** Safety / policy sections from `legal.json` (empty when only the example is bundled). */
    fun loadLegalSections(context: Context): List<HelpSection> =
        runCatching {
            loadAsset(context, LEGAL_ASSET)?.let(::parseLegalSections).orEmpty()
        }.getOrDefault(emptyList())

    private fun loadAsset(context: Context, name: String): JSONObject? =
        runCatching {
            context.assets.open(name).use { stream ->
                JSONObject(stream.readBytes().decodeToString())
            }
        }.getOrNull()

    internal fun parse(root: JSONObject): HelpPage {
        val sections = root.getJSONArray("sections").let(::parseSections)
        return HelpPage(
            title = root.getString("title"),
            sections = sections,
        )
    }

    /** `legal.json` / `legal.example.json` — sections only. */
    internal fun parseLegalSections(root: JSONObject): List<HelpSection> =
        root.getJSONArray("sections").let(::parseSections)

    internal fun mergeLegal(help: HelpPage, legal: List<HelpSection>): HelpPage {
        val out = help.sections.toMutableList()
        val sourceIdx = out.indexOfFirst { it.heading == SOURCE_CODE_HEADING }
        if (sourceIdx >= 0) {
            out.addAll(sourceIdx, legal)
        } else {
            out.addAll(legal)
        }
        return help.copy(sections = out)
    }

    private fun parseSections(array: JSONArray): List<HelpSection> =
        buildList(array.length()) {
            for (i in 0 until array.length()) {
                add(parseSection(array.getJSONObject(i)))
            }
        }

    private fun parseSection(section: JSONObject): HelpSection {
        val body = section.getJSONArray("body").let(::stringList)
        val links = if (section.has("links")) {
            parseLinks(section.getJSONArray("links"))
        } else {
            emptyList()
        }
        val image = if (section.has("image")) section.getString("image") else null
        return HelpSection(
            heading = section.getString("heading"),
            body = body,
            links = links,
            image = image,
        )
    }

    private fun parseLinks(array: JSONArray): List<HelpLink> =
        buildList(array.length()) {
            for (i in 0 until array.length()) {
                val link = array.getJSONObject(i)
                add(HelpLink(label = link.getString("label"), url = link.getString("url")))
            }
        }

    private fun stringList(array: JSONArray): List<String> =
        buildList(array.length()) {
            for (i in 0 until array.length()) {
                add(array.getString(i))
            }
        }

    private const val SOURCE_CODE_HEADING = "Source code"
}
