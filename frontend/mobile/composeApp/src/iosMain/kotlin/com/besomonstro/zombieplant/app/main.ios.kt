
package com.besomonstro.zombieplant.app

import androidx.compose.ui.window.ComposeUIViewController
import zombieplant.IOSPlatform
import zombieplant.Platform

fun MainViewController() = ComposeUIViewController { App() }

fun init() {
    Platform.INSTANCE = IOSPlatform()
}
