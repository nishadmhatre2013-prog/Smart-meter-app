package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.random.Random

// Connection state for smart meters
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    SERVICES_DISCOVERED,
    ERROR
}

data class SerialLogLine(
    val timestamp: String,
    val direction: String, // "TX", "RX", "DECODE", "INFO", "SUCCESS", "ERROR"
    val text: String
)

data class BleCharacteristicInfo(
    val uuid: String,
    val name: String,
    val value: String,
    val properties: String
)

data class BleServiceInfo(
    val uuid: String,
    val name: String,
    val characteristics: List<BleCharacteristicInfo>
)

data class RssiSnapshot(
    val timestamp: Long,
    val rssi: Int
)

enum class AccountTier {
    NORMAL, PREMIUM
}

data class UserAccount(
    val email: String,
    val tier: AccountTier
)

// Data classes representing smart meters discovered
data class BleMeter(
    val name: String,
    val address: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = false,
    val telemetry: MeterTelemetry = MeterTelemetry.generateRandom(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val services: List<BleServiceInfo> = emptyList(),
    val connectionError: String? = null,
    val rssiHistory: List<RssiSnapshot> = listOf(RssiSnapshot(System.currentTimeMillis(), rssi))
) {
    val estimatedDistance: Double
        get() = 10.0.pow((-69.0 - rssi) / (10.0 * 2.0))

    fun withNewRssi(newRssi: Int, timestamp: Long = System.currentTimeMillis()): BleMeter {
        val updatedHistory = (rssiHistory + RssiSnapshot(timestamp, newRssi)).takeLast(100)
        return this.copy(rssi = newRssi, lastSeen = timestamp, rssiHistory = updatedHistory)
    }
}

data class MeterTelemetry(
    val voltage: Double,
    val current: Double,
    val activePowerKw: Double,
    val gridFrequencyHz: Double,
    val batteryPercentage: Int,
    val cumulativeKwh: Double,
    val alertState: String? = null
) {
    companion object {
        fun generateRandom(): MeterTelemetry {
            val volt = 220.0 + Random.nextDouble(-5.0, 5.0)
            val curr = Random.nextDouble(1.0, 15.0)
            val power = (volt * curr) / 1000.0
            val freq = 50.0 + Random.nextDouble(-0.1, 0.1)
            val batt = Random.nextInt(75, 100)
            val kwh = 78190.0 + Random.nextDouble(1.0, 10.0)
            val alert = if (Random.nextDouble() > 0.95) "High Load Warning" else null
            return MeterTelemetry(volt, curr, power, freq, batt, kwh, alert)
        }
    }
}

enum class MeterIconType {
    VOLTAGE, CURRENT, POWER, FREQUENCY, BATTERY, KWH
}

data class TelemetryField(
    val title: String,
    val value: String,
    val iconType: MeterIconType
)

class MeterScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    private val prefs = context.getSharedPreferences("ble_scanner_prefs", Context.MODE_PRIVATE)

    private val _discoveredMeters = MutableStateFlow<List<BleMeter>>(emptyList())
    val discoveredMeters: StateFlow<List<BleMeter>> = combine(_discoveredMeters, _currentUser) { meters, user ->
        if (user?.tier == AccountTier.PREMIUM) {
            meters
        } else {
            meters.take(1)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun login(email: String, tier: AccountTier) {
        val account = UserAccount(email, tier)
        _currentUser.value = account
        prefs.edit().apply {
            putString("user_email", email)
            putString("user_tier", tier.name)
            apply()
        }
        appendSerialLog("INFO", "Account logged in: $email (${tier.name}_TIER)")
    }

    fun registerUser(email: String, password: String, tier: AccountTier): Boolean {
        val e = email.trim().lowercase()
        val p = password.trim()
        if (e.isBlank() || p.isBlank()) return false
        prefs.edit().apply {
            putString("reg_pwd_$e", p)
            putString("reg_tier_$e", tier.name)
            apply()
        }
        android.util.Log.d("AuthDebug", "Registered $e tier ${tier.name}")
        appendSerialLog("INFO", "Registered user: $e as ${tier.name}")
        return true
    }

    fun verifyAndLogin(email: String, password: String): Boolean {
        android.util.Log.d("AuthDebug", "verifyAndLogin called: '$email'/'$password'")
        val e = email.trim().lowercase()
        val p = password.trim()
        if (e.isBlank() || p.isBlank()) return false
        
        // Admin check
        if (e == "admin" && p == "admin") {
            login("admin@system", AccountTier.PREMIUM)
            return true
        }
        
        // Look up by trimmed and lowercase first
        var storedPassword = prefs.getString("reg_pwd_$e", null)
        var storedTierStr = prefs.getString("reg_tier_$e", null)
        
        // Try original or trimmed just in case it was stored that way before this update
        if (storedPassword == null) {
            val original = email
            storedPassword = prefs.getString("reg_pwd_$original", null)
            storedTierStr = prefs.getString("reg_tier_$original", null)
        }
        if (storedPassword == null) {
            val trimmed = email.trim()
            storedPassword = prefs.getString("reg_pwd_$trimmed", null)
            storedTierStr = prefs.getString("reg_tier_$trimmed", null)
        }
        
        android.util.Log.d("AuthDebug", "Verifying $e: pwdMatch=${storedPassword == p}, tier=$storedTierStr")
        
        if (storedPassword != null && storedPassword == p && storedTierStr != null) {
            val tier = try {
                AccountTier.valueOf(storedTierStr)
            } catch (ex: Exception) {
                AccountTier.NORMAL
            }
            login(e, tier)
            return true
        }
        
        // Fallback or default profiles
        if (e == "premium@smartmeter.com" && p == "password123") {
            login("premium@smartmeter.com", AccountTier.PREMIUM)
            return true
        }
        if (e == "normal@smartmeter.com" && p == "password123") {
            login("normal@smartmeter.com", AccountTier.NORMAL)
            return true
        }
        
        return false
    }

    fun isEmailRegistered(email: String): Boolean {
        val e = email.trim().lowercase()
        val original = email
        val trimmed = email.trim()
        return prefs.contains("reg_pwd_$e") || 
               prefs.contains("reg_pwd_$trimmed") || 
               prefs.contains("reg_pwd_$original")
    }

    fun logout() {
        _currentUser.value = null
        prefs.edit().apply {
            remove("user_email")
            remove("user_tier")
            apply()
        }
        appendSerialLog("INFO", "Account logged out. Guest tier active.")
    }

    private val _bluetoothState = MutableStateFlow("UNKNOWN") // ON, OFF, NOT_SUPPORTED, UNKNOWN
    val bluetoothState: StateFlow<String> = _bluetoothState.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _selectedMeterDetail = MutableStateFlow<BleMeter?>(null)
    val selectedMeterDetail: StateFlow<BleMeter?> = _selectedMeterDetail.asStateFlow()

    private val _recentlyViewedMeters = MutableStateFlow<List<BleMeter>>(emptyList())
    val recentlyViewedMeters: StateFlow<List<BleMeter>> = _recentlyViewedMeters.asStateFlow()

    fun addToRecentlyViewed(meter: BleMeter) {
        val current = _recentlyViewedMeters.value.toMutableList()
        current.removeAll { it.address == meter.address }
        current.add(0, meter)
        _recentlyViewedMeters.value = current.take(6) // Keep top 6 recently viewed
    }

    fun clearRecentlyViewed() {
        _recentlyViewedMeters.value = emptyList()
    }

    private val _currentThemeIndex = MutableStateFlow(0)
    val currentThemeIndex: StateFlow<Int> = _currentThemeIndex.asStateFlow()

    private val _themeMode = MutableStateFlow(0) // 0 = System Default, 1 = Dark Mode, 2 = Light Mode
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _scanFilterPrefix = MutableStateFlow("M22615")
    val scanFilterPrefix: StateFlow<String> = _scanFilterPrefix.asStateFlow()

    private val _showAllBleDevices = MutableStateFlow(false)
    val showAllBleDevices: StateFlow<Boolean> = _showAllBleDevices.asStateFlow()

    private val _serialLogs = MutableStateFlow<List<SerialLogLine>>(emptyList())
    val serialLogs: StateFlow<List<SerialLogLine>> = _serialLogs.asStateFlow()

    fun appendSerialLog(direction: String, text: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        val time = sdf.format(java.util.Date())
        val newLog = SerialLogLine(time, direction, text)
        _serialLogs.value = (_serialLogs.value + newLog).takeLast(100)
    }

    fun clearSerialLogs() {
        _serialLogs.value = emptyList()
    }

    private val _connectionTimeoutJobs = mutableMapOf<String, Job>()
    private var serialStreamJob: Job? = null

    private fun startSerialConsoleGenerator(address: String) {
        serialStreamJob?.cancel()
        clearSerialLogs()
        appendSerialLog("INFO", "GATT Session initiated on port COM1.")
        appendSerialLog("INFO", "Registering notification listeners...")
        appendSerialLog("SUCCESS", "Serial Port Virtual Transceiver online.")
        
        serialStreamJob = viewModelScope.launch {
            while (true) {
                delay(1800)
                val currentMeters = _discoveredMeters.value
                val meter = currentMeters.find { it.address == address }
                if (meter == null) {
                    appendSerialLog("ERROR", "Connection lost: device handle not found.")
                    break
                }
                
                // Simulate Slight Telemetry Noise
                val currentTel = meter.telemetry
                val voltNoise = Random.nextDouble(-0.5, 0.5)
                val currNoise = Random.nextDouble(-0.15, 0.15)
                val newVolt = (currentTel.voltage + voltNoise).coerceIn(215.0, 240.0)
                val newCurr = (currentTel.current + currNoise).coerceIn(0.5, 20.0)
                val newPower = (newVolt * newCurr) / 1000.0
                val newFreq = (currentTel.gridFrequencyHz + Random.nextDouble(-0.005, 0.005)).coerceIn(49.9, 50.1)
                
                val jitter = Random.nextInt(-2, 3)
                val newRssi = (meter.rssi + jitter).coerceIn(-100, -30)
                
                val updatedMeter = meter.withNewRssi(newRssi).copy(
                    telemetry = currentTel.copy(
                        voltage = newVolt,
                        current = newCurr,
                        activePowerKw = newPower,
                        gridFrequencyHz = newFreq
                    )
                )
                
                // Update lists
                _discoveredMeters.value = _discoveredMeters.value.map {
                    if (it.address == address) updatedMeter else it
                }
                if (_selectedMeterDetail.value?.address == address) {
                    _selectedMeterDetail.value = updatedMeter
                }

                // Transmit polling query
                val hexAddr = address.replace(":", "")
                appendSerialLog("TX", "AT+POLL=0x$hexAddr")
                
                delay(350)
                
                // Receive ASCII serial telemetry package
                val pkg = "${'$'}SM-M226;ADDR=$address;V=${"%.1f".format(newVolt)};A=${"%.2f".format(newCurr)};P=${"%.3f".format(newPower)};F=${"%.2f".format(newFreq)}*${Random.nextInt(10, 99)}"
                appendSerialLog("RX", pkg)
                
                delay(250)
                
                // Decode acknowledgement
                appendSerialLog("DECODE", "V_RMS=${"%.1f".format(newVolt)}V, I_RMS=${"%.2f".format(newCurr)}A -> Load=${"%.3f".format(newPower)}kW")
            }
        }
    }

    private fun stopSerialConsoleGenerator() {
        serialStreamJob?.cancel()
        serialStreamJob = null
        appendSerialLog("INFO", "Serial Port Transceiver offline.")
    }

    private var scanRssiJob: Job? = null

    fun toggleSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        if (enabled) {
            val current = _discoveredMeters.value.toMutableList()
            val simulatedAddresses = listOf("DE:AD:BE:EF:01:02", "FA:CE:B0:0C:03:04", "CA:FE:BA:BE:05:06")
            val names = listOf("M22615-A (Smart Meter)", "M22615-B (Gas Transceiver)", "M22615-C (Grid Volts)")
            val bases = listOf(-55, -72, -87)
            
            simulatedAddresses.forEachIndexed { idx, addr ->
                if (!current.any { it.address == addr }) {
                    current.add(BleMeter(
                        name = names[idx],
                        address = addr,
                        rssi = bases[idx],
                        isSimulated = true,
                        telemetry = MeterTelemetry.generateRandom()
                    ))
                }
            }
            _discoveredMeters.value = current.sortedByDescending { it.rssi }
            appendSerialLog("INFO", "Simulation Engine initialized with ${simulatedAddresses.size} virtual meters.")
        } else {
            val current = _discoveredMeters.value.filter { !it.isSimulated }
            _discoveredMeters.value = current
            appendSerialLog("INFO", "Simulation Engine deactivated.")
        }
    }

    private fun startScanRssiUpdates() {
        scanRssiJob?.cancel()
        scanRssiJob = viewModelScope.launch {
            while (true) {
                delay(1500)
                val isSim = _isSimulationMode.value
                val isScan = _isScanning.value
                if ((isScan || isSim) && _discoveredMeters.value.isNotEmpty()) {
                    _discoveredMeters.value = _discoveredMeters.value.map { meter ->
                        if ((meter.isSimulated && isSim) || isScan) {
                            val jitter = Random.nextInt(-2, 3)
                            val newRssi = (meter.rssi + jitter).coerceIn(-100, -30)
                            meter.withNewRssi(newRssi)
                        } else {
                            meter
                        }
                    }.sortedByDescending { it.rssi }
                    
                    val currentSelect = _selectedMeterDetail.value
                    if (currentSelect != null) {
                        _selectedMeterDetail.value = _discoveredMeters.value.find { it.address == currentSelect.address }
                    }
                }
            }
        }
    }

    private fun stopScanRssiUpdates() {
        scanRssiJob?.cancel()
        scanRssiJob = null
    }

    fun changeTheme(index: Int) {
        _currentThemeIndex.value = index % 3
    }

    fun changeThemeMode(mode: Int) {
        _themeMode.value = mode % 3
    }

    fun setScanFilterPrefix(prefix: String) {
        _scanFilterPrefix.value = prefix
    }

    fun setShowAllBleDevices(showAll: Boolean) {
        _showAllBleDevices.value = showAll
    }

    fun exportDataToCsv(context: Context) {
        viewModelScope.launch {
            try {
                val header = "Record Timestamp,Meter Name,MAC Address,RSSI (dBm),Voltage (V),Current (A/Amps),Active Power (kW),Grid Frequency (Hz),Battery (%),Cumulative Usage (kWh),Connection State,Alert State,Estimated Distance (m)"
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val rows = mutableListOf<String>()

                _discoveredMeters.value.forEach { meter ->
                    meter.rssiHistory.forEach { snapshot ->
                        val timeStr = sdf.format(Date(snapshot.timestamp))
                        val row = listOf(
                            timeStr,
                            meter.name.replace(",", " "),
                            meter.address,
                            snapshot.rssi.toString(),
                            String.format(Locale.US, "%.2f", meter.telemetry.voltage),
                            String.format(Locale.US, "%.3f", meter.telemetry.current),
                            String.format(Locale.US, "%.4f", meter.telemetry.activePowerKw),
                            String.format(Locale.US, "%.2f", meter.telemetry.gridFrequencyHz),
                            meter.telemetry.batteryPercentage.toString(),
                            String.format(Locale.US, "%.3f", meter.telemetry.cumulativeKwh),
                            meter.connectionState.name,
                            (meter.telemetry.alertState ?: "None").replace(",", " "),
                            String.format(Locale.US, "%.2f", 10.0.pow((-69.0 - snapshot.rssi) / 20.0))
                        ).joinToString(",")
                        rows.add(row)
                    }
                }

                if (rows.isEmpty()) {
                    rows.add("${sdf.format(Date())},No meter telemetry captured yet,,0,0.0,0.0,0.0,0.0,0,0.0,DISCONNECTED,None,0.0")
                }

                val csvContent = (listOf(header) + rows).joinToString("\n")
                val exportDir = File(context.cacheDir, "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                val file = File(exportDir, "meter_telemetry_history_export.csv")
                file.writeText(csvContent)

                val fileUri = FileProvider.getUriForFile(
                    context,
                    "com.example.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Smart Meter Telemetry & RSSI History Export")
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Share Meter CSV Analytics").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var connectedRssiPollingJob: Job? = null

    @SuppressLint("MissingPermission")
    private fun startConnectedRssiPolling() {
        if (connectedRssiPollingJob != null && connectedRssiPollingJob?.isActive == true) return
        connectedRssiPollingJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                _gattConnections.forEach { (address, gatt) ->
                    try {
                        gatt.readRemoteRssi()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun updateMeterRssi(address: String, rssi: Int) {
        val updatedList = _discoveredMeters.value.map {
            if (it.address == address) {
                it.withNewRssi(rssi)
            } else {
                it
            }
        }
        _discoveredMeters.value = updatedList
        
        val currentSelect = _selectedMeterDetail.value
        if (currentSelect != null && currentSelect.address == address) {
            _selectedMeterDetail.value = updatedList.find { it.address == address }
        }
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanJob: Job? = null

    init {
        // Load stored user account if any
        val storedEmail = prefs.getString("user_email", null)
        val storedTierStr = prefs.getString("user_tier", null)
        if (storedEmail != null && storedTierStr != null) {
            try {
                val tier = AccountTier.valueOf(storedTierStr)
                _currentUser.value = UserAccount(storedEmail, tier)
            } catch (e: Exception) {
                _currentUser.value = null
            }
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        updateBluetoothState()
        checkPermissionsState()
        startConnectedRssiPolling()
        startScanRssiUpdates()
    }

    private val _gattConnections = mutableMapOf<String, BluetoothGatt>()

    fun selectMeter(meter: BleMeter?) {
        _selectedMeterDetail.value = meter
        if (meter != null) {
            addToRecentlyViewed(meter)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToMeter(meter: BleMeter) {
        val address = meter.address
        updateMeterConnectionState(address, ConnectionState.CONNECTING)

        _connectionTimeoutJobs[address]?.cancel()
        _connectionTimeoutJobs[address] = viewModelScope.launch {
            delay(10000) // 10 seconds timeout
            val currentMeter = _discoveredMeters.value.find { it.address == address }
            if (currentMeter != null && currentMeter.connectionState != ConnectionState.SERVICES_DISCOVERED) {
                appendSerialLog("ERROR", "Connection to ${meter.name} timed out after 10 seconds.")
                _gattConnections[address]?.disconnect()
                _gattConnections[address]?.close()
                _gattConnections.remove(address)
                updateMeterConnectionState(address, ConnectionState.ERROR, "Connection timed out")
            }
        }

        if (meter.isSimulated) {
            viewModelScope.launch {
                delay(800)
                updateMeterConnectionState(address, ConnectionState.CONNECTED)
                delay(500)
                updateMeterConnectionState(address, ConnectionState.DISCOVERING_SERVICES)
                delay(1000)
                
                val virtualServices = listOf(
                    BleServiceInfo(
                        uuid = "0000180A-0000-1000-8000-00805F9B34FB",
                        name = "Device Information Service",
                        characteristics = listOf(
                            BleCharacteristicInfo("00002A29-0000-1000-8000-00805F9B34FB", "Manufacturer Name", "Demo Utility Corp", "READ"),
                            BleCharacteristicInfo("00002A24-0000-1000-8000-00805F9B34FB", "Model Number", "DLMS-M22615", "READ"),
                            BleCharacteristicInfo("00002A26-0000-1000-8000-00805F9B34FB", "Firmware Revision", "v4.18.2-SIM", "READ")
                        )
                    ),
                    BleServiceInfo(
                        uuid = "0000180F-0000-1000-8000-00805F9B34FB",
                        name = "Battery Service",
                        characteristics = listOf(
                            BleCharacteristicInfo("00002A19-0000-1000-8000-00805F9B34FB", "Battery Level", "${meter.telemetry.batteryPercentage}", "READ, NOTIFY")
                        )
                    ),
                    BleServiceInfo(
                        uuid = "0000FFA0-0000-1000-8000-00805F9B34FB",
                        name = "Smart Meter Proprietary Service",
                        characteristics = listOf(
                            BleCharacteristicInfo("0000FFA1-0000-1000-8000-00805F9B34FB", "Active Load Power", "%.3f".format(meter.telemetry.activePowerKw), "READ, NOTIFY"),
                            BleCharacteristicInfo("0000FFA2-0000-1000-8000-00805F9B34FB", "RMS Line Voltage", "%.1f".format(meter.telemetry.voltage), "READ"),
                            BleCharacteristicInfo("0000FFA3-0000-1000-8000-00805F9B34FB", "Current Draw", "%.2f".format(meter.telemetry.current), "READ"),
                            BleCharacteristicInfo("0000FFA4-0000-1000-8000-00805F9B34FB", "Grid Frequency", "%.2f".format(meter.telemetry.gridFrequencyHz), "READ"),
                            BleCharacteristicInfo("0000FFA5-0000-1000-8000-00805F9B34FB", "Cumulative Energy Usage", "%.1f".format(meter.telemetry.cumulativeKwh), "READ")
                        )
                    )
                )
                
                updateMeterServices(address, virtualServices)
                updateMeterConnectionState(address, ConnectionState.SERVICES_DISCOVERED)
            }
            return
        }

        if (bluetoothAdapter == null || _bluetoothState.value != "ON") {
            updateMeterConnectionState(address, ConnectionState.ERROR, "Bluetooth is disabled")
            return
        }

        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device == null) {
                updateMeterConnectionState(address, ConnectionState.ERROR, "Unknown hardware device")
                return
            }

            _gattConnections[address]?.disconnect()
            _gattConnections[address]?.close()

            val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        super.onConnectionStateChange(gatt, status, newState)
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                updateMeterConnectionState(address, ConnectionState.CONNECTED)
                                viewModelScope.launch {
                                    delay(500)
                                    updateMeterConnectionState(address, ConnectionState.DISCOVERING_SERVICES)
                                    gatt.discoverServices()
                                }
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                updateMeterConnectionState(address, ConnectionState.DISCONNECTED)
                                _gattConnections.remove(address)
                                gatt.close()
                            }
                        } else {
                            updateMeterConnectionState(address, ConnectionState.ERROR, "GATT Status Error: $status")
                            _gattConnections.remove(address)
                            gatt.close()
                        }
                    }

                    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                        super.onReadRemoteRssi(gatt, rssi, status)
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            updateMeterRssi(address, rssi)
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        super.onServicesDiscovered(gatt, status)
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val parsedServices = mutableListOf<BleServiceInfo>()
                            gatt.services?.forEach { service ->
                                val characteristicsList = mutableListOf<BleCharacteristicInfo>()
                                service.characteristics?.forEach { char ->
                                    val props = getCharacteristicPropertiesString(char.properties)
                                    val initialVal = if (char.value != null && char.value.isNotEmpty()) {
                                        char.value.joinToString("") { "%02X".format(it) }
                                    } else {
                                        "Click to Read"
                                    }
                                    
                                    val charName = getKnownCharacteristicName(char.uuid.toString())
                                    characteristicsList.add(
                                        BleCharacteristicInfo(
                                            uuid = char.uuid.toString(),
                                            name = charName,
                                            value = initialVal,
                                            properties = props
                                        )
                                    )
                                }
                                val serviceName = getKnownServiceName(service.uuid.toString())
                                parsedServices.add(
                                    BleServiceInfo(
                                        uuid = service.uuid.toString(),
                                        name = serviceName,
                                        characteristics = characteristicsList
                                    )
                                )
                            }
                            updateMeterServices(address, parsedServices)
                            updateMeterConnectionState(address, ConnectionState.SERVICES_DISCOVERED)
                        } else {
                            updateMeterConnectionState(address, ConnectionState.ERROR, "Service Discovery Failed ($status)")
                        }
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        super.onCharacteristicRead(gatt, characteristic, status)
                        @Suppress("DEPRECATION")
                        val valueBytes = characteristic.value
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val valStr = if (valueBytes != null) {
                                try {
                                    val utf = String(valueBytes, Charsets.UTF_8).trim()
                                    if (utf.all { it.isLetterOrDigit() || it.isWhitespace() || ":-%,.".contains(it) }) {
                                        utf
                                    } else {
                                        "0x" + valueBytes.joinToString("") { "%02X".format(it) }
                                    }
                                } catch (e: Exception) {
                                    "0x" + valueBytes.joinToString("") { "%02X".format(it) }
                                }
                            } else {
                                "Empty"
                            }
                            updateCharacteristicValue(address, characteristic.service.uuid.toString(), characteristic.uuid.toString(), valStr)
                        }
                    }
                }

                val gatt = device.connectGatt(context, false, callback)
                if (gatt != null) {
                    _gattConnections[address] = gatt
                } else {
                    updateMeterConnectionState(address, ConnectionState.ERROR, "Could not initialize GATT Link")
                }

            } catch (e: Exception) {
                updateMeterConnectionState(address, ConnectionState.ERROR, "GATT Init Exception: ${e.message}")
            }
    }

    @SuppressLint("MissingPermission")
    fun disconnectMeter(address: String) {
        val meter = _discoveredMeters.value.find { it.address == address }
        if (meter != null && meter.isSimulated) {
            updateMeterConnectionState(address, ConnectionState.DISCONNECTED)
            return
        }
        _gattConnections[address]?.disconnect()
        _gattConnections[address]?.close()
        _gattConnections.remove(address)
        updateMeterConnectionState(address, ConnectionState.DISCONNECTED)
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(address: String, serviceUuid: String, charUuid: String) {
        val meter = _discoveredMeters.value.find { it.address == address }
        if (meter != null && meter.isSimulated) {
            appendSerialLog("TX", "AT+READ_CHAR=$charUuid")
            viewModelScope.launch {
                delay(300)
                val services = meter.services
                val service = services.find { it.uuid == serviceUuid }
                val char = service?.characteristics?.find { it.uuid == charUuid }
                if (char != null) {
                    val newVal = when (charUuid) {
                        "00002A19-0000-1000-8000-00805F9B34FB" -> "${meter.telemetry.batteryPercentage}"
                        "0000FFA1-0000-1000-8000-00805F9B34FB" -> "%.3f".format(meter.telemetry.activePowerKw + Random.nextDouble(-0.1, 0.1))
                        "0000FFA2-0000-1000-8000-00805F9B34FB" -> "%.1f".format(meter.telemetry.voltage + Random.nextDouble(-0.5, 0.5))
                        "0000FFA3-0000-1000-8000-00805F9B34FB" -> "%.2f".format(meter.telemetry.current + Random.nextDouble(-0.05, 0.05))
                        "0000FFA4-0000-1000-8000-00805F9B34FB" -> "%.2f".format(meter.telemetry.gridFrequencyHz + Random.nextDouble(-0.002, 0.002))
                        "0000FFA5-0000-1000-8000-00805F9B34FB" -> "%.3f".format(meter.telemetry.cumulativeKwh + 0.125)
                        else -> char.value
                    }
                    updateCharacteristicValue(address, serviceUuid, charUuid, newVal)
                    appendSerialLog("RX", "CHAR_VAL=$newVal")
                    appendSerialLog("DECODE", "$charUuid: Decoded value changed -> $newVal")
                }
            }
            return
        }
        val gatt = _gattConnections[address] ?: return
        try {
            val service = gatt.getService(java.util.UUID.fromString(serviceUuid))
            val characteristic = service?.getCharacteristic(java.util.UUID.fromString(charUuid))
            if (characteristic != null) {
                gatt.readCharacteristic(characteristic)
            }
        } catch (e: java.lang.Exception) {
            // Attribute read error
        }
    }

    private fun updateMeterConnectionState(address: String, state: ConnectionState, error: String? = null) {
        val updatedList = _discoveredMeters.value.map {
            if (it.address == address) {
                it.copy(connectionState = state, connectionError = error)
            } else {
                it
            }
        }
        _discoveredMeters.value = updatedList
        
        // Update selection sync
        val currentSelect = _selectedMeterDetail.value
        if (currentSelect != null && currentSelect.address == address) {
            _selectedMeterDetail.value = updatedList.find { it.address == address }
        }

        if (state == ConnectionState.SERVICES_DISCOVERED || state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
            _connectionTimeoutJobs[address]?.cancel()
            _connectionTimeoutJobs.remove(address)
        }

        if (state == ConnectionState.SERVICES_DISCOVERED) {
            startSerialConsoleGenerator(address)
        } else if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
            stopSerialConsoleGenerator()
        }
    }

    private fun updateMeterServices(address: String, services: List<BleServiceInfo>) {
        val updatedList = _discoveredMeters.value.map {
            if (it.address == address) {
                it.copy(services = services)
            } else {
                it
            }
        }
        _discoveredMeters.value = updatedList
        val currentSelect = _selectedMeterDetail.value
        if (currentSelect != null && currentSelect.address == address) {
            _selectedMeterDetail.value = updatedList.find { it.address == address }
        }
    }

    private fun updateCharacteristicValue(address: String, serviceUuid: String, charUuid: String, newValue: String) {
        val updatedList = _discoveredMeters.value.map { meter ->
            if (meter.address == address) {
                val updatedServices = meter.services.map { service ->
                    if (service.uuid == serviceUuid) {
                        val updatedChars = service.characteristics.map { char ->
                            if (char.uuid == charUuid) {
                                char.copy(value = newValue)
                            } else {
                                char
                            }
                        }
                        service.copy(characteristics = updatedChars)
                    } else {
                        service
                    }
                }
                meter.copy(services = updatedServices)
            } else {
                meter
            }
        }
        _discoveredMeters.value = updatedList
        val currentSelect = _selectedMeterDetail.value
        if (currentSelect != null && currentSelect.address == address) {
            _selectedMeterDetail.value = updatedList.find { it.address == address }
        }
    }

    fun checkPermissionsState() {
        val granted = getRequiredPermissionsList().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        _permissionsGranted.value = granted
    }

    fun getRequiredPermissionsList(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    private fun updateBluetoothState() {
        val state = if (bluetoothAdapter == null) {
            "NOT_SUPPORTED"
        } else if (bluetoothAdapter!!.isEnabled) {
            "ON"
        } else {
            "OFF"
        }
        _bluetoothState.value = state
    }

    fun startScan() {
        if (!permissionsGranted.value) return
        _isScanning.value = true
        startScanRssiUpdates()

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            _isScanning.value = false
            stopScanRssiUpdates()
        }
    }

    fun stopScan() {
        _isScanning.value = false
        stopScanRssiUpdates()

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // Permission revoked midway
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            addOrUpdateScannedDevice(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { addOrUpdateScannedDevice(it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addOrUpdateScannedDevice(result: ScanResult) {
        val device = result.device
        val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown"

        // Industry or dynamic customizable filter constraint
        if (!_showAllBleDevices.value && _scanFilterPrefix.value.isNotEmpty()) {
            if (!name.startsWith(_scanFilterPrefix.value, ignoreCase = true)) return
        }

        val address = device.address
        val rssi = result.rssi

        val current = _discoveredMeters.value.toMutableList()
        val index = current.indexOfFirst { it.address == address }
        if (index >= 0) {
            current[index] = current[index].withNewRssi(rssi)
        } else {
            current.add(BleMeter(name = name, address = address, rssi = rssi, isSimulated = false))
        }
        _discoveredMeters.value = current.sortedByDescending { it.rssi }
    }

    private fun getCharacteristicPropertiesString(props: Int): String {
        val list = mutableListOf<String>()
        if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) list.add("READ")
        if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) list.add("WRITE")
        if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) list.add("NOTIFY")
        if ((props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) list.add("INDICATE")
        return if (list.isEmpty()) "NONE" else list.joinToString(", ")
    }

    private fun getKnownServiceName(uuidStr: String): String {
        return when (uuidStr.uppercase()) {
            "00001800-0000-1000-8000-00805F9B34FB" -> "Generic Access Service"
            "00001801-0000-1000-8000-00805F9B34FB" -> "Generic Attribute Service"
            "0000180A-0000-1000-8000-00805F9B34FB" -> "Device Information Service"
            "0000180F-0000-1000-8000-00805F9B34FB" -> "Battery Service"
            "0000FF00-0000-1000-8000-00805F9B34FB" -> "Utility Meter Services"
            else -> "Custom Utility Profile Service"
        }
    }

    private fun getKnownCharacteristicName(uuidStr: String): String {
        return when (uuidStr.uppercase()) {
            "00002A29-0000-1000-8000-00805F9B34FB" -> "Manufacturer"
            "00002A24-0000-1000-8000-00805F9B34FB" -> "Model"
            "00002A27-0000-1000-8000-00805F9B34FB" -> "Hardware Rev"
            "00002A19-0000-1000-8000-00805F9B34FB" -> "Battery Level"
            "0000FF01-0000-1000-8000-00805F9B34FB" -> "Load Demand kW"
            "0000FF02-0000-1000-8000-00805F9B34FB" -> "Node Line Voltage V"
            "0000FF03-0000-1000-8000-00805F9B34FB" -> "Active Current Flow A"
            else -> "Utility Characteristic"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        stopSerialConsoleGenerator()
        stopScanRssiUpdates()
        _connectionTimeoutJobs.values.forEach { it.cancel() }
        _connectionTimeoutJobs.clear()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BleScannerAppContainer()
            }
        }
    }
}
