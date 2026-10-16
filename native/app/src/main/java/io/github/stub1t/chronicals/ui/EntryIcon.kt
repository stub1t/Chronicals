package io.github.stub1t.chronicals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stub1t.chronicals.data.EntryType

/** Material stand-ins for the Lucide icons custom types store as 'lucide:<key>'. */
private val CUSTOM_ICONS: Map<String, ImageVector> = mapOf(
    "activity" to Icons.Outlined.MonitorHeart,
    "heart-pulse" to Icons.Outlined.FavoriteBorder,
    "brain" to Icons.Outlined.Psychology,
    "eye" to Icons.Outlined.Visibility,
    "ear" to Icons.Outlined.Hearing,
    "bone" to Icons.Outlined.Accessibility,
    "hand" to Icons.Outlined.PanTool,
    "thermometer" to Icons.Outlined.Thermostat,
    "flame" to Icons.Outlined.LocalFireDepartment,
    "zap" to Icons.Outlined.Bolt,
    "wind" to Icons.Outlined.Air,
    "flower-2" to Icons.Outlined.LocalFlorist,
    "leaf" to Icons.Outlined.Eco,
    "sun" to Icons.Outlined.WbSunny,
    "cloud-rain" to Icons.Outlined.Umbrella,
    "snowflake" to Icons.Outlined.AcUnit,
    "moon" to Icons.Outlined.DarkMode,
    "bed" to Icons.Outlined.Bed,
    "droplet" to Icons.Outlined.WaterDrop,
    "droplets" to Icons.Outlined.Opacity,
    "candy" to Icons.Outlined.Icecream,
    "pill" to Icons.Outlined.Medication,
    "syringe" to Icons.Outlined.Vaccines,
    "stethoscope" to Icons.Outlined.MedicalServices,
    "bandage" to Icons.Outlined.Healing,
    "apple" to Icons.Outlined.LocalGroceryStore,
    "utensils" to Icons.Outlined.Restaurant,
    "coffee" to Icons.Outlined.Coffee,
    "wine" to Icons.Outlined.WineBar,
    "dumbbell" to Icons.Outlined.FitnessCenter,
    "footprints" to Icons.Outlined.DirectionsWalk,
    "baby" to Icons.Outlined.ChildCare,
    "sparkles" to Icons.Outlined.AutoAwesome,
    "notebook-pen" to Icons.Outlined.EditNote,
)

/** Icon keys offered for own types, in the order of the picker. */
val CUSTOM_ICON_KEYS: List<String> get() = CUSTOM_ICONS.keys.toList()

fun hexColor(hex: String?, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

@Composable
fun typeColor(type: EntryType?): Color = hexColor(type?.color, MaterialTheme.colorScheme.onSurfaceVariant)

// Each path is parsed on its own: PathParser.toNodes() hands out the parser's own list, so a shared
// parser turns every path into the last one.
private fun duoVector(data: DuoIconData, color: Color): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        fun nodes(d: String) = addPathNodes(d)
        data.fill.forEach { addPath(nodes(it.d), fill = SolidColor(color), fillAlpha = 0.22f * it.opacity) }
        data.stroke.forEach {
            addPath(nodes(it.d), stroke = SolidColor(color), strokeAlpha = it.opacity, strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        data.solid.forEach { addPath(nodes(it.d), fill = SolidColor(color), fillAlpha = it.opacity) }
    }.build()

/** A built-in duo icon by name ('headache', 'medication', ...), in a colour. */
@Composable
fun DuoIcon(name: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val data = DuoIcons.ALL[name] ?: return
    val vector = remember(name, color) { duoVector(data, color) }
    Icon(vector, contentDescription = null, tint = Color.Unspecified, modifier = modifier.size(size))
}

/** The icon of an entry type: drawn for built-ins, Material for custom ones, else its emoji. Optionally on a soft circle. */
@Composable
fun EntryIcon(type: EntryType?, size: Dp, badge: Boolean = false, modifier: Modifier = Modifier) {
    val color = typeColor(type)
    val inner = if (badge) size * 0.58f else size
    Box(
        modifier
            .size(size)
            .then(if (badge) Modifier.background(color.copy(alpha = 0.16f), CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val duo = type?.let { DuoIcons.BY_TYPE[it.id] }
        val icon = type?.icon
        when {
            duo != null -> DuoIcon(duo, color, inner)
            icon == null || icon.startsWith("lucide:") ->
                Icon(CUSTOM_ICONS[icon?.removePrefix("lucide:")] ?: Icons.Outlined.Adjust, null, tint = color, modifier = Modifier.size(inner))
            else -> Text(icon, fontSize = (inner.value * 0.9f).sp)
        }
    }
}
