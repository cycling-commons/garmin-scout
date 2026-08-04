package org.cyclingcommons.scout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutDimens
import org.cyclingcommons.scout.ui.theme.ScoutSpacing
import org.cyclingcommons.scout.ui.theme.ScoutType

/** The pin-and-ripple mark, tinted to sit on any Scout surface. */
@Composable
fun ScoutMark(
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = ScoutColors.Brand,
) {
    Icon(
        painter = painterResource(R.drawable.ic_scout_mark),
        contentDescription = stringResource(R.string.cd_scout_logo),
        tint = tint,
        modifier = modifier.size(size),
    )
}

enum class ScoutLogoLayout {
    /** Mark and name on one line — ride header. */
    Horizontal,
    /** Mark above name — intro / splash. */
    Vertical,
}

/** Ride header: disc mark + name. Intro: SVG lockup (`Brand/welcome-logo.svg`). */
@Composable
fun ScoutLogo(
    markSize: Dp,
    modifier: Modifier = Modifier,
    layout: ScoutLogoLayout = ScoutLogoLayout.Horizontal,
    /** Vertical lockup only — fraction of parent width (default matches ride chrome scale). */
    lockupWidthFraction: Float = 0.7f,
    @Suppress("UNUSED_PARAMETER") nameSize: TextUnit? = null,
) {
    when (layout) {
        ScoutLogoLayout.Horizontal -> {
            val textStyle = TextStyle(
                fontFamily = MaterialTheme.typography.headlineMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = nameSize ?: (markSize.value * 0.54f).sp,
                letterSpacing = (-0.5).sp,
            )
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.xs),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_scout_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(markSize),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = textStyle,
                    color = ScoutColors.Brand,
                )
            }
        }
        ScoutLogoLayout.Vertical -> {
            ScoutLockupLogo(modifier = modifier.fillMaxWidth(lockupWidthFraction))
        }
    }
}

/** Renders `Brand/welcome-logo.svg` verbatim (outlined paths, no font lookup). */
@Composable
private fun ScoutLockupLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/welcome-logo.svg")
            .build(),
        imageLoader = imageLoader,
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** Page frame for the secondary screens: title, back affordance, scrolling body. */
@Composable
fun ScoutPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = ScoutColors.TextPrimary,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScoutColors.Screen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ScoutSpacing.sm,
                    end = ScoutSpacing.lg,
                    top = ScoutSpacing.sm,
                    bottom = ScoutSpacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.xs),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_done),
                    tint = ScoutColors.TextPrimary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = titleColor,
            )
        }
        content()
    }
}

/** Grouped block of settings/pairing controls. */
@Composable
fun ScoutSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = ScoutColors.TextSecondary,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = ScoutType.overline,
            color = titleColor,
            modifier = Modifier.padding(start = ScoutSpacing.xs, bottom = ScoutSpacing.sm),
        )
        ScoutCard {
            Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.md)) {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ScoutColors.TextSecondary,
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun ScoutCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ScoutColors.Surface, RoundedCornerShape(ScoutDimens.cardCorner))
            .border(
                width = 1.dp,
                color = ScoutColors.Outline,
                shape = RoundedCornerShape(ScoutDimens.cardCorner),
            )
            .padding(ScoutSpacing.lg),
    ) {
        content()
    }
}

/** Full-width action. Primary = brand fill, secondary = outline. */
@Composable
fun ScoutButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(ScoutDimens.tileCorner)
    val content: @Composable () -> Unit = {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ScoutColors.Brand,
                contentColor = ScoutColors.TextOnBrand,
                disabledContainerColor = ScoutColors.SurfaceRaised,
                disabledContentColor = ScoutColors.TextSecondary,
            ),
            elevation = null,
            modifier = modifier.height(ScoutDimens.controlHeight),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ScoutColors.TextPrimary,
                disabledContentColor = ScoutColors.TextSecondary,
            ),
            modifier = modifier.height(ScoutDimens.controlHeight),
        ) { content() }
    }
}

/**
 * A switch setting. The whole row toggles — a bare switch is a small target for a
 * gloved thumb — and the ON/OFF word carries the state for anyone who reads the
 * unlit track as decoration rather than a control.
 */
@Composable
fun ScoutToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScoutDimens.tileCorner))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = ScoutSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = ScoutColors.TextPrimary,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScoutColors.TextSecondary,
                )
            }
        }
        Text(
            text = stringResource(if (checked) R.string.toggle_on else R.string.toggle_off),
            style = ScoutType.overline,
            color = if (checked) ScoutColors.Brand else ScoutColors.TextSecondary,
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ScoutColors.TextOnBrand,
                checkedTrackColor = ScoutColors.Brand,
                checkedBorderColor = ScoutColors.Brand,
                uncheckedThumbColor = ScoutColors.TextSecondary,
                uncheckedTrackColor = ScoutColors.Screen,
                uncheckedBorderColor = ScoutColors.OutlineStrong,
            ),
        )
    }
}

/** Small status chip: a dot plus a word, used for ride and radar state. */
@Composable
fun StatusPill(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(ScoutColors.SurfaceRaised, CircleShape)
            .padding(horizontal = ScoutSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Text(
            text = label.uppercase(),
            style = ScoutType.overline,
            color = ScoutColors.TextPrimary,
        )
    }
}
