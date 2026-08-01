package org.cyclingcommons.scout.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
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

/** Floated beside the first paragraph in the Cycling Commons help section. */
private val HelpCcLogoWidth = 108.dp

/** logo_cycling_commons.webp is 512×512 with transparent padding baked in. */
private const val CcLogoTopInsetFraction = 119f / 512f
private const val CcLogoContentHeightFraction = 222f / 512f

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
        Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.md)) {
            val imageKey = section.image
            if (imageKey != null) {
                HelpSectionFlowingText(imageKey = imageKey, paragraphs = section.body)
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

/** Logo floated left; first paragraph wraps on the right; rest is full width below. */
@Composable
private fun HelpSectionFlowingText(
    imageKey: String,
    paragraphs: List<String>,
) {
    val drawable = when (imageKey) {
        "cycling_commons" -> R.drawable.logo_cycling_commons
        else -> return
    }
    if (paragraphs.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(HelpCcLogoWidth)
                    .height(HelpCcLogoWidth * CcLogoContentHeightFraction)
                    .clip(RectangleShape),
            ) {
                Image(
                    painter = painterResource(drawable),
                    contentDescription = stringResource(R.string.cd_cycling_commons_logo),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .width(HelpCcLogoWidth)
                        .offset(y = -HelpCcLogoWidth * CcLogoTopInsetFraction),
                )
            }
            Text(
                text = paragraphs.first(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                color = ScoutColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        paragraphs.drop(1).forEach { paragraph ->
            HelpParagraph(text = paragraph)
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

