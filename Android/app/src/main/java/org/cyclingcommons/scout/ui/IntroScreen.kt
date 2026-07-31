package org.cyclingcommons.scout.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cyclingcommons.scout.R

@Composable
fun IntroScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Square brand mark as-is — no circle / rounded clip over the artwork.
        Image(
            painter = painterResource(R.drawable.scout_logo_white),
            contentDescription = "Scout",
            modifier = Modifier.size(180.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "powering the",
            color = Color(0xFFAAAAAA),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Image(
            painter = painterResource(R.drawable.logo_cycling_commons),
            contentDescription = "Cycling Commons",
            modifier = Modifier
                .widthIn(max = 350.dp)
                .fillMaxWidth(0.95f)
                .height(150.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onContinue) {
            Text("Continue")
        }
    }
}
