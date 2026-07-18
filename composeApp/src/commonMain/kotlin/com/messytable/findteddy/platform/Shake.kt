package com.messytable.findteddy.platform

import androidx.compose.runtime.Composable

/**
 * Listens to the device accelerometer while [enabled] and invokes [onShake]
 * (debounced) whenever the phone is shaken.
 */
@Composable
expect fun ShakeListener(enabled: Boolean, onShake: () -> Unit)
