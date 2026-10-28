package com.karelisio.miroir.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.staggeredAppear

/** Écran opaque affiché par-dessus l'appli tant qu'elle est verrouillée. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(180.dp).staggeredAppear(0, "lock-blob"), contentAlignment = Alignment.Center) {
                MorphingBlob(
                    Modifier.fillMaxSize().graphicsLayer { alpha = 0.9f },
                    colors.primaryContainer,
                    shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Heart, MaterialShapes.Flower, MaterialShapes.Heart),
                    periodMs = 5200,
                )
                Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(72.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Miroir est verrouillé", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.staggeredAppear(1, "lock-title"))
            Spacer(Modifier.height(8.dp))
            Text(
                "Vos réponses restent entre vous deux.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.staggeredAppear(2, "lock-sub"),
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onUnlock, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp).staggeredAppear(3, "lock-btn")) {
                Icon(Icons.Rounded.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("Déverrouiller", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
