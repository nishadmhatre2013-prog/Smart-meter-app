package com.example

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

data class BleAppTheme(
    val id: Int,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val cardBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val activeGlow: Color
)

fun getAppTheme(index: Int, isDark: Boolean): BleAppTheme {
    return when(index) {
        1 -> {
            if (isDark) {
                BleAppTheme(
                    id = 1,
                    name = "Neon Cyberpunk (Dark)",
                    primary = Color(0xFFEC4899), // Hot Pink
                    secondary = Color(0xFF8B5CF6), // Royal Purple
                    accent = Color(0xFF00F0FF), // Cyber Cyan
                    background = Color(0xFF0F0B26), // Neon Dark Purple
                    cardBg = Color(0xFF1D1440), // Indigo Purple Card
                    textPrimary = Color(0xFFFFFFFF),
                    textSecondary = Color(0xFF9386A4),
                    border = Color(0xFF3E287A),
                    activeGlow = Color(0xFFEC4899)
                )
            } else {
                BleAppTheme(
                    id = 1,
                    name = "Neon Cyberpunk (Light)",
                    primary = Color(0xFFDB2777), // Active Pink
                    secondary = Color(0xFF7C3AED), // Purple
                    accent = Color(0xFF0D9488), // Teal Accent
                    background = Color(0xFFFAF7FD), // Light Pinkish White tint
                    cardBg = Color(0xFFFFFFFF),
                    textPrimary = Color(0xFF110C1A), // Near black purple
                    textSecondary = Color(0xFF6B5E82),
                    border = Color(0xFFE5D5F5),
                    activeGlow = Color(0xFFDB2777)
                )
            }
        }
        2 -> {
            if (isDark) {
                BleAppTheme(
                    id = 2,
                    name = "Solar Thermal (Dark)",
                    primary = Color(0xFFF97316), // High Vis Orange
                    secondary = Color(0xFFFBBF24), // Golden Amber
                    accent = Color(0xFFEF4444), // Crimson Alert
                    background = Color(0xFF111111), // Midnight Carbon
                    cardBg = Color(0xFF1F1F1F), // Dark Accent Carbon Card
                    textPrimary = Color(0xFFF9FAFB),
                    textSecondary = Color(0xFF78716C),
                    border = Color(0xFF44403C),
                    activeGlow = Color(0xFFF97316)
                )
            } else {
                BleAppTheme(
                    id = 2,
                    name = "Solar Thermal (Light)",
                    primary = Color(0xFFEA580C), // Orange
                    secondary = Color(0xFFD97706), // Amber
                    accent = Color(0xFFDC2626), // Crimson
                    background = Color(0xFFFFFBEB), // Pale Amber
                    cardBg = Color(0xFFFFFFFF),
                    textPrimary = Color(0xFF1C1917), // Charcoal Accent
                    textSecondary = Color(0xFF78716C),
                    border = Color(0xFFF5DDC4),
                    activeGlow = Color(0xFFEA580C)
                )
            }
        }
        else -> {
            if (isDark) {
                BleAppTheme(
                    id = 0,
                    name = "Steel Industrial (Dark)",
                    primary = Color(0xFF10B981), // Emerald Green
                    secondary = Color(0xFF38BDF8), // Sky Blue
                    accent = Color(0xFF06B6D4), // Cyan
                    background = Color(0xFF0F172A), // Slate Dark
                    cardBg = Color(0xFF1E293B), // Card Slate
                    textPrimary = Color(0xFFF1F5F9),
                    textSecondary = Color(0xFF64748B),
                    border = Color(0xFF334155),
                    activeGlow = Color(0xFF10B981)
                )
            } else {
                BleAppTheme(
                    id = 0,
                    name = "Steel Industrial (Light)",
                    primary = Color(0xFF059669), // Emerald
                    secondary = Color(0xFF0284C7), // Blue
                    accent = Color(0xFF0891B2), // Cyan
                    background = Color(0xFFF8FAFC), // White Slate
                    cardBg = Color(0xFFFFFFFF),
                    textPrimary = Color(0xFF0F172A), // Slate Dark Text
                    textSecondary = Color(0xFF475569),
                    border = Color(0xFFE2E8F0),
                    activeGlow = Color(0xFF059669)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScannerAppContainer(
    viewModel: MeterScannerViewModel = viewModel()
) {
    val currentThemeIndex by viewModel.currentThemeIndex.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isThemeDark = when (themeMode) {
        1 -> true  // Force Dark
        2 -> false // Force Light
        else -> isSystemDark // System Default
    }
    val theme = getAppTheme(currentThemeIndex, isThemeDark)
    
    val selectedMeterDetail by viewModel.selectedMeterDetail.collectAsState()
    var activeTab by remember { mutableStateOf(0) }
    
    // Automatically switch tabs once any device card is initiated/connected
    LaunchedEffect(selectedMeterDetail) {
        if (selectedMeterDetail != null) {
            activeTab = 1
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = theme.cardBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Scan") },
                    label = { Text("BLE Scanner", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = theme.background,
                        selectedTextColor = theme.primary,
                        unselectedIconColor = theme.textSecondary,
                        unselectedTextColor = theme.textSecondary,
                        indicatorColor = theme.primary
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Details") },
                    label = { Text("Device Details", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = theme.background,
                        selectedTextColor = theme.primary,
                        unselectedIconColor = theme.textSecondary,
                        unselectedTextColor = theme.textSecondary,
                        indicatorColor = theme.primary
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Themes & Settings") },
                    label = { Text("Theme Lab", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = theme.background,
                        selectedTextColor = theme.primary,
                        unselectedIconColor = theme.textSecondary,
                        unselectedTextColor = theme.textSecondary,
                        indicatorColor = theme.primary
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> BleScannerTab(
                    viewModel = viewModel,
                    theme = theme,
                    onDeviceSelected = { activeTab = 1 }
                )
                1 -> DeviceDetailsScreen(
                    viewModel = viewModel,
                    theme = theme
                )
                2 -> ThemeSettingsTab(
                    viewModel = viewModel,
                    theme = theme
                )
            }
        }
    }
}

@Composable
fun BleScannerTab(
    viewModel: MeterScannerViewModel,
    theme: BleAppTheme,
    onDeviceSelected: () -> Unit
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredMeters by viewModel.discoveredMeters.collectAsState()
    val bluetoothState by viewModel.bluetoothState.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val showAllBleDevices by viewModel.showAllBleDevices.collectAsState()
    val scanFilterPrefix by viewModel.scanFilterPrefix.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredMeters = remember(discoveredMeters, searchQuery) {
        if (searchQuery.isBlank()) {
            discoveredMeters
        } else {
            discoveredMeters.filter { meter ->
                meter.name.contains(searchQuery, ignoreCase = true) ||
                meter.address.contains(searchQuery, ignoreCase = true) ||
                "%.1f".format(meter.telemetry.voltage).contains(searchQuery) ||
                "%.3f".format(meter.telemetry.activePowerKw).contains(searchQuery) ||
                "%.1f".format(meter.telemetry.cumulativeKwh).contains(searchQuery) ||
                (meter.telemetry.alertState?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }
    
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        viewModel.checkPermissionsState()
        if (granted) {
            viewModel.startScan()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // High visibility Header Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(theme.primary.copy(alpha = 0.25f), theme.background),
                        startY = 0f
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BLE UTILITY SCANNER",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.primary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Professional BLE Smart Meter Telemetry Scanner",
                        fontSize = 11.sp,
                        color = theme.textPrimary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }

                val context = LocalContext.current
                Button(
                    onClick = { viewModel.exportDataToCsv(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    modifier = Modifier.testTag("export_csv_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export CSV Data",
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "EXPORT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Bluetooth hardware status card (No antenna scanner references!)
        StateControlIndicator(
            isScanning = isScanning,
            bluetoothState = bluetoothState,
            permissionsGranted = permissionsGranted,
            metersCount = discoveredMeters.size,
            theme = theme,
            onGrantClicked = {
                permissionsLauncher.launch(viewModel.getRequiredPermissionsList().toTypedArray())
            }
        )

        // Configurable Scan Discovery & Filter Options Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "DISCOVERY & FILTER OPTIONS",
                    fontSize = 10.sp,
                    color = theme.accent,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show All BLE Advertisements",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = if (showAllBleDevices) "Listing all discovered BLE beacons nearby" else "Only listing matching smart utility devices",
                            fontSize = 10.sp,
                            color = theme.textSecondary
                        )
                    }
                    Switch(
                        checked = showAllBleDevices,
                        onCheckedChange = { viewModel.setShowAllBleDevices(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.accent,
                            checkedTrackColor = theme.accent.copy(alpha = 0.4f),
                            uncheckedThumbColor = theme.textSecondary,
                            uncheckedTrackColor = theme.border
                        )
                    )
                }

                if (!showAllBleDevices) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Device Name Prefix Filter",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = scanFilterPrefix,
                        onValueChange = { viewModel.setScanFilterPrefix(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = theme.textPrimary),
                        placeholder = { Text("e.g. M22615, Fitbit, Polar", fontSize = 13.sp, color = theme.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.border,
                            focusedContainerColor = theme.background,
                            unfocusedContainerColor = theme.background
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Real-time hardware scanner will only ask/look for BLE advert names beginning with this code.",
                        fontSize = 9.sp,
                        color = theme.textSecondary.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic Real-time Meter Search Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SEARCH DISCOVERED METERS",
                    fontSize = 10.sp,
                    color = theme.accent,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("meters_search_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = theme.textPrimary),
                    placeholder = { Text("Search by name, address, V, kW, Usage, alerts...", fontSize = 12.sp, color = theme.textSecondary) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp).testTag("clear_search_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search text",
                                    tint = theme.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.border,
                        focusedContainerColor = theme.background,
                        unfocusedContainerColor = theme.background
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Label matches
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showAllBleDevices) "FILTER MATCHES (ALL BEACONS)" else "FILTER MATCHES (CODE: ${scanFilterPrefix.uppercase()}*)",
                color = theme.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isScanning) theme.accent else theme.textSecondary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isScanning) "SCANNING ACTIVE" else "IDLE",
                    color = if (isScanning) theme.accent else theme.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = theme.accent,
                trackColor = theme.cardBg
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (showAllBleDevices) "Continuous Bluetooth scan in progress... Showing all beacons." else "Continuous Bluetooth scan in progress... Filtering nearby ${scanFilterPrefix} meters.",
                color = theme.accent,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Discovered lists
        if (discoveredMeters.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Canvas(modifier = Modifier.size(64.dp)) {
                        drawCircle(color = theme.border, radius = size.minDimension / 2, style = Stroke(width = 4f))
                        drawCircle(color = theme.accent.copy(alpha = 0.4f), radius = size.minDimension / 3)
                        drawLine(
                            color = theme.accent,
                            start = Offset(size.width * 0.3f, size.height * 0.5f),
                            end = Offset(size.width * 0.7f, size.height * 0.5f),
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Smart Meters Found",
                        color = theme.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ensure BLE is active and M22615 meters operate nearby.",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else if (filteredMeters.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon with zero results",
                        tint = theme.accent,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Match Found",
                        color = theme.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No metrics or addresses match \"$searchQuery\". Try another spelling, numbers, or clear search.",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { searchQuery = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reset Search", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("meters_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredMeters, key = { it.address }) { meter ->
                    MeterCard(
                        meter = meter,
                        theme = theme,
                        onClick = {
                            viewModel.selectMeter(meter)
                            onDeviceSelected()
                        }
                    )
                }
            }
        }

        // Scan Action Floating Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            ScanMainActionButton(
                isScanning = isScanning,
                theme = theme,
                onClick = {
                    if (!permissionsGranted) {
                        permissionsLauncher.launch(viewModel.getRequiredPermissionsList().toTypedArray())
                    } else {
                        if (isScanning) {
                            viewModel.stopScan()
                        } else {
                            viewModel.startScan()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun RssiHistoryLineChart(
    meter: BleMeter,
    theme: BleAppTheme,
    modifier: Modifier = Modifier
) {
    val history = meter.rssiHistory
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, theme.border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "REAL-TIME SIGNAL FLUCTUATION",
                        fontSize = 10.sp,
                        color = theme.accent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "RSSI Trend Over Time (dBm)",
                        fontSize = 12.sp,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                val currentRssi = meter.rssi
                val badgeColor = when {
                    currentRssi >= -65 -> theme.primary
                    currentRssi >= -85 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LATEST: $currentRssi dBm",
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            if (history.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(theme.background, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Waiting for data",
                            tint = theme.textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Awaiting RSSI telemetry telemetry...",
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("rssi_line_chart")
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val widthThreshold = size.width
                        val heightThreshold = size.height
                        
                        val rightPadding = 48.dp.toPx()
                        val chartWidth = widthThreshold - rightPadding
                        val chartHeight = heightThreshold
                        
                        val rssiValues = history.map { it.rssi }
                        val minRssiValue = (rssiValues.minOrNull() ?: -100).toFloat()
                        val maxRssiValue = (rssiValues.maxOrNull() ?: -30).toFloat()
                        
                        val span = maxRssiValue - minRssiValue
                        val minRssi = if (span == 0f) minRssiValue - 5f else minRssiValue - 3f
                        val maxRssi = if (span == 0f) maxRssiValue + 5f else maxRssiValue + 3f
                        
                        val yRange = maxRssi - minRssi
                        
                        val gridLines = listOf(-100f, -80f, -60f, -40f)
                        gridLines.forEach { dbmValue ->
                            if (dbmValue in minRssi..maxRssi) {
                                val normalizedY = 1.0f - ((dbmValue - minRssi) / yRange)
                                val gridY = normalizedY * chartHeight
                                
                                drawLine(
                                    color = theme.border.copy(alpha = 0.4f),
                                    start = Offset(0f, gridY),
                                    end = Offset(chartWidth, gridY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(8f, 8f),
                                        phase = 0f
                                    )
                                )
                                
                                val textPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb(
                                        (0.6f * 255).toInt(),
                                        (theme.textSecondary.red * 255).toInt(),
                                        (theme.textSecondary.green * 255).toInt(),
                                        (theme.textSecondary.blue * 255).toInt()
                                    )
                                    textSize = 8.dp.toPx()
                                    textAlign = android.graphics.Paint.Align.LEFT
                                    typeface = android.graphics.Typeface.MONOSPACE
                                    isAntiAlias = true
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    "${dbmValue.toInt()} dBm",
                                    chartWidth + 6.dp.toPx(),
                                    gridY + 3.dp.toPx(),
                                    textPaint
                                )
                            }
                        }
                        
                        val points = mutableListOf<Offset>()
                        val maxPointsToDraw = history.size
                        
                        history.forEachIndexed { index, snapshot ->
                            val x = (index.toFloat() / (maxPointsToDraw - 1)) * chartWidth
                            val normalizedY = 1.0f - ((snapshot.rssi - minRssi) / yRange)
                            val y = normalizedY * chartHeight
                            points.add(Offset(x, y))
                        }
                        
                        val fillPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points.first().x, chartHeight)
                            points.forEach { point ->
                                lineTo(point.x, point.y)
                            }
                            lineTo(points.last().x, chartHeight)
                            close()
                        }
                        
                        val chartColor = when {
                            meter.rssi >= -65 -> theme.primary
                            meter.rssi >= -85 -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                        
                        drawPath(
                            path = fillPath,
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    chartColor.copy(alpha = 0.25f),
                                    chartColor.copy(alpha = 0.00f)
                                ),
                                startY = 0f,
                                endY = chartHeight
                            )
                        )
                        
                        val strokePath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                val from = points[i - 1]
                                val to = points[i]
                                val controlX = (from.x + to.x) / 2f
                                cubicTo(
                                    controlX, from.y,
                                    controlX, to.y,
                                    to.x, to.y
                                )
                            }
                        }
                        
                        drawPath(
                            path = strokePath,
                            color = chartColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        
                        val latestPoint = points.last()
                        drawCircle(
                            color = chartColor.copy(alpha = 0.35f),
                            radius = 6.dp.toPx(),
                            center = latestPoint
                        )
                        drawCircle(
                            color = chartColor,
                            radius = 3.dp.toPx(),
                            center = latestPoint
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceDetailsScreen(
    viewModel: MeterScannerViewModel,
    theme: BleAppTheme
) {
    val selectedMeterDetail by viewModel.selectedMeterDetail.collectAsState()
    
    if (selectedMeterDetail == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(theme.cardBg),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(45.dp)) {
                        drawCircle(color = theme.textSecondary.copy(alpha = 0.4f), radius = size.minDimension / 2, style = Stroke(width = 4f))
                        drawLine(
                            color = theme.textSecondary,
                            start = Offset(size.width * 0.2f, size.height * 0.2f),
                            end = Offset(size.width * 0.8f, size.height * 0.8f),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "NO DEVICE LINKED",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please navigate to the BLE Scanner tab, select an available smart meter from the list, and open high precision parameters.",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
        return
    }

    val meter = selectedMeterDetail!!
    var detailsTab by remember { mutableStateOf(0) } // 0 = GATT, 1 = SERIAL CONSOLE
    val serialLogs by viewModel.serialLogs.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Device Name and Unified ID
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, theme.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ACTIVE LINKED DEVICE",
                        fontSize = 10.sp,
                        color = theme.accent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = meter.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "UNIQUE BLE ID: ${meter.address}",
                        fontSize = 11.sp,
                        color = theme.textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.background)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val stateColor = when (meter.connectionState) {
                            ConnectionState.DISCONNECTED -> theme.textSecondary
                            ConnectionState.CONNECTING -> Color(0xFFF59E0B)
                            ConnectionState.CONNECTED -> theme.activeGlow
                            ConnectionState.DISCOVERING_SERVICES -> theme.secondary
                            ConnectionState.SERVICES_DISCOVERED -> theme.accent
                            ConnectionState.ERROR -> Color(0xFFEF4444)
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(stateColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (meter.connectionState) {
                                ConnectionState.DISCONNECTED -> "DISCONNECTED"
                                ConnectionState.CONNECTING -> "ESTABLISHING HANDSHAKE..."
                                ConnectionState.CONNECTED -> "GATT CONSOLE ONLINE"
                                ConnectionState.DISCOVERING_SERVICES -> "DISCOVERING GATT SERVICES..."
                                ConnectionState.SERVICES_DISCOVERED -> "GATT ATTRIBUTES FULLY MAPED"
                                ConnectionState.ERROR -> "CONNECTION FAILED"
                            },
                            color = stateColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.background)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Device RSSI",
                                tint = theme.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LIVE SIGNAL FEED:",
                                color = theme.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        val animatedDetailRssi by animateIntAsState(
                            targetValue = meter.rssi,
                            animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
                            label = "detailRssiAnim"
                        )
                        val rssiPercent = ((animatedDetailRssi + 100).coerceIn(0, 70) / 70f)
                        val rssiColor = when {
                            animatedDetailRssi >= -65 -> theme.primary
                            animatedDetailRssi >= -85 -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${animatedDetailRssi} dBm",
                                color = rssiColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("detail_rssi_dBm")
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.padding(bottom = 1.dp)
                            ) {
                                for (i in 0..3) {
                                    val active = rssiPercent >= (i / 4f)
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height((3 + i * 2.5).dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(if (active) rssiColor else theme.border)
                                    )
                                }
                            }
                        }
                    }

                    if (meter.connectionError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Error: ${meter.connectionError}",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Live RSSI History Fluctuation Line Chart
        item {
            RssiHistoryLineChart(meter = meter, theme = theme)
        }

        // Action Buttons: Connect / Disconnect
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (meter.connectionState == ConnectionState.DISCONNECTED || meter.connectionState == ConnectionState.ERROR) {
                    Button(
                        onClick = { viewModel.connectToMeter(meter) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CONNECT TO DEVICE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = theme.background)
                    }
                } else {
                    Button(
                        onClick = { viewModel.disconnectMeter(meter.address) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("DISCONNECT FROM DEVICE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        // Mode Navigation Subtab Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("subtabs_row"),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, theme.border)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (detailsTab == 0) theme.primary else Color.Transparent)
                            .clickable { detailsTab = 0 }
                            .padding(vertical = 10.dp)
                            .testTag("metrics_subtab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GATT DIAGNOSTICS", 
                            color = if (detailsTab == 0) theme.background else theme.textPrimary, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (detailsTab == 1) theme.primary else Color.Transparent)
                            .clickable { detailsTab = 1 }
                            .padding(vertical = 10.dp)
                            .testTag("serial_subtab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (detailsTab == 1) theme.background else theme.accent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SERIAL CONSOLE",
                                color = if (detailsTab == 1) theme.background else theme.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        if (detailsTab == 0) {
            // Live stats fields
            item {
                Text(
                    text = "LIVE SMART GRID METRICS (COMS STREAM)",
                    fontSize = 10.sp,
                    color = theme.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            item {
                val gridItems = listOf(
                    TelemetryField("RMS Line Voltage", "%.1f V".format(meter.telemetry.voltage), MeterIconType.VOLTAGE),
                    TelemetryField("RMS Node Current", "%.2f A".format(meter.telemetry.current), MeterIconType.CURRENT),
                    TelemetryField("Active Load Demand", "%.3f kW".format(meter.telemetry.activePowerKw), MeterIconType.POWER),
                    TelemetryField("Grid Frequency", "%.3f Hz".format(meter.telemetry.gridFrequencyHz), MeterIconType.FREQUENCY),
                    TelemetryField("Sensor Battery", "${meter.telemetry.batteryPercentage}%", MeterIconType.BATTERY),
                    TelemetryField("Cumulative Energy", "%.1f kWh".format(meter.telemetry.cumulativeKwh), MeterIconType.KWH)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in gridItems.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryMiniCard(
                                modifier = Modifier.weight(1f),
                                field = gridItems[i],
                                theme = theme,
                                tint = if (gridItems[i].iconType == MeterIconType.BATTERY) {
                                    if (meter.telemetry.batteryPercentage > 50) theme.primary else Color(0xFFEF4444)
                                } else theme.secondary
                            )
                            if (i + 1 < gridItems.size) {
                                TelemetryMiniCard(
                                    modifier = Modifier.weight(1f),
                                    field = gridItems[i + 1],
                                    theme = theme,
                                    tint = theme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Available GATT services subtree
            item {
                Text(
                    text = "AVAILABLE GATT SERVICES TREE",
                    fontSize = 10.sp,
                    color = theme.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (meter.connectionState == ConnectionState.SERVICES_DISCOVERED && meter.services.isNotEmpty()) {
                items(meter.services) { service ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, theme.border)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Build, contentDescription = "Service", tint = theme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = service.name,
                                    color = theme.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "UUID: ${service.uuid}",
                                color = theme.textSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = theme.border)
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                service.characteristics.forEach { char ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.background)
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = char.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.textPrimary
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(theme.primary.copy(alpha = 0.15f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = char.properties,
                                                    color = theme.primary,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            text = "UUID: ${char.uuid}",
                                            color = theme.textSecondary,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Value: ${char.value}",
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = theme.accent,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (char.properties.contains("READ")) {
                                                IconButton(
                                                    onClick = { viewModel.readCharacteristic(meter.address, service.uuid, char.uuid) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Refresh,
                                                        contentDescription = "Read",
                                                        tint = theme.secondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.cardBg)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (meter.connectionState == ConnectionState.CONNECTING || meter.connectionState == ConnectionState.DISCOVERING_SERVICES) {
                                "Consulting controller and scanning available GATT records..."
                            } else {
                                "Device currently target offline. Establish real-time connection above to acquire active GATT diagnostics."
                            },
                            color = theme.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            // RENDER SERIAL TERMINAL CONSOLE
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070B14))
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    // Terminal header bar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "COM1 SERIAL_MONITOR",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        IconButton(
                            onClick = { viewModel.clearSerialLogs() },
                            modifier = Modifier.size(24.dp).testTag("clear_terminal_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Console",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Terminal Screen contents box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color(0xFF03050A))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        if (serialLogs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No serial stream established.\n\nConnect to device to map diagnostics and initialize automatic serial output telemetry frames.",
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }
                        } else {
                            val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                            
                            LaunchedEffect(serialLogs.size) {
                                if (serialLogs.isNotEmpty()) {
                                    lazyListState.scrollToItem(serialLogs.size - 1)
                                }
                            }
                            
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(serialLogs) { log ->
                                    val (color, prefix) = when (log.direction) {
                                        "TX" -> Pair(theme.primary, "➔ [TX] ")
                                        "RX" -> Pair(theme.accent, "◀ [RX] ")
                                        "DECODE" -> Pair(theme.activeGlow, "✔ [INFO] ")
                                        "SUCCESS" -> Pair(theme.activeGlow, "✚ [OK] ")
                                        "ERROR" -> Pair(Color(0xFFEF4444), "✖ [ERR] ")
                                        else -> Pair(theme.textSecondary, "ℹ [SYS] ")
                                    }
                                    
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "[${log.timestamp}] ",
                                            color = Color(0xFF475569),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = prefix + log.text,
                                            color = color,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSettingsTab(
    viewModel: MeterScannerViewModel,
    theme: BleAppTheme
) {
    val currentThemeIndex by viewModel.currentThemeIndex.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("theme_preset_card"),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SELECT BRAND DESIGN SYSTEM (THEME)",
                    fontSize = 10.sp,
                    color = theme.accent,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val themes = listOf(
                        Triple(0, "Steel Industrial 💻", "Pro corporate slate theme with emerald values"),
                        Triple(1, "Neon Cyberpunk 🌌", "Vibrant ambient violet layout with glowing magenta"),
                        Triple(2, "Solar Thermal ☀️", "Warm carbon structure with sunset orange accents")
                    )

                    themes.forEach { (index, name, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (currentThemeIndex == index) theme.background else Color.Transparent)
                                .clickable { viewModel.changeTheme(index) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentThemeIndex == index,
                                onClick = { viewModel.changeTheme(index) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = theme.primary,
                                    unselectedColor = theme.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentThemeIndex == index) theme.primary else theme.textPrimary
                                )
                                Text(
                                    text = desc,
                                    fontSize = 10.sp,
                                    color = theme.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dark/Light Mode Switch Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("theme_mode_card"),
            colors = CardDefaults.cardColors(containerColor = theme.cardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SELECT THEME MODE (LIGHT / DARK)",
                    fontSize = 10.sp,
                    color = theme.accent,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf(
                        Triple(0, "System Default ⚙️", "Matches the current system light/dark theme automatically"),
                        Triple(1, "Dark Mode 🌙", "Force battery-saving OLED friendly dark presentation"),
                        Triple(2, "Light Mode ☀️", "Force clean, high contrast daytime lighting styling")
                    )

                    modes.forEach { (mode, name, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (themeMode == mode) theme.background else Color.Transparent)
                                .clickable { viewModel.changeThemeMode(mode) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.changeThemeMode(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = theme.primary,
                                    unselectedColor = theme.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (themeMode == mode) theme.primary else theme.textPrimary
                                )
                                Text(
                                    text = desc,
                                    fontSize = 10.sp,
                                    color = theme.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StateControlIndicator(
    isScanning: Boolean,
    bluetoothState: String,
    permissionsGranted: Boolean,
    metersCount: Int,
    theme: BleAppTheme,
    onGrantClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, theme.border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BLUETOOTH CHIP STATUS",
                        fontSize = 10.sp,
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (bluetoothState) {
                            "ON" -> "Bluetooth Receiver Active"
                            "OFF" -> "Bluetooth Controller Disabled"
                            "NOT_SUPPORTED" -> "BLE Not Supported on Device"
                            else -> "Initializing Controller..."
                        },
                        color = when (bluetoothState) {
                            "ON" -> theme.primary
                            else -> Color(0xFFF87171)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (bluetoothState == "ON") theme.primary.copy(alpha = 0.2f) else Color(0xFF7F1D1D).copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.5f, size.height * 0.15f)
                            lineTo(size.width * 0.5f, size.height * 0.85f)
                            lineTo(size.width * 0.75f, size.height * 0.67f)
                            lineTo(size.width * 0.25f, size.height * 0.33f)
                            lineTo(size.width * 0.75f, size.height * 0.33f)
                            lineTo(size.width * 0.5f, size.height * 0.15f)
                        }
                        drawPath(
                            path = path,
                            color = if (bluetoothState == "ON") theme.primary else Color(0xFFF87171),
                            style = Stroke(width = 3f, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            if (!permissionsGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = theme.border)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF7F1D1D).copy(alpha = 0.3f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PERMISSIONS REQUIRED",
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "BLE Scanning requires location & scan permissions.",
                            color = Color(0xFFFECACA),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Button(
                        onClick = onGrantClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ScanMainActionButton(
    isScanning: Boolean,
    theme: BleAppTheme,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isScanning) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .scale(pulseScale)
            .height(50.dp)
            .fillMaxWidth()
            .testTag("scan_button"),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isScanning) Color(0xFFEF4444) else theme.primary
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("HALT SYSTEM BLE SCAN", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
            } else {
                Canvas(modifier = Modifier.size(20.dp)) {
                    drawCircle(color = theme.background, radius = size.minDimension / 3, style = Stroke(width = 3f))
                    drawLine(
                        color = theme.background,
                        start = Offset(size.width * 0.55f, size.height * 0.55f),
                        end = Offset(size.width * 0.9f, size.height * 0.9f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("RUN LIVE BLUETOOTH SCAN", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.background)
            }
        }
    }
}

@Composable
fun RssiSignalWidget(
    rssi: Int,
    rssiPercent: Float,
    rssiColor: Color,
    theme: BleAppTheme
) {
    val animatedRssi by animateIntAsState(
        targetValue = rssi,
        animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
        label = "rssiAnim"
    )
    val animatedPercent = ((animatedRssi + 100).coerceIn(0, 70) / 70f)
    val animatedColor = when {
        animatedRssi >= -65 -> theme.primary
        animatedRssi >= -85 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rssiPulse")
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.background.copy(alpha = 0.5f))
            .border(1.dp, theme.border.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("rssi_signal_widget_${rssi}")
    ) {
        // RADAR / RIPPLE WAVE CANVAS
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f
                
                // Draw a pulsing beacon point at the center
                drawCircle(
                    color = animatedColor,
                    radius = 3.dp.toPx(),
                    center = center
                )
                
                // Draw expanding ring waves representing live radio telemetry broadcast
                val numWaves = 2
                for (i in 0 until numWaves) {
                    val progress = (waveProgress + i / numWaves.toFloat()) % 1f
                    val currentRadius = progress * maxRadius
                    val opacity = (1f - progress) * 0.4f
                    drawCircle(
                        color = animatedColor,
                        radius = currentRadius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                        alpha = opacity
                    )
                }
            }
        }
        
        Column {
            Text(
                text = "RSSI SIGNAL",
                fontSize = 7.sp,
                color = theme.textSecondary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                lineHeight = 8.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$animatedRssi",
                    color = animatedColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "dBm",
                    color = theme.textSecondary,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                // Segment block indicators (WiFi bar visual)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(bottom = 1.dp)
                ) {
                    for (i in 0..3) {
                        val active = animatedPercent >= (i / 4f)
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height((3 + i * 2.5).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (active) animatedColor else theme.border)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MeterCard(
    meter: BleMeter,
    theme: BleAppTheme,
    onClick: () -> Unit
) {
    val rssiPercent = ((meter.rssi + 100).coerceIn(0, 70) / 70f)
    val rssiColor = when {
        meter.rssi >= -65 -> theme.primary
        meter.rssi >= -85 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("meter_card_${meter.address}"),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, theme.border)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(30.dp)) {
                        drawCircle(color = rssiColor, radius = size.minDimension / 2, style = Stroke(width = 3f))
                        drawCircle(color = rssiColor, radius = 4f)
                        drawLine(
                            color = rssiColor,
                            start = Offset(size.width * 0.5f, size.height * 0.5f),
                            end = Offset(size.width * 0.8f, size.height * 0.3f),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = meter.name,
                            color = theme.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        val dotColor = when (meter.connectionState) {
                            ConnectionState.DISCONNECTED -> theme.textSecondary
                            ConnectionState.CONNECTING -> Color(0xFFF59E0B)
                            ConnectionState.CONNECTED -> theme.primary
                            ConnectionState.DISCOVERING_SERVICES -> theme.accent
                            ConnectionState.SERVICES_DISCOVERED -> theme.secondary
                            ConnectionState.ERROR -> Color(0xFFEF4444)
                        }
                        
                        val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
                        val alphaScale by if (meter.connectionState == ConnectionState.CONNECTING || meter.connectionState == ConnectionState.DISCOVERING_SERVICES) {
                            infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )
                        } else {
                            remember { mutableStateOf(1.0f) }
                        }

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor.copy(alpha = alphaScale))
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (meter.connectionState) {
                                ConnectionState.DISCONNECTED -> "OFFLINE"
                                ConnectionState.CONNECTING -> "LINKING..."
                                ConnectionState.CONNECTED -> "ONLINE"
                                ConnectionState.DISCOVERING_SERVICES -> "READING..."
                                ConnectionState.SERVICES_DISCOVERED -> "READY"
                                ConnectionState.ERROR -> "FAULT"
                            },
                            color = dotColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        if (meter.isSimulated) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(theme.primary.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("SIM", color = theme.primary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ID: ${meter.address}",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Estimated distance: %.1f meters".format(meter.estimatedDistance),
                        color = theme.textSecondary.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    RssiSignalWidget(
                        rssi = meter.rssi,
                        rssiPercent = rssiPercent,
                        rssiColor = rssiColor,
                        theme = theme
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val lastSeenElapsed = System.currentTimeMillis() - meter.lastSeen
                    val isLive = lastSeenElapsed < 2500

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isLive) theme.activeGlow.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(theme.activeGlow)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "LIVE",
                                color = theme.activeGlow,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                "CONNECTED",
                                color = theme.textSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.background)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voltage & Unit (V)
                Column {
                    Text(
                        text = "VOLTAGE",
                        fontSize = 8.sp,
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "%.1f V".format(meter.telemetry.voltage),
                        color = theme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Current & Unit (A/Amps)
                Column {
                    Text(
                        text = "AMPS",
                        fontSize = 8.sp,
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "%.2f A".format(meter.telemetry.current),
                        color = theme.secondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Active Power Load & Unit (kW)
                Column {
                    Text(
                        text = "DEMAND",
                        fontSize = 8.sp,
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "%.3f kW".format(meter.telemetry.activePowerKw),
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Cumulative Usage & Unit (kWh)
                Column {
                    Text(
                        text = "USAGE",
                        fontSize = 8.sp,
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "%.1f kWh".format(meter.telemetry.cumulativeKwh),
                        color = theme.activeGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Last Seen Time (HH:mm:ss)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SYNC TIME",
                        fontSize = 8.sp,
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
                    val formattedTime = timeFormat.format(java.util.Date(meter.lastSeen))
                    Text(
                        text = formattedTime,
                        color = theme.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (meter.connectionState == ConnectionState.CONNECTING || 
                meter.connectionState == ConnectionState.DISCOVERING_SERVICES) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = if (meter.connectionState == ConnectionState.CONNECTING) Color(0xFFF59E0B) else theme.accent,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
fun TelemetryMiniCard(
    modifier: Modifier = Modifier,
    field: TelemetryField,
    theme: BleAppTheme,
    tint: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, theme.border)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                when (field.iconType) {
                    MeterIconType.VOLTAGE -> {
                        val path = Path().apply {
                            moveTo(size.width * 0.55f, size.height * 0.1f)
                            lineTo(size.width * 0.25f, size.height * 0.55f)
                            lineTo(size.width * 0.5f, size.height * 0.55f)
                            lineTo(size.width * 0.45f, size.height * 0.9f)
                            lineTo(size.width * 0.75f, size.height * 0.45f)
                            lineTo(size.width * 0.5f, size.height * 0.45f)
                            close()
                        }
                        drawPath(path = path, color = tint)
                    }
                    MeterIconType.CURRENT -> {
                        drawCircle(color = tint, radius = size.minDimension / 3, style = Stroke(width = 3f))
                        drawCircle(color = tint, radius = 4f, center = Offset(size.width * 0.5f, size.height * 0.5f))
                    }
                    MeterIconType.POWER -> {
                        drawCircle(color = tint, radius = size.minDimension / 2.5f, style = Stroke(width = 3f))
                        drawLine(
                            color = tint,
                            start = Offset(size.width * 0.5f, size.height * 0.5f),
                            end = Offset(size.width * 0.8f, size.height * 0.2f),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }
                    MeterIconType.FREQUENCY -> {
                        val path = Path().apply {
                            moveTo(0f, size.height * 0.5f)
                            quadraticTo(size.width * 0.25f, size.height * 0.1f, size.width * 0.5f, size.height * 0.5f)
                            quadraticTo(size.width * 0.75f, size.height * 0.9f, size.width, size.height * 0.5f)
                        }
                        drawPath(path = path, color = tint, style = Stroke(width = 4f, cap = StrokeCap.Round))
                    }
                    MeterIconType.BATTERY -> {
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(size.width * 0.1f, size.height * 0.25f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.65f, size.height * 0.5f),
                            style = Stroke(width = 3f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                        )
                        drawRect(
                            color = tint,
                            topLeft = Offset(size.width * 0.75f, size.height * 0.4f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.15f, size.height * 0.2f)
                        )
                    }
                    MeterIconType.KWH -> {
                        drawCircle(color = tint, radius = size.minDimension / 3)
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = field.title, color = theme.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = field.value,
                    color = theme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
