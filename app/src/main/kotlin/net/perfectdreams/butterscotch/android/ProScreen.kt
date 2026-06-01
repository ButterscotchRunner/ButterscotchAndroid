package net.perfectdreams.butterscotch.android

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.billing.BillingManager
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar

@Composable
fun ProScreen(nav: NavHostController) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val billing = remember { BillingManager.getInstance(context) }

    Scaffold(
        topBar = { ButterscotchTopBar("Butterscotch Pro", nav, navigationIcon = { ButterscotchBackButton(nav) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                bitmap = ImageBitmap.imageResource(R.drawable.butterscotch_logo_pro),
                contentDescription = "Butterscotch Pro logo",
                // Nearest-neighbor keeps the pixel-art crisp when scaled up
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(160.dp)
                    .offset { IntOffset(0, offsetY.dp.roundToPx()) },
            )

            Text("Butterscotch Pro", style = MaterialTheme.typography.headlineMedium)

            Spacer(Modifier.height(24.dp))

            ProPerk(Icons.Filled.Block, "No ads", "Enjoy Butterscotch without any ads")
            Spacer(Modifier.height(12.dp))
            ProPerk(Icons.Filled.Home, "Home-screen shortcuts", "Pin your games straight to your home screen")
            Spacer(Modifier.height(12.dp))
            ProPerk(Icons.Filled.Code, "Support the Project", "Help us keep developing and improving Butterscotch")

            Spacer(Modifier.height(32.dp))

            if (billing.isPro) {
                Button(onClick = {}, enabled = false) {
                    Text("Thanks for Supporting Butterscotch! :3")
                }
            } else {
                val price = billing.proProduct?.oneTimePurchaseOfferDetails?.formattedPrice
                Button(
                    onClick = { activity?.let { billing.launchProPurchase(it) } },
                    // No product details yet means the billing connection is not ready
                    enabled = billing.proProduct != null && activity != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (price != null) "Get Pro • $price" else "Get Pro")
                }
            }
        }
    }
}

@Composable
private fun ProPerk(icon: ImageVector, title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
