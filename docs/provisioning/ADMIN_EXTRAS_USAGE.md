# Използване на Admin Extras Bundle

## 📦 Какво съдържа ADMIN_EXTRAS_BUNDLE?

След QR provisioning, всички данни от `locations.json` стават достъпни в приложението:

```json
{
  "warehouse_id": "WH_SOFIA_CENTRAL_01",
  "server_url": "https://api.warehouse.bg",
  "printer_ip": "192.168.1.100",
  "printer_name": "Zebra_ZD421",
  "scanner_type": "honeywell_1900",
  "location_name": "Склад София - Централен"
}
```

---

## 🔧 Как да ги прочетем в MainActivity

### Пример 1: Четене при старт на приложението

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверка дали идваме от provisioning
        if (intent.getBooleanExtra("from_provisioning", false)) {
            readProvisioningExtras()
        }

        setContent {
            // ... твоят UI код
        }
    }

    private fun readProvisioningExtras() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceOwnerReceiver::class.java)

        try {
            // Взимаме admin extras от provisioning
            val extras = dpm.getApplicationRestrictions(adminComponent, packageName)

            val warehouseId = extras.getString("warehouse_id", "")
            val serverUrl = extras.getString("server_url", "")
            val printerIp = extras.getString("printer_ip", "")
            val printerName = extras.getString("printer_name", "")
            val scannerType = extras.getString("scanner_type", "")
            val locationName = extras.getString("location_name", "")

            Log.d("Provisioning", "Warehouse ID: $warehouseId")
            Log.d("Provisioning", "Server URL: $serverUrl")
            Log.d("Provisioning", "Printer: $printerName at $printerIp")
            Log.d("Provisioning", "Scanner: $scannerType")
            Log.d("Provisioning", "Location: $locationName")

            // Запазваме в SharedPreferences или DataStore
            saveProvisioningConfig(
                warehouseId, serverUrl, printerIp,
                printerName, scannerType, locationName
            )

        } catch (e: Exception) {
            Log.e("Provisioning", "Failed to read extras", e)
        }
    }

    private fun saveProvisioningConfig(
        warehouseId: String,
        serverUrl: String,
        printerIp: String,
        printerName: String,
        scannerType: String,
        locationName: String
    ) {
        val prefs = getSharedPreferences("warehouse_config", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("warehouse_id", warehouseId)
            putString("server_url", serverUrl)
            putString("printer_ip", printerIp)
            putString("printer_name", printerName)
            putString("scanner_type", scannerType)
            putString("location_name", locationName)
            apply()
        }

        Log.d("Config", "Configuration saved for location: $locationName")
    }
}
```

---

## 💡 Практически примери за употреба

### 1️⃣ **SERVER_URL** - API комуникация

```kotlin
// ApiClient.kt
class ApiClient(private val context: Context) {

    private fun getServerUrl(): String {
        val prefs = context.getSharedPreferences("warehouse_config", Context.MODE_PRIVATE)
        return prefs.getString("server_url", "https://default-api.com") ?: "https://default-api.com"
    }

    suspend fun sendInventoryData(data: InventoryData): Result<Unit> {
        val serverUrl = getServerUrl()
        val warehouseId = getWarehouseId()

        return try {
            val response = httpClient.post("$serverUrl/api/inventory") {
                contentType(ContentType.Application.Json)
                setBody(InventoryRequest(
                    warehouseId = warehouseId,
                    items = data.items,
                    timestamp = System.currentTimeMillis()
                ))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getWarehouseId(): String {
        val prefs = context.getSharedPreferences("warehouse_config", Context.MODE_PRIVATE)
        return prefs.getString("warehouse_id", "UNKNOWN") ?: "UNKNOWN"
    }
}
```

**Use Case:** Всяка локация има свой warehouse_id и знае към кой сървър да изпраща данни.

---

### 2️⃣ **PRINTER_IP & PRINTER_NAME** - Автоматично принтиране

```kotlin
// PrinterManager.kt
class PrinterManager(private val context: Context) {

    private var printerConnection: Connection? = null

    fun initializePrinter() {
        val prefs = context.getSharedPreferences("warehouse_config", Context.MODE_PRIVATE)
        val printerIp = prefs.getString("printer_ip", "") ?: ""
        val printerName = prefs.getString("printer_name", "Zebra") ?: "Zebra"

        if (printerIp.isNotEmpty()) {
            Log.d("Printer", "Connecting to $printerName at $printerIp")
            connectToPrinter(printerIp)
        }
    }

    private fun connectToPrinter(ipAddress: String) {
        try {
            printerConnection = NetworkConnection(ipAddress)
            printerConnection?.open()
            Log.d("Printer", "Connected to printer at $ipAddress")
        } catch (e: Exception) {
            Log.e("Printer", "Failed to connect", e)
        }
    }

    fun printBarcode(barcode: String, productName: String) {
        val printerName = getPrinterName()

        try {
            printerConnection?.let { conn ->
                // ZPL код за Zebra принтери
                val zpl = """
                    ^XA
                    ^FO50,50^BY3
                    ^BCN,100,Y,N,N
                    ^FD$barcode^FS
                    ^FO50,180^A0N,30,30^FD$productName^FS
                    ^XZ
                """.trimIndent()

                conn.write(zpl.toByteArray())
                Log.d("Printer", "Barcode printed: $barcode")
            }
        } catch (e: Exception) {
            Log.e("Printer", "Print failed", e)
        }
    }

    private fun getPrinterName(): String {
        val prefs = context.getSharedPreferences("warehouse_config", Context.MODE_PRIVATE)
        return prefs.getString("printer_name", "Unknown") ?: "Unknown"
    }

    fun disconnect() {
        printerConnection?.close()
    }
}

// Използване:
val printerManager = PrinterManager(context)
printerManager.initializePrinter()
printerManager.printBarcode("123456789", "Product XYZ")
```

**Use Case:** Всяка локация има свой принтер. След provisioning устройството знае IP адреса и автоматично се свързва.

---

### 3️⃣ **SCANNER_TYPE** - Конфигуриране на баркод скенер

```kotlin
// ScannerConfig.kt
class ScannerConfig(private val context: Context) {

    fun getScannerType(): ScannerType {
        val prefs = context.getSharedPreferences("warehouse_config", Context.MODE_PRIVATE)
        val scannerType = prefs.getString("scanner_type", "generic") ?: "generic"

        return when (scannerType) {
            "honeywell_1900" -> ScannerType.Honeywell1900
            "datalogic_gryphon" -> ScannerType.DatalogicGryphon
            "zebra_ds3678" -> ScannerType.ZebraDS3678
            else -> ScannerType.Generic
        }
    }

    fun configureScannerSettings() {
        when (getScannerType()) {
            ScannerType.Honeywell1900 -> {
                // Honeywell specific configuration
                configureHoneywell()
            }
            ScannerType.DatalogicGryphon -> {
                // Datalogic specific configuration
                configureDatalogic()
            }
            ScannerType.ZebraDS3678 -> {
                // Zebra specific configuration
                configureZebra()
            }
            else -> {
                // Generic scanner
                Log.d("Scanner", "Using generic scanner configuration")
            }
        }
    }

    private fun configureHoneywell() {
        Log.d("Scanner", "Configuring Honeywell 1900 scanner")
        // Honeywell SDK configuration
        // Example: set symbologies, trigger modes, etc.
    }

    private fun configureDatalogic() {
        Log.d("Scanner", "Configuring Datalogic Gryphon scanner")
        // Datalogic SDK configuration
    }

    private fun configureZebra() {
        Log.d("Scanner", "Configuring Zebra DS3678 scanner")
        // Zebra DataWedge or EMDK configuration
    }

    enum class ScannerType {
        Honeywell1900,
        DatalogicGryphon,
        ZebraDS3678,
        Generic
    }
}

// Използване в MainActivity:
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val scannerConfig = ScannerConfig(this)
    scannerConfig.configureScannerSettings()
}
```

**Use Case:** Различни складове имат различни скенери. Приложението автоматично се конфигурира за правилния хардуер.

---

### 4️⃣ **WAREHOUSE_ID** - Идентификация на локацията

```kotlin
// ViewModel.kt
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val warehouseId: String
        get() {
            val prefs = context.getSharedPreferences("warehouse_config", Context.MODE_PRIVATE)
            return prefs.getString("warehouse_id", "UNKNOWN") ?: "UNKNOWN"
        }

    fun scanProduct(barcode: String) {
        viewModelScope.launch {
            val product = repository.getProduct(barcode)

            // Запазваме скена с warehouse ID
            val scanRecord = ScanRecord(
                barcode = barcode,
                warehouseId = warehouseId,
                timestamp = System.currentTimeMillis(),
                productName = product?.name ?: "Unknown"
            )

            repository.saveScanRecord(scanRecord)

            // Синхронизираме със сървъра
            repository.syncToServer(scanRecord)
        }
    }
}

data class ScanRecord(
    val barcode: String,
    val warehouseId: String,
    val timestamp: Long,
    val productName: String
)
```

**Use Case:** Всеки скан се маркира с warehouse_id за да знаеш в кой склад е направен.

---

### 5️⃣ **LOCATION_NAME** - UI персонализация

```kotlin
// UI Composable
@Composable
fun WarehouseHeader() {
    val prefs = LocalContext.current.getSharedPreferences("warehouse_config", Context.MODE_PRIVATE)
    val locationName = prefs.getString("location_name", "Склад") ?: "Склад"
    val warehouseId = prefs.getString("warehouse_id", "") ?: ""

    TopAppBar(
        title = {
            Column {
                Text(text = locationName)
                Text(
                    text = warehouseId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
```

**Use Case:** UI-ят показва името на локацията и warehouse ID за визуална идентификация.

---

## 🎯 Пълен пример: Warehouse Kiosk Screen

```kotlin
@Composable
fun WarehouseKioskScreen(
    viewModel: WarehouseViewModel = hiltViewModel()
) {
    val prefs = LocalContext.current.getSharedPreferences("warehouse_config", Context.MODE_PRIVATE)
    val locationName = prefs.getString("location_name", "Unknown") ?: "Unknown"
    val warehouseId = prefs.getString("warehouse_id", "UNKNOWN") ?: "UNKNOWN"
    val printerIp = prefs.getString("printer_ip", "No Printer") ?: "No Printer"
    val scannerType = prefs.getString("scanner_type", "Generic") ?: "Generic"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$locationName ($warehouseId)") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Location Info Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Локация", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Име:", locationName)
                    InfoRow("Warehouse ID:", warehouseId)
                    InfoRow("Принтер:", printerIp)
                    InfoRow("Скенер:", scannerType)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions
            Button(
                onClick = { viewModel.testPrinter() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Тест на принтер")
            }

            Button(
                onClick = { viewModel.syncData() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Синхронизация")
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
```

---

## 📝 Резюме

| Поле | Как се използва | Пример |
|------|----------------|--------|
| **warehouse_id** | Идентификация на локацията в API calls, база данни записи | "WH_SOFIA_CENTRAL_01" |
| **server_url** | API endpoint за комуникация | "https://api.warehouse.bg" |
| **printer_ip** | Автоматично свързване към принтер | "192.168.1.100" |
| **printer_name** | Модел на принтера за специфична конфигурация | "Zebra_ZD421" |
| **scanner_type** | Конфигурация на баркод скенер SDK | "honeywell_1900" |
| **location_name** | UI персонализация, логове | "Склад София - Централен" |

## 🔐 Best Practices

1. **Винаги запазвай extras в SharedPreferences/DataStore** след provisioning
2. **Използовай defaults** ако extras липсват
3. **Валидирай данните** преди употреба (особено IP адреси, URLs)
4. **Логвай** когато четеш/записваш extras за debugging
5. **Криптирай чувствителни данни** ако е нужно

---

**След provisioning устройството "знае" всичко за локацията си автоматично!** 🎉