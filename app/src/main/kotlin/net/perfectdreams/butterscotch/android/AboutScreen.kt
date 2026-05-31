package net.perfectdreams.butterscotch.android

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar

@Composable
fun AboutScreen(nav: NavHostController) {
    Scaffold(
        topBar = { ButterscotchTopBar("About", nav, navigationIcon = { ButterscotchBackButton(nav) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val transition = rememberInfiniteTransition(label = "logoBob")
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1_800, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "offsetY",
            )

            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.butterscotch_logo),
                contentDescription = "Butterscotch logo",
                // Nearest-neighbor keeps the pixel-art crisp when scaled up
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(160.dp)
                    .offset { IntOffset(0, offsetY.dp.roundToPx()) },
            )

            Text("Butterscotch", style = MaterialTheme.typography.headlineMedium)
            Text("Created by MrPowerGamerBR", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
