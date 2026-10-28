package com.karelisio.miroir.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * Marges sûres en mode immersif : les barres système sont masquées (marges nulles),
 * il faut donc éviter nous-mêmes l'encoche de la caméra.
 */
val WindowInsets.Companion.miroirSafe: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/** Marges pour une barre d'application en haut d'écran. */
val WindowInsets.Companion.miroirTopBar: WindowInsets
    @Composable get() = miroirSafe.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
