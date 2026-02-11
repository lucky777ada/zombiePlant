package zombieplant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PropaneTank
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import zombieplant.ui.Image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZombiePlantDashboard(
    viewModel: ZombiePlantViewModel = viewModel(),
    videoPlayer: @Composable (ByteArray, () -> Unit) -> Unit,
    platform: Platform
) {
    val hardwareStatus by viewModel.hardwareStatus.collectAsState()
    val lastUpdated by viewModel.lastUpdated.collectAsState()
    val plantImage by viewModel.plantImage.collectAsState()
    val latestTimelapse by viewModel.latestTimelapse.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var autoRefresh by remember { mutableStateOf(false) }
    var showVideo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchData(platform)
    }

    LaunchedEffect(autoRefresh) {
        while (autoRefresh) {
            viewModel.fetchData(platform)
            delay(5000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZombiePlant") },
                navigationIcon = {
                    IconButton(onClick = { /* Handle navigation icon click */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    Text("Auto-refresh")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoRefresh,
                        onCheckedChange = { autoRefresh = it }
                    )
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    PlantImage(
                        plantImage = plantImage,
                        onImageClick = {
                            viewModel.fetchLatestTimelapse()
                            showVideo = true
                        }
                    )
                }
                item {
                    SensorReadings(hardwareStatus, lastUpdated)
                }
                item {
                    HardwareControls(
                        hardwareStatus = hardwareStatus,
                        onMainLightChange = { viewModel.toggleAcRelay(if (it) "on" else "off", platform) },
                        onWaterOutChange = { viewModel.togglePump("water_out", if (it) 5.0f else 0.0f, platform) },
                        onWaterInChange = { viewModel.togglePump("water_in", if (it) 5.0f else 0.0f, platform) },
                        onFloraMicroChange = { viewModel.togglePump("flora_micro", if (it) 5.0f else 0.0f, platform) },
                        onFloraBloomChange = { viewModel.togglePump("flora_bloom", if (it) 5.0f else 0.0f, platform) },
                        onFloraGroChange = { viewModel.togglePump("flora_gro", if (it) 5.0f else 0.0f, platform) }
                    )
                }
            }
        }
    }

    if (showVideo) {
        latestTimelapse?.let {
            videoPlayer(it) {
                showVideo = false
            }
        }
    }
}

@Composable
fun SensorReadings(hardwareStatus: HardwareStatusResponse?, lastUpdated: Long) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val environment = hardwareStatus?.environment
            val temperature = if (environment is DHTSuccess) environment.temperature_f else "N/A"
            val humidity = if (environment is DHTSuccess) environment.humidity_percent else "N/A"

            SensorRow(icon = Icons.Default.Thermostat, label = "Temperature", value = "$temperature°F")
            SensorRow(icon = Icons.Default.WaterDrop, label = "Humidity", value = "$humidity%")
            SensorRow(icon = Icons.Default.Science, label = "pH", value = "${hardwareStatus?.ph?.ph ?: "N/A"} pH")
            SensorRow(icon = Icons.Default.WbSunny, label = "TDS", value = "${hardwareStatus?.tds?.ppm ?: "N/A"} ppm")
            SensorRow(
                icon = Icons.Default.PropaneTank,
                label = "Tank Level",
                value = if (hardwareStatus?.water_level?.full == true) "Full" else if (hardwareStatus?.water_level?.empty == true) "Empty" else "Normal"
            )
            SensorRow(icon = Icons.Default.Update, label = "Last Updated", value = formatTime(lastUpdated))
        }
    }
}

@Composable
fun SensorRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }
        Text(value)
    }
}

@Composable
fun NutrientsDialog(
    hardwareStatus: HardwareStatusResponse?,
    onDismiss: () -> Unit,
    onFloraMicroChange: (Boolean) -> Unit,
    onFloraBloomChange: (Boolean) -> Unit,
    onFloraGroChange: (Boolean) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Nutrient Pumps", style = MaterialTheme.typography.titleLarge)
                Control(
                    icon = Icons.Default.Grass,
                    label = "Flora Micro",
                    checked = hardwareStatus?.pumps?.get("flora_micro") == 1,
                    onCheckedChange = onFloraMicroChange
                )
                Control(
                    icon = Icons.Default.Grass,
                    label = "Flora Bloom",
                    checked = hardwareStatus?.pumps?.get("flora_bloom") == 1,
                    onCheckedChange = onFloraBloomChange
                )
                Control(
                    icon = Icons.Default.Grass,
                    label = "Flora Gro",
                    checked = hardwareStatus?.pumps?.get("flora_gro") == 1,
                    onCheckedChange = onFloraGroChange
                )
            }
        }
    }
}

@Composable
fun HardwareControls(
    hardwareStatus: HardwareStatusResponse?,
    onMainLightChange: (Boolean) -> Unit,
    onWaterOutChange: (Boolean) -> Unit,
    onWaterInChange: (Boolean) -> Unit,
    onFloraMicroChange: (Boolean) -> Unit,
    onFloraBloomChange: (Boolean) -> Unit,
    onFloraGroChange: (Boolean) -> Unit
) {
    var showNutrientsDialog by remember { mutableStateOf(false) }

    if (showNutrientsDialog) {
        NutrientsDialog(
            hardwareStatus = hardwareStatus,
            onDismiss = { showNutrientsDialog = false },
            onFloraMicroChange = onFloraMicroChange,
            onFloraBloomChange = onFloraBloomChange,
            onFloraGroChange = onFloraGroChange
        )
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Hardware controls", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Control(
                    icon = Icons.Default.WbSunny,
                    label = "Main Light",
                    checked = hardwareStatus?.ac_power == "on",
                    onCheckedChange = onMainLightChange
                )
                Control(
                    icon = Icons.Default.WaterDrop,
                    label = "Water Out",
                    checked = hardwareStatus?.pumps?.get("water_out") == 1,
                    onCheckedChange = onWaterOutChange
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Control(
                    icon = Icons.Default.WaterDrop,
                    label = "Water In",
                    checked = hardwareStatus?.pumps?.get("water_in") == 1,
                    onCheckedChange = onWaterInChange
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Grass, contentDescription = "Nutrients")
                    Text("Nutrients")
                    Button(onClick = { showNutrientsDialog = true }) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

@Composable
fun Control(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label)
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PlantImage(plantImage: ByteArray?, onImageClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable(onClick = onImageClick)
    ) {
        if (plantImage != null) {
            Image(
                data = plantImage,
                contentDescription = "Plant Image",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun formatTime(timeInMillis: Long): String {
    if (timeInMillis == 0L) return "N/A"
    val instant = Instant.fromEpochMilliseconds(timeInMillis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.date} ${localDateTime.hour}:${localDateTime.minute}:${localDateTime.second}"
}
