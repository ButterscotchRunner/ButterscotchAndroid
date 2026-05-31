package net.perfectdreams.butterscotch.android

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar

@Composable
fun AboutScreen(nav: NavHostController) {
    val resources = LocalResources.current
    val contributors = remember(resources) {
        resources.openRawResource(R.raw.contributors)
            .bufferedReader()
            .use { it.readText() }
            .lines()
            .filter { it.isNotBlank() }
    }

    Scaffold(
        topBar = { ButterscotchTopBar("About", nav, navigationIcon = { ButterscotchBackButton(nav) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
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

            Spacer(Modifier.height(24.dp))
            Text("Contributors", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            contributors.forEach { name ->
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { nav.navigate(Route.Licenses) }) {
                Text("Open-Source Licenses")
            }
        }
    }
}
