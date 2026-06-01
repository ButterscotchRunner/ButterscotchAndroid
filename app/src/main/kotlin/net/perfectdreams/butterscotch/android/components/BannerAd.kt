package net.perfectdreams.butterscotch.android.components

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import net.perfectdreams.butterscotch.android.BuildConfig

// In debug builds we MUST use Google's sample ad unit so we don't rack up invalid traffic on the real unit
private val BANNER_AD_UNIT_ID = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/9214589741" else "ca-app-pub-9989170954243288/9870425234"

/**
 * An anchored adaptive AdMob banner sized to the current screen width.
 *
 * AdMob's [AdView] is a classic Android View, so we host it through [AndroidView]. The view is built
 * once per composition (keyed by width) and the ad is requested in the factory so it loads exactly once
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp

    // Reserve the banner's height up front so the list doesn't jump around once the ad finishes loading
    val adSize = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(adSize.height.dp),
        factory = { ctx ->
            // FrameLayout wrapper lets the banner center itself within the full width
            val container = FrameLayout(ctx)
            val adView = AdView(ctx).apply {
                adUnitId = BANNER_AD_UNIT_ID
                setAdSize(adSize)
            }
            container.addView(adView)
            adView.loadAd(AdRequest.Builder().build())
            container
        },
    )
}
