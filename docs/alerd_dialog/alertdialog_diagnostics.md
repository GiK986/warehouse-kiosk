# Диагностика и отладка: "Input channel object was disposed" грешка

## Как да разпознаеш race condition

### Признаци на race condition:

1. **Временни крахове** - не винаги се случва
2. **Различна поведение** на различни устройства
3. **Случайни грешки** в логовете при navigation
4. **Stuttering/lag** след dismiss на dialog
5. **ANR (Application Not Responding)** при бързи операции

### Точна грешка в logcat:

```
E/AndroidRuntime: FATAL EXCEPTION: main
    java.lang.RuntimeException: Input channel object was disposed without first 
    being removed with the input manager
        at android.view.InputChannel.dispose()
        at android.view.ViewRootImpl.setView()
        
    Caused by: java.lang.RuntimeException: ViewRootImpl.performTraversal
        at android.view.ViewRootImpl.performTraversal()
```

---

##步骤 1: Диагностирай текущата конфигурация

### Добави logging в твоя PasswordDialog.kt:

```kotlin
@Composable
fun PasswordDialog(navController: NavController) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Log state changes
    LaunchedEffect(showPasswordDialog) {
        Log.d("PasswordDialog", "State changed: showPasswordDialog=$showPasswordDialog, time=${System.currentTimeMillis()}")
    }

    // Log composition lifecycle
    DisposableEffect(Unit) {
        Log.d("PasswordDialog", "Composable entered composition")
        onDispose {
            Log.d("PasswordDialog", "Composable left composition")
        }
    }

    Button(
        onClick = {
            Log.d("PasswordDialog", "Button clicked, starting dismiss sequence")
            showPasswordDialog = false
            
            // Log coroutine launch
            viewModelScope.launch {
                Log.d("PasswordDialog", "Coroutine launched for navigation")
                delay(100)
                Log.d("PasswordDialog", "Delay complete, navigating...")
                navController.navigate("main_screen")
                Log.d("PasswordDialog", "Navigation called")
            }
        }
    ) {
        Text("Dismiss")
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                Log.d("PasswordDialog", "onDismissRequest called")
                showPasswordDialog = false
            },
            // ... rest of AlertDialog
        )
    }
}
```

### Анализ на логовете:

```
// ✅ ПРАВИЛЕН ред:
1. "State changed: showPasswordDialog=true"
2. "Button clicked, starting dismiss sequence"
3. "State changed: showPasswordDialog=false"  ← Dialog из composition removed
4. "Composable left composition"
5. "Coroutine launched for navigation"
6. "Delay complete, navigating..."
7. "Navigation called"

// ❌ ГРЕШЕН ред (race condition):
1. "State changed: showPasswordDialog=true"
2. "Button clicked, starting dismiss sequence"
3. "Coroutine launched for navigation"       ← Твърде рано!
4. "State changed: showPasswordDialog=false"
5. "Delay complete, navigating..."
6. "Navigation called"
7. "Composable left composition"             ← Слишком поздно!
8. CRASH: "Input channel object was disposed..."
```

---

## Шаг 2: Анализ със трасиране

### Использовай Android Profiler:

```kotlin
// Добави profiler events за отследяване
@Composable
fun PasswordDialogWithProfiling(navController: NavController) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    // Start tracing
    val traceTag = "PasswordDialog"
    
    Button(
        onClick = {
            // Begin trace
            android.os.Trace.beginSection("$traceTag:dismiss")
            try {
                showPasswordDialog = false
                viewModelScope.launch {
                    android.os.Trace.beginSection("$traceTag:navigate")
                    try {
                        delay(100)
                        navController.navigate("main_screen")
                    } finally {
                        android.os.Trace.endSection()
                    }
                }
            } finally {
                android.os.Trace.endSection()
            }
        }
    ) { Text("Dismiss") }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            // ...
        )
    }
}
```

### Как четеш Profiler резултатите:

1. Отвори Android Studio → Profiler
2. Натисни "Trace System API Calls" checkbox
3. Погледни "System Trace" tab
4. Намери твоите "PasswordDialog:dismiss" и "PasswordDialog:navigate" events
5. Проверете че "dismiss" завршава **преди** "navigate" да почне

---

## Шаг 3: Тест на различни timing сценарии

### Create test scenario file:

```kotlin
// Test.kt - за тестване на timing

fun testPasswordDialogTiming() {
    // Сценарий 1: Нормална операция
    testCase("Normal dismiss and navigate") {
        showDialog()
        delay(100)  // дай време на dialog да се render
        clickConfirm()
        delay(200)  // дай време за dismiss animation
        verifyNavigated()
    }

    // Сценарий 2: Быстрые click
    testCase("Rapid clicks") {
        showDialog()
        delay(50)   // Много рано!
        clickConfirm()
        delay(10)   // Много малко!
        clickConfirm() // Double click!
        verifyNavigated()
    }

    // Сценарий 3: Dialog interrupted
    testCase("Dialog interrupted") {
        showDialog()
        delay(100)
        clickConfirm()
        delay(50)
        // System interrupt (e.g., screen rotation)
        simulateScreenRotation()
        delay(100)
        verifyNavigated()
    }

    // Сценарий 4: Slow device
    testCase("Slow device simulation") {
        showDialog()
        delay(100)
        clickConfirm()
        // Симулирай медленное устройство
        simulateHighCPULoad()
        delay(500) // Дай повече време
        verifyNavigated()
    }
}
```

---

## Шаг 4: Подробна диагностика на вашия текущ код

### Въпрос: Защо delay(100) е проблематичен?

```
Timeline на вашия текущ код:

T=0ms:    showPasswordDialog = false (dialog removed from composition)
T=1ms:    DialogComposable вече не се invocates
T=5ms:    Compose начало recomposition
T=20ms:   Recomposition завершена
T=50ms:   Input Channel начало cleanup process
T=100ms:  delay() завършва
T=101ms:  navController.navigate() се вика
T=102ms:  Navigation начало
T=150ms:  Input Channel все още в cleanup
T=151ms:  Navigation детайли се обработват
T=200ms:  CRASH! Input Channel disposed but navigation ongoing

Проблем: Timing е случаен!
```

### Как да диагностицираш:

```kotlin
@Composable
fun DiagnosticPasswordDialog(navController: NavController) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var lastActionTime by remember { mutableStateOf(0L) }
    var compositionId by remember { mutableStateOf(UUID.randomUUID()) }

    DisposableEffect(compositionId) {
        Log.d("DIAG", "Composition $compositionId created")
        val startTime = System.currentTimeMillis()
        
        onDispose {
            val duration = System.currentTimeMillis() - startTime
            Log.d("DIAG", "Composition $compositionId disposed after ${duration}ms")
        }
    }

    LaunchedEffect(showPasswordDialog) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLast = currentTime - lastActionTime
        Log.d("DIAG", "Dialog state=$showPasswordDialog, delta=${timeSinceLast}ms")
        lastActionTime = currentTime
    }

    Button(
        onClick = {
            Log.d("DIAG", "Button clicked at ${System.currentTimeMillis()}")
            val dismissTime = System.currentTimeMillis()
            showPasswordDialog = false
            
            viewModelScope.launch {
                Log.d("DIAG", "Launch at ${System.currentTimeMillis()}, delta=${System.currentTimeMillis()-dismissTime}ms")
                delay(100)
                val beforeNav = System.currentTimeMillis()
                Log.d("DIAG", "Before navigate at ${beforeNav}, delta=${beforeNav-dismissTime}ms")
                navController.navigate("main_screen")
            }
        }
    ) { Text("Dismiss") }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            confirmButton = { Button(onClick = { showPasswordDialog = false }) { Text("OK") } }
        )
    }
}

// Analyze logs:
// Look for patterns:
// - "dismiss" и "navigate" са в един coroutine scope
// - Timing варира по-силно от очаквано
// - Race conditions са видими в несоответствия на timestamps
```

---

## Шаг 5: Проверка на вашите dependencies

### Провери версиите:

```kotlin
// في build.gradle.kts

// ❌ СТАРО:
dependencies {
    implementation "androidx.compose.ui:ui:1.5.0"
    implementation "androidx.compose.runtime:runtime:1.5.0"
    implementation "androidx.navigation:navigation-compose:2.5.0"
}

// ✅ НОВО (Ноември 2025):
dependencies {
    implementation "androidx.compose.ui:ui:1.7.0"
    implementation "androidx.compose.runtime:runtime:1.7.0"
    implementation "androidx.navigation:navigation-compose:2.8.0"
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0"
    implementation "androidx.lifecycle:lifecycle-runtime-compose:2.8.0"
}
```

### Чекирай известни проблеми:

```
Compose 1.5.x има известен бъг с input channel disposal:
- Fixed in 1.6.0
- Fixed in 1.7.0

Navigation 2.5.x има timing issues:
- Fixed in 2.6.0
- Fixed in 2.8.0

Ако имаш старите версии, UPGRADE я!
```

---

## Шаг 6: Детайлна трасировка на AlertDialog lifecycle

### Complete diagnostic composable:

```kotlin
@Composable
fun CompletePasswordDialogDiagnostics(
    navController: NavController,
    viewModel: PasswordViewModel = viewModel()
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var debugLog by remember { mutableStateOf(listOf<String>()) }
    
    fun addLog(msg: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val threadId = Thread.currentThread().id
        debugLog = debugLog + "[$timestamp][T$threadId] $msg"
        Log.d("DIALOG_DEBUG", msg)
    }

    // Lifecycle tracking
    DisposableEffect(showPasswordDialog) {
        addLog("DisposableEffect entered: showPasswordDialog=$showPasswordDialog")
        
        onDispose {
            addLog("DisposableEffect onDispose called")
        }
    }

    // Composition tracking
    remember {
        addLog("Composable function invoked")
        { }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Debug log display
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .border(1.dp, Color.Gray)
                .padding(4.dp)
        ) {
            items(debugLog.size) { index ->
                Text(
                    debugLog[index],
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                addLog("Click: Starting dismiss sequence")
                val startTime = System.nanoTime()
                
                addLog("State change: showPasswordDialog = false")
                showPasswordDialog = false
                
                addLog("Launching coroutine for navigation")
                viewModelScope.launch {
                    val launchTime = System.nanoTime()
                    addLog("Coroutine started: delta=${(launchTime - startTime) / 1_000_000}ms")
                    
                    addLog("Before delay(100)")
                    delay(100)
                    val afterDelay = System.nanoTime()
                    addLog("After delay(100): delta=${(afterDelay - startTime) / 1_000_000}ms")
                    
                    addLog("Calling navigate()")
                    try {
                        navController.navigate("main_screen")
                        addLog("navigate() completed successfully")
                    } catch (e: Exception) {
                        addLog("navigate() failed: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Text("Dismiss Dialog")
        }

        if (showPasswordDialog) {
            addLog("AlertDialog rendering")
            AlertDialog(
                onDismissRequest = {
                    addLog("AlertDialog.onDismissRequest called")
                    showPasswordDialog = false
                },
                confirmButton = {
                    Button(onClick = { showPasswordDialog = false }) {
                        Text("OK")
                    }
                }
            )
        } else {
            addLog("AlertDialog NOT rendering (showPasswordDialog=false)")
        }
    }
}
```

---

## Шаг 7: Проверка на Input Manager state

### Deep inspection:

```kotlin
// За experimentation (НЕ за production!)
fun inspectInputChannelState(viewRootImpl: ViewRootImpl) {
    try {
        val inputChannelField = ViewRootImpl::class.java.getDeclaredField("mInputChannel")
        inputChannelField.isAccessible = true
        val inputChannel = inputChannelField.get(viewRootImpl)
        
        Log.d("INPUT_DEBUG", "InputChannel state: $inputChannel")
        
        if (inputChannel != null) {
            val dispatchingField = InputChannel::class.java.getDeclaredField("mDispatcher")
            dispatchingField.isAccessible = true
            val dispatcher = dispatchingField.get(inputChannel)
            Log.d("INPUT_DEBUG", "Dispatcher: $dispatcher")
        }
    } catch (e: Exception) {
        Log.e("INPUT_DEBUG", "Error inspecting input channel", e)
    }
}
```

---

## Шаг 8: Network на проблеми и решения

### Таблица за диагностика:

| Симптом | Причина | Решение |
|---------|---------|---------|
| Крах при click | Input channel disposed during dialog removal | Използовай LaunchedEffect |
| Случайни крахе | Timing-dependent race condition | Обработи state changes правилно |
| ANR при navigate | Блокиран main thread | Използовай viewModelScope |
| Memory leak | Input handlers не деregistered | DisposableEffect с cleanup |
| Screen rotation crash | Dialog state lost | Save state in ViewModel |

---

## Шаг 9: Production monitoring

### Добави Crash Analytics:

```kotlin
// Fabric/Firebase Crashlytics integration
@Composable
fun PasswordDialogWithCrashReporting(navController: NavController) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    try {
        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    try {
                        showPasswordDialog = false
                    } catch (e: Exception) {
                        // Report to Firebase
                        FirebaseCrashlytics.getInstance().recordException(e)
                        Log.e("PasswordDialog", "Error dismissing dialog", e)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        try {
                            showPasswordDialog = false
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().recordException(e)
                        }
                    }) { Text("OK") }
                }
            )
        }
    } catch (e: Exception) {
        FirebaseCrashlytics.getInstance().recordException(e)
        Log.e("PasswordDialog", "Fatal error", e)
    }
}
```

---

## Шаг 10: Отладка във вашата IDE

### Breakpoints strategy:

1. **Set breakpoint** на `showPasswordDialog = false`
2. **Set conditional breakpoint** на `navController.navigate()`
   - Condition: `!showPasswordDialog && isDialogComposing`
3. **Use Debug Watches**:
   ```
   showPasswordDialog
   Thread.currentThread().id
   System.currentTimeMillis()
   ```
4. **Step through** navigation lifecycle
5. **Inspect** Compose recomposition stack

---

## Резюме на диагностични стъпки

1. ✅ Добави logging във всички ключни точки
2. ✅ Анализирай logcat за race condition сигнали
3. ✅ Използовай Android Profiler за timeline analysis
4. ✅ Тестирай различни timing сценарии
5. ✅ Провери версиите на Compose и Navigation
6. ✅ Имплементирай complete lifecycle tracking
7. ✅ Инспектирай InputChannel state (за debug)
8. ✅ Постави мониториране на crashes
9. ✅ Използовай IDE debugger със breakpoints
10. ✅ Мониториrake metrics в production

