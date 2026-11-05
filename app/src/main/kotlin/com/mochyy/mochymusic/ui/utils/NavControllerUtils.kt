// NavControllerUtils.kt

package com.mochyy.mochymusic.ui.utils

import androidx.navigation.NavController
import com.mochyy.mochymusic.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
