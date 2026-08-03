package org.cyclingcommons.scout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.RecoveryPrompt
import org.cyclingcommons.scout.ui.components.ScoutButton
import org.cyclingcommons.scout.ui.components.ScoutLogo
import org.cyclingcommons.scout.ui.components.ScoutLogoLayout
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutSpacing

@Composable
fun RecoveryScreen(
    prompt: RecoveryPrompt,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScoutSpacing.lg, vertical = ScoutSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(ScoutSpacing.xl),
    ) {
        ScoutLogo(
            markSize = 72.dp,
            layout = ScoutLogoLayout.Vertical,
            lockupWidthFraction = 0.5f,
        )
        Text(
            text = stringResource(R.string.recovery_title),
            style = MaterialTheme.typography.headlineMedium,
            color = ScoutColors.Brand,
        )
        Text(
            text = stringResource(
                R.string.recovery_message,
                prompt.elapsedLabel,
                prompt.sampleCount,
                prompt.fitFileName,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = ScoutColors.TextSecondary,
        )
        ScoutButton(
            label = stringResource(R.string.recovery_resume),
            onClick = onResume,
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        )
        ScoutButton(
            label = stringResource(R.string.recovery_discard),
            onClick = onDiscard,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.recovery_discard_hint),
            style = MaterialTheme.typography.bodySmall,
            color = ScoutColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
