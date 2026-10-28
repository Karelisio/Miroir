package com.karelisio.miroir.ui.pairing

import com.karelisio.miroir.ui.components.miroirTopBar
import com.karelisio.miroir.ui.components.miroirSafe
import androidx.compose.foundation.layout.WindowInsets
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.BuildConfig
import com.karelisio.miroir.permissions.AppPermission
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.springPress
import com.karelisio.miroir.ui.onboarding.MirrorBubbles
import com.karelisio.miroir.ui.settings.rememberPermissionRequester
import com.karelisio.miroir.ui.settings.rememberPermissionStates
import kotlinx.coroutines.delay

@Composable
fun PairingScreen(
    container: AppContainer,
    showInvite: Boolean,
    onPaired: () -> Unit,
    onBack: (() -> Unit)?,
    onMessage: (String) -> Unit,
) {
    val vm: PairingViewModel = viewModel { PairingViewModel(container, showInvite) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    LaunchedEffect(ui.error) { ui.error?.let { haptics.reject(); onMessage(it); vm.clearError() } }
    LaunchedEffect(ui.step) {
        if (ui.step == PairStep.DONE) {
            haptics.confirm()
            delay(1800)
            onPaired()
        }
    }
    val canGoBack = ui.step == PairStep.SCAN || (ui.step == PairStep.CHOOSE && !showInvite)
    BackHandler(enabled = canGoBack) { vm.back() }

    Scaffold(
        contentWindowInsets = WindowInsets.miroirSafe,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.miroirTopBar,
                title = {},
                navigationIcon = {
                    if (canGoBack || onBack != null) {
                        IconButton(onClick = { if (canGoBack) vm.back() else onBack?.invoke() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = ui.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(220, 60))) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut(tween(140)))
            },
            label = "pairing",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { step ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                when (step) {
                    PairStep.NAME -> NameStep(ui.name, vm::setName, vm::confirmName)
                    PairStep.CHOOSE -> ChooseStep(ui.serverUrl, vm::setServer, onCreate = { haptics.press(); vm.create() }, onScan = { haptics.press(); vm.scan() })
                    PairStep.WORKING -> {
                        LoadingIndicator(Modifier.size(96.dp))
                        Text("Connexion de vos miroirs…", style = MaterialTheme.typography.titleLarge)
                    }
                    PairStep.SHOW_QR -> InviteStep(ui.invite.orEmpty())
                    PairStep.SCAN -> ScanStep(vm::onScanned)
                    PairStep.DONE -> {
                        MirrorBubbles()
                        Text("Vous êtes reliés 💜", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                        Text("Votre première question vous attend.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onContinue: () -> Unit) {
    MirrorBubbles(size = 0.7f)
    Text("Comment t'appelles-tu ?", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    Text("C'est ainsi que l'autre te verra dans Miroir.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        singleLine = true,
        label = { Text("Ton prénom") },
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onContinue() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onContinue, enabled = name.isNotBlank(), shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Continuer", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ChooseStep(serverUrl: String, onServer: (String) -> Unit, onCreate: () -> Unit, onScan: () -> Unit) {
    var advanced by rememberSaveable { mutableStateOf(BuildConfig.WORKER_URL.isBlank()) }
    Text("Relions vos deux miroirs", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    Text(
        "L'un de vous crée le miroir et affiche un QR code, l'autre le scanne. La clé de chiffrement passe directement d'un téléphone à l'autre.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChoiceCard(Icons.Rounded.QrCode2, "Créer notre miroir", "Afficher un QR code à scanner", MaterialShapes.Cookie9Sided, primary = true, onClick = onCreate)
    ChoiceCard(Icons.Rounded.QrCodeScanner, "Scanner le QR de l'autre", "L'autre a déjà créé le miroir", MaterialShapes.Flower, primary = false, onClick = onScan)
    TextButton(onClick = { advanced = !advanced }) {
        Icon(Icons.Rounded.Dns, null)
        Spacer(Modifier.width(8.dp))
        Text("Serveur")
    }
    AnimatedVisibility(advanced, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServer,
            singleLine = true,
            label = { Text("Adresse du Worker Cloudflare") },
            placeholder = { Text("https://miroir.exemple.workers.dev") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    polygon: androidx.graphics.shapes.RoundedPolygon,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (primary) colors.primaryContainer else colors.secondaryContainer,
        contentColor = if (primary) colors.onPrimaryContainer else colors.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().springPress(interaction, 0.96f),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(polygon.toShape()).background(if (primary) colors.primary else colors.secondary),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (primary) colors.onPrimary else colors.onSecondary) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InviteStep(invite: String) {
    val colors = MaterialTheme.colorScheme
    Text("Scanne-moi depuis l'autre téléphone", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        MorphingBlob(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { alpha = 0.35f }, colors.primary, periodMs = 8000)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.White, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth(0.78f)) {
            QrCode(invite, Modifier.padding(22.dp))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(Modifier.size(36.dp))
        Spacer(Modifier.width(8.dp))
        Text("En attente de l'autre téléphone…", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "Sur l'autre téléphone : Miroir → « Scanner le QR de l'autre ». Ne partage pas ce code : il contient votre clé secrète.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = colors.onSurfaceVariant,
    )
}

@Composable
private fun ScanStep(onScanned: (String) -> Unit) {
    val states = rememberPermissionStates()
    val request = rememberPermissionRequester()
    val granted = states[AppPermission.CAMERA] == true
    Text("Vise le QR code de l'autre", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    if (granted) {
        QrScanner(onScanned, Modifier.fillMaxWidth().aspectRatio(0.8f).clip(MaterialTheme.shapes.extraLarge))
    } else {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            MorphingBlob(Modifier.fillMaxSize(), MaterialTheme.colorScheme.primaryContainer)
            Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(AppPermission.CAMERA.why, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = { request(AppPermission.CAMERA) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Autoriser la caméra")
        }
    }
}
