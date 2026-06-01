package net.perfectdreams.butterscotch.android

import android.content.Context
import net.perfectdreams.butterscotch.android.billing.BillingManager
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameLibrary

object Libraries {
    fun loadGameLibrary(context: Context): GameLibrary {
        return GameLibrary.load(context)
    }

    fun loadLayoutLibrary(context: Context): LayoutLibrary {
        return LayoutLibrary.load(context)
    }
}