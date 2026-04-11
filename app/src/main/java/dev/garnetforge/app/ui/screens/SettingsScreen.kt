package dev.garnetforge.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garnetforge.app.data.model.DeviceInfo
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*

@Composable
fun SettingsScreen(
    deviceInfo: DeviceInfo,
    themeMode: Int,
    blurEnabled: Boolean,
    onTheme: (Int) -> Unit,
    onBlurToggle: (Boolean) -> Unit,
    onTelegram: () -> Unit,
) {
    var aboutExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        SectionHeader("APPEARANCE")
        GarnetCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(when(themeMode) { 1 -> "Dark"; 2 -> "Light"; else -> "System Default" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileChip("Auto", themeMode == 0) { onTheme(0) }
                        ProfileChip("Dark", themeMode == 1) { onTheme(1) }
                        ProfileChip("Light", themeMode == 2) { onTheme(2) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 2.dp), 0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                LabeledSwitch(
                    label = "Background Blur",
                    subtitle = "Glass effect behind expanded tuning cards",
                    checked = blurEnabled,
                    onCheckedChange = onBlurToggle
                )
            }
        }

        SectionHeader("ABOUT")
        GarnetCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { aboutExpanded = !aboutExpanded }
                    .padding(vertical = 4.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Column {
                    Text("GarnetForge", style = MaterialTheme.typography.titleMedium,
                        color = GarnetLight, fontWeight = FontWeight.Bold)
                    Text("Kernel Manager · v1.0.0", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (aboutExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(aboutExpanded,
                enter = expandVertically() + fadeIn(),
                exit  = shrinkVertically() + fadeOut()) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), 0.5.dp, BorderCol)

                    SubHeader("DEVICE")
                    InfoRow("Device",   deviceInfo.deviceName.ifEmpty { "—" })
                    InfoRow("Codename", deviceInfo.codename.ifEmpty { "—" })
                    InfoRow("SoC",      deviceInfo.socModel.ifEmpty { "—" })
                    InfoRow("Android",  deviceInfo.androidVersion.ifEmpty { "—" })
                    InfoRow("Kernel",   deviceInfo.kernelVersion.ifEmpty { "—" })
                    Spacer(Modifier.height(8.dp))

                    HorizontalDivider(Modifier.padding(vertical = 4.dp), 0.5.dp, BorderCol)
                    SubHeader("DEVELOPER")
                    Text("montfort_1607", style = MaterialTheme.typography.bodyLarge,
                        color = GarnetLight, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onTelegram)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Send, null, tint = GarnetLight, modifier = Modifier.size(16.dp))
                        Text("@montfort_1607 on Telegram",
                            style = MaterialTheme.typography.bodyMedium, color = GarnetLight)
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = GarnetLight,
            modifier = Modifier.widthIn(max = 200.dp))
    }
}
