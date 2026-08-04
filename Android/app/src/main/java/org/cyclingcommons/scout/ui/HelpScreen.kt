package org.cyclingcommons.scout.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.help.HelpContent
import org.cyclingcommons.scout.help.HelpLink
import org.cyclingcommons.scout.help.HelpSection
import org.cyclingcommons.scout.ui.components.ScoutPage
import org.cyclingcommons.scout.ui.components.ScoutSection
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutSpacing

/** CC wordmark width as a fraction of the content area (25% smaller than full width). */
private const val CyclingCommonsLogoWidthFraction = 0.75f

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val page = remember(context) { HelpContent.load(context) }
    ScoutPage(
        title = page?.title ?: stringResource(R.string.help_title_fallback),
        onBack = onBack,
        titleColor = ScoutColors.Brand,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ScoutSpacing.lg,
                    end = ScoutSpacing.lg,
                    bottom = ScoutSpacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(ScoutSpacing.xl),
        ) {
            if (page == null) {
                Text(
                    text = stringResource(R.string.help_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScoutColors.TextSecondary,
                )
            } else {
                page.sections.forEach { section ->
                    HelpSectionBlock(section = section, onOpenLink = onOpenLink)
                }
            }
        }
    }
}

@Composable
private fun HelpSectionBlock(
    section: HelpSection,
    onOpenLink: (String) -> Unit,
) {
    ScoutSection(title = section.heading, titleColor = ScoutColors.Brand) {
        Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.sm)) {
            val imageKey = section.image
            if (imageKey != null) {
                HelpSectionWithLogo(imageKey = imageKey, paragraphs = section.body)
            } else {
                section.body.forEach { paragraph ->
                    HelpParagraph(text = paragraph)
                }
            }
            section.links.forEach { link ->
                HelpLinkRow(link = link, onOpenLink = onOpenLink)
            }
        }
    }
}

/** Logo centered above section body text. */
@Composable
private fun HelpSectionWithLogo(
    imageKey: String,
    paragraphs: List<String>,
) {
    val drawable = when (imageKey) {
        "cycling_commons" -> R.drawable.instance_logo
        "instance" -> R.drawable.instance_logo
        else -> return
    }
    if (paragraphs.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(drawable),
            contentDescription = stringResource(R.string.cd_cycling_commons_logo),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(CyclingCommonsLogoWidthFraction),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ScoutSpacing.xs),
        ) {
            paragraphs.forEach { paragraph ->
                HelpParagraph(text = paragraph)
            }
        }
    }
}

@Composable
private fun HelpParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = ScoutColors.TextPrimary,
    )
}

@Composable
private fun HelpLinkRow(
    link: HelpLink,
    onOpenLink: (String) -> Unit,
) {
    TextButton(
        onClick = { onOpenLink(link.url) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = link.label,
            style = MaterialTheme.typography.bodyLarge,
            color = ScoutColors.Brand,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

