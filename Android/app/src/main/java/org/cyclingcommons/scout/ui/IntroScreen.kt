package org.cyclingcommons.scout.ui



import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.res.painterResource

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import org.cyclingcommons.scout.R

import org.cyclingcommons.scout.ui.components.ScoutButton

import org.cyclingcommons.scout.ui.components.ScoutLogo

import org.cyclingcommons.scout.ui.components.ScoutLogoLayout

import org.cyclingcommons.scout.ui.theme.ScoutColors

import org.cyclingcommons.scout.ui.theme.ScoutSpacing

import org.cyclingcommons.scout.ui.theme.ScoutType



/** First run only: what Scout does, what it costs, and who it belongs to. */

@Composable

fun IntroScreen(onContinue: () -> Unit) {

    Column(

        modifier = Modifier

            .fillMaxSize()

            .background(ScoutColors.Screen)

            .verticalScroll(rememberScrollState())

            .padding(horizontal = ScoutSpacing.xl, vertical = ScoutSpacing.xxl),

        horizontalAlignment = Alignment.CenterHorizontally,

    ) {

        Spacer(Modifier.height(ScoutSpacing.xxl))

        ScoutLogo(
            markSize = 112.dp,
            layout = ScoutLogoLayout.Vertical,
        )

        Spacer(Modifier.height(ScoutSpacing.md))

        Text(

            text = stringResource(R.string.brand_tagline),

            style = MaterialTheme.typography.bodyLarge,

            color = ScoutColors.TextSecondary,

            textAlign = TextAlign.Center,

        )

        Spacer(Modifier.height(ScoutSpacing.xl))

        Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.lg)) {

            IntroPoint(stringResource(R.string.intro_point_tag))

            IntroPoint(stringResource(R.string.intro_point_radar))

            IntroPoint(stringResource(R.string.intro_point_private))

        }

        Spacer(Modifier.height(ScoutSpacing.xl))

        Text(

            text = stringResource(R.string.intro_permissions_title),

            style = MaterialTheme.typography.titleMedium,

            color = ScoutColors.TextPrimary,

            modifier = Modifier.fillMaxWidth(),

        )

        Spacer(Modifier.height(ScoutSpacing.sm))

        Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.md)) {

            IntroPoint(stringResource(R.string.intro_permission_location))

            IntroPoint(stringResource(R.string.intro_permission_notifications))

            IntroPoint(stringResource(R.string.intro_permission_bluetooth))

        }

        Text(

            text = stringResource(R.string.intro_permissions_when),

            style = MaterialTheme.typography.bodyMedium,

            color = ScoutColors.TextSecondary,

            modifier = Modifier

                .fillMaxWidth()

                .padding(top = ScoutSpacing.md),

        )

        Spacer(Modifier.height(ScoutSpacing.xxl))

        Text(

            text = stringResource(R.string.intro_powering).uppercase(),

            style = ScoutType.overline,

            color = ScoutColors.TextSecondary,

        )

        Spacer(Modifier.height(ScoutSpacing.md))

        Image(

            painter = painterResource(R.drawable.logo_cycling_commons),

            contentDescription = stringResource(R.string.cd_cycling_commons_logo),

            contentScale = ContentScale.Fit,

            modifier = Modifier

                .fillMaxWidth()

                .height(110.dp),

        )

        Spacer(Modifier.height(ScoutSpacing.xxl))

        ScoutButton(

            label = stringResource(R.string.intro_continue),

            onClick = onContinue,

            primary = true,

            modifier = Modifier.fillMaxWidth(),

        )

        Spacer(Modifier.height(ScoutSpacing.lg))

    }

}



@Composable

private fun IntroPoint(text: String) {

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.md),

    ) {

        Spacer(

            modifier = Modifier

                .padding(top = ScoutSpacing.sm)

                .size(8.dp)

                .background(ScoutColors.Brand, CircleShape),

        )

        Text(

            text = text,

            style = MaterialTheme.typography.bodyLarge,

            color = ScoutColors.TextPrimary,

            modifier = Modifier.weight(1f),

        )

    }

}


