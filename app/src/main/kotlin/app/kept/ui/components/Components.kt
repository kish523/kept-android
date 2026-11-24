package app.kept.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kept.ui.theme.Kept
import app.kept.ui.theme.KeptIcons
import app.kept.ui.theme.KeptMotion
import app.kept.ui.theme.LocalKeptEffects
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shrinks a little while pressed and springs back: the "touch" feel on cards, rows and buttons. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, KeptMotion.press(), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Clickable with the press-scale feel and the platform ripple. */
fun Modifier.keptClickable(
    pressed: Float = 0.97f,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    pressScale(interaction, pressed).clickable(interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, role = role, onClick = onClick)
}

/** Fades and rises into place once, [index] steps after the screen appears. Off when [enabled] is false. */
fun Modifier.enterRise(index: Int, enabled: Boolean = true, stepMs: Long = 45, baseMs: Long = 120): Modifier = composed {
    if (!enabled || !LocalKeptEffects.current.richEntrances) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(baseMs + index * stepMs)
        progress.animateTo(1f, tween(560, easing = KeptMotion.Emphasized))
    }
    val rise = with(LocalDensity.current) { 16.dp.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/**
 * The soft, slowly drifting colour glow behind the top of Home: two radial gradients, so it looks the
 * same on every Android version (no blur effect needed).
 */
fun Modifier.ambientGlow(): Modifier = composed {
    val k = Kept.colors
    val accent = k.accent
    val second = k.glow2
    val alpha = k.glowAlpha
    // The drift redraws every frame forever, so budget phones get the same glow standing still.
    val moving = LocalKeptEffects.current.ambientMotion
    var drift = 0.5f
    var drift2 = 0.5f
    if (moving) {
        val t = rememberInfiniteTransition(label = "glow")
        drift = t.animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift").value
        drift2 = t.animateFloat(1f, 0f, infiniteRepeatable(tween(21_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift2").value
    }
    drawBehind {
        val d = density
        fun blob(color: Color, cx: Float, cy: Float, r: Float) = drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = alpha),
                0.55f to color.copy(alpha = alpha * 0.45f),
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = r,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
        blob(accent, size.width - 40 * d - drift * 40 * d, 150 * d + drift * 30 * d, 210 * d)
        blob(second, 20 * d + drift2 * 30 * d, 280 * d - drift2 * 20 * d, 170 * d)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Kept.colors.muted,
        modifier = modifier.padding(start = 6.dp, bottom = 10.dp).semantics { heading() },
    )
}

/** Rounded square with a line icon in the accent colour: categories, return, warranty. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val k = Kept.colors
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.33f)).background(k.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = k.accent, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun DaysPill(text: String, soon: Boolean, modifier: Modifier = Modifier) {
    val k = Kept.colors
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (soon) k.soon else k.ink,
        modifier = modifier
            .clip(CircleShape)
            .background(if (soon) k.soonContainer else k.surface2)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/**
 * A choice chip. Selected: filled with the accent, the corners morph from square-ish to a pill and a
 * check mark slides in. Read out as a radio button so TalkBack says "selected".
 */
@Composable
fun KeptChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val k = Kept.colors
    val corner by animateDpAsState(if (selected) 19.dp else 12.dp, KeptMotion.pop(), label = "corner")
    val bg by animateColorAsState(if (selected) k.accent else k.surface, tween(250), label = "bg")
    val fg by animateColorAsState(if (selected) k.onAccent else k.ink, tween(250), label = "fg")
    val checkWidth by animateDpAsState(if (selected) 22.dp else 0.dp, KeptMotion.pop(), label = "check")
    val shape = RoundedCornerShape(corner)
    Row(
        modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.background(Color.Transparent))
            .drawBehind {
                if (!selected) {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        k.lineStrong,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                    )
                }
            }
            .keptClickable(pressed = 0.92f, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(checkWidth), contentAlignment = Alignment.CenterStart) {
            if (checkWidth > 2.dp) Icon(KeptIcons.Check, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.padding(vertical = 9.dp))
    }
}

/** Frosted, rounded snackbar with an optional action (Undo). */
@Composable
fun GlassSnackbar(data: SnackbarData) {
    val k = Kept.colors
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(k.heroBackground.copy(alpha = 0.94f))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(k.toastAccent), contentAlignment = Alignment.Center) {
            Icon(KeptIcons.Check, contentDescription = null, tint = Color(0xFF0F1012), modifier = Modifier.size(18.dp))
        }
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.labelLarge,
            color = k.heroInk,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        data.visuals.actionLabel?.let { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = k.toastAccent,
                modifier = Modifier
                    .clip(CircleShape)
                    .keptClickable(pressed = 0.94f) { data.performAction() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            )
        } ?: Spacer(Modifier.width(8.dp))
    }
}

/** A pulsing outline, used once on fields Kept wasn't sure about to draw the eye. */
fun Modifier.attentionPulse(active: Boolean, color: Color, corner: Dp = 0.dp): Modifier = composed {
    if (!active) return@composed this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(700)
        repeat(2) {
            a.snapTo(1f)
            a.animateTo(0f, tween(1400))
        }
    }
    drawBehind {
        if (a.value > 0f) {
            val w = 2.dp.toPx()
            drawRoundRect(
                color.copy(alpha = a.value),
                topLeft = Offset(w / 2, w / 2),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(w),
            )
        }
    }
}

/** Shimmering placeholder block while a receipt is being read. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 58.dp) {
    val k = Kept.colors
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(k.surface2, k.surface, k.surface2),
                        start = Offset(size.width * (x - 1), 0f),
                        end = Offset(size.width * x, 0f),
                    ),
                )
            },
    )
}

/** Three dots bouncing in turn: "working on it". */
@Composable
fun BouncingDots(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val p by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(
                    androidx.compose.animation.core.keyframes {
                        durationMillis = 1000
                        0f at 0 + i * 150
                        1f at 250 + i * 150
                        0f at 500 + i * 150
                    },
                ),
                label = "dot$i",
            )
            val lift = with(LocalDensity.current) { 6.dp.toPx() }
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer { translationY = -p * lift; alpha = 0.35f + 0.65f * p }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Small helper so callers can launch from a composable click without remembering a scope each time. */
@Composable
fun rememberLauncher(): (suspend () -> Unit) -> Unit {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(scope) { { block -> scope.launch { block() } } }
}

/** Marks content that frosted bars blur. Does nothing in light effects mode (no blur there). */
fun Modifier.glassSource(state: HazeState): Modifier = composed {
    if (LocalKeptEffects.current.blur) hazeSource(state) else this
}

/**
 * A frosted bar over [state]'s content. In light effects mode it's a plain translucent tint instead,
 * which costs nothing to draw. [fadeUp] makes it fade in from the top (the strip under the Scan button).
 */
fun Modifier.glassBar(state: HazeState, fadeUp: Boolean = false): Modifier = composed {
    val k = Kept.colors
    when {
        LocalKeptEffects.current.blur -> hazeEffect(state, k.glassBar) {
            if (fadeUp) progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f)
        }
        fadeUp -> background(Brush.verticalGradient(listOf(Color.Transparent, k.background.copy(alpha = 0.9f), k.background.copy(alpha = 0.96f))))
        else -> background(k.background.copy(alpha = 0.96f))
    }
}
