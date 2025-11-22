# PasswordDialog.kt - Практически решения за вашия случай

## Актуалното состояние (C текущия проблем)

```kotlin
// ❌ ТЕКУЩО - Потенциална race condition на line 46
val passwordDialogState = remember { mutableStateOf(false) }

// ... някъде в кода
Button(
    onClick = {
        passwordDialogState.value = false
        viewModelScope.launch {
            delay(100) // Line 46: Проблемът - неопределена delay
            navController.navigate("main_screen")
        }
    }
)
```

### Проблеми с текущия подход:
1. **100ms delay не гарантира completion** на composition
2. **Race condition** може да възникне между dialog removal и navigation
3. **Непредсказуем** на различни устройства
4. **Memory leaks** ако корутина се отмени по време на delay
5. **Input channel disposal** преди cleanup

---

## ✅ Решение 1: Правилна LaunchedEffect версия (ПРЕПОРЪЧАНО)

```kotlin
// PasswordDialog.kt

@Composable
fun PasswordDialogScreen(navController: NavController) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var shouldNavigate by remember { mutableStateOf(false) }

    // ✅ ПРАВИЛНО: LaunchedEffect управлява navigation timing
    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            // По този момент:
            // 1. Dialog е вече removed от composition
            // 2. Всички ресурси са disposed
            // 3. Input channels са deregistered
            navController.navigate("main_screen")
            shouldNavigate = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = { showPasswordDialog = true },
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Show Password Dialog")
        }

        // Dialog composable - условно показан
        if (showPasswordDialog) {
            PasswordAlertDialog(
                onDismissRequest = {
                    showPasswordDialog = false
                },
                onConfirm = { password ->
                    // Validate password...
                    validatePassword(password) { isValid ->
                        if (isValid) {
                            showPasswordDialog = false
                            shouldNavigate = true // ← This triggers navigation
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PasswordAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Enter Password") },
        text = {
            Column {
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showPassword,
                        onCheckedChange = { showPassword = it }
                    )
                    Text("Show password")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
```

**Преимущества:**
- ✅ Няма race conditions
- ✅ Composable architecture следвана правилно
- ✅ Автоматично управление на timing
- ✅ Чисто и четимо

---

## ✅ Решение 2: ViewModel-based (За по-сложни сценарии)

```kotlin
// PasswordViewModel.kt
class PasswordViewModel : ViewModel() {
    private val _dialogState = MutableStateFlow<DialogState>(DialogState.Hidden)
    val dialogState = _dialogState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>(
        extraBufferCapacity = 1
    )
    val navigationEvent = _navigationEvent.asSharedFlow()

    fun showDialog() {
        _dialogState.value = DialogState.Visible
    }

    fun dismissDialog() {
        _dialogState.value = DialogState.Hidden
    }

    fun confirmPassword(password: String) {
        viewModelScope.launch {
            // Validation logic
            val isValid = validatePassword(password)
            
            if (isValid) {
                // Dismiss first
                _dialogState.value = DialogState.Hidden
                
                // Then navigate (Compose будет обработана)
                _navigationEvent.emit(NavigationEvent.ToMainScreen)
            } else {
                // Show error
                _navigationEvent.emit(NavigationEvent.ShowError("Invalid password"))
            }
        }
    }

    sealed class DialogState {
        object Hidden : DialogState()
        object Visible : DialogState()
    }

    sealed class NavigationEvent {
        object ToMainScreen : NavigationEvent()
        data class ShowError(val message: String) : NavigationEvent()
    }

    private suspend fun validatePassword(password: String): Boolean {
        // Симулирай async validation
        delay(500)
        return password.length >= 6
    }
}

// PasswordDialog.kt
@Composable
fun PasswordScreen(
    viewModel: PasswordViewModel = viewModel(),
    navController: NavController
) {
    val dialogState by viewModel.dialogState.collectAsState()
    val navigationEvent = viewModel.navigationEvent.collectAsStateWithLifecycle(initialValue = null)

    // ✅ Управление на navigation через event
    LaunchedEffect(navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                PasswordViewModel.NavigationEvent.ToMainScreen -> {
                    navController.navigate("main_screen") {
                        popUpTo("password_screen") { inclusive = true }
                    }
                }
                is PasswordViewModel.NavigationEvent.ShowError -> {
                    // Show toast or snackbar
                    println("Error: ${event.message}")
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { viewModel.showDialog() }) {
            Text("Login")
        }

        when (val state = dialogState) {
            PasswordViewModel.DialogState.Hidden -> {
                // Dialog скрит
            }
            PasswordViewModel.DialogState.Visible -> {
                PasswordAlertDialog(
                    onDismissRequest = { viewModel.dismissDialog() },
                    onConfirm = { password ->
                        viewModel.confirmPassword(password)
                    }
                )
            }
        }
    }
}
```

**Преимущества:**
- ✅ Бизнес логика отделена от UI
- ✅ Лесна за тестване
- ✅ Скалируема архитектура
- ✅ State persistence на конфигурационни промени

---

## ✅ Решение 3: State Machine паттерн (За сложни dialogs)

```kotlin
// DialogLifecycleState.kt
sealed class PasswordDialogLifecycle {
    object Hidden : PasswordDialogLifecycle()
    object Appearing : PasswordDialogLifecycle()
    object Visible : PasswordDialogLifecycle()
    object Validating : PasswordDialogLifecycle()
    object Dismissing : PasswordDialogLifecycle()
    object Disposed : PasswordDialogLifecycle()
}

// PasswordDialogManager.kt
class PasswordDialogManager {
    private val _lifecycle = MutableStateFlow<PasswordDialogLifecycle>(
        PasswordDialogLifecycle.Hidden
    )
    val lifecycle = _lifecycle.asStateFlow()

    fun show() {
        _lifecycle.value = PasswordDialogLifecycle.Appearing
    }

    fun setVisible() {
        if (_lifecycle.value == PasswordDialogLifecycle.Appearing) {
            _lifecycle.value = PasswordDialogLifecycle.Visible
        }
    }

    fun startValidation() {
        if (_lifecycle.value == PasswordDialogLifecycle.Visible) {
            _lifecycle.value = PasswordDialogLifecycle.Validating
        }
    }

    fun dismiss() {
        _lifecycle.value = PasswordDialogLifecycle.Dismissing
    }

    fun onAnimationComplete() {
        when (_lifecycle.value) {
            PasswordDialogLifecycle.Appearing -> _lifecycle.value = PasswordDialogLifecycle.Visible
            PasswordDialogLifecycle.Dismissing -> _lifecycle.value = PasswordDialogLifecycle.Disposed
            else -> {} // no-op
        }
    }

    fun isReadyForNavigation(): Boolean {
        return _lifecycle.value == PasswordDialogLifecycle.Disposed
    }

    fun reset() {
        _lifecycle.value = PasswordDialogLifecycle.Hidden
    }
}

// PasswordScreen.kt
@Composable
fun PasswordScreenWithStateMachine(
    navController: NavController,
    manager: PasswordDialogManager = remember { PasswordDialogManager() }
) {
    val lifecycle by manager.lifecycle.collectAsState()

    // ✅ Navigate ONLY when dialog is fully disposed
    LaunchedEffect(lifecycle) {
        if (manager.isReadyForNavigation()) {
            navController.navigate("main_screen")
            manager.reset()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { manager.show() },
            enabled = lifecycle == PasswordDialogLifecycle.Hidden
        ) {
            Text("Show Password Dialog")
        }

        when (lifecycle) {
            PasswordDialogLifecycle.Hidden -> {
                // Dialog скрит
            }

            PasswordDialogLifecycle.Appearing,
            PasswordDialogLifecycle.Visible,
            PasswordDialogLifecycle.Validating,
            PasswordDialogLifecycle.Dismissing -> {
                AnimatedVisibility(
                    visible = lifecycle != PasswordDialogLifecycle.Disposed,
                    enter = fadeIn() + scaleIn(initialScale = 0.95f),
                    exit = fadeOut() + scaleOut(targetScale = 0.95f),
                    label = "PasswordDialogAnimation"
                ) {
                    PasswordAlertDialogWithState(
                        state = lifecycle,
                        onDismissRequest = { manager.dismiss() },
                        onConfirm = { password ->
                            manager.startValidation()
                            // Validate...
                            manager.dismiss()
                        },
                        onAnimationComplete = { manager.onAnimationComplete() }
                    )
                }
            }

            PasswordDialogLifecycle.Disposed -> {
                // Will navigate in LaunchedEffect
            }
        }
    }
}

@Composable
private fun PasswordAlertDialogWithState(
    state: PasswordDialogLifecycle,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    onAnimationComplete: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    // Track animation completion
    LaunchedEffect(Unit) {
        snapshotFlow { state }.collect { currentState ->
            if (currentState == PasswordDialogLifecycle.Visible) {
                onAnimationComplete()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Enter Password") },
        text = {
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = state == PasswordDialogLifecycle.Visible
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty() && state == PasswordDialogLifecycle.Visible
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = state != PasswordDialogLifecycle.Validating
            ) {
                Text("Cancel")
            }
        }
    )
}
```

**Преимущества:**
- ✅ Явна управление на всички състояния
- ✅ Безопасни преходи
- ✅ Лесна за дебъгиране
- ✅ Предсказуемо поведение

---

## ✅ Решение 4: Минимална промяна (Ако искаш quick fix)

```kotlin
// Просто замени delay() със yield()

// ❌ СТАРА версия
Button(
    onClick = {
        showPasswordDialog = false
        viewModelScope.launch {
            delay(100) // ← Проблем!
            navController.navigate("main_screen")
        }
    }
)

// ✅ НОВА версия
Button(
    onClick = {
        showPasswordDialog = false
        viewModelScope.launch {
            yield() // ← Дай на Compose шанс да обновча
            navController.navigate("main_screen")
        }
    }
)
```

Или още по-добре - използвай LaunchedEffect както показахме в Решение 1.

---

## Сравнение на решенията

| Решение | Сложност | Безопасност | Мащабируемост | Препоръка |
|---------|----------|-----------|-------------|-----------|
| **Решение 1** (LaunchedEffect) | Ниска | Висока | Висока | ✅ За повечето случаи |
| **Решение 2** (ViewModel) | Средна | Висока | Висока | ✅ За сложни сценарии |
| **Решение 3** (State Machine) | Висока | Много висока | Много висока | ✅ За критичния код |
| **Решение 4** (yield) | Очень ниска | Средна | Ниска | За quick fix |

---

## Тестване на решенията

```kotlin
// Едноставен тест
@Test
fun testPasswordDialogNavigation() {
    composeTestRule.setContent {
        val navController = rememberNavController()
        PasswordDialogScreen(navController)
    }

    // Покази dialog
    composeTestRule.onNodeWithText("Show Password Dialog").performClick()

    // Потвърди с парола
    composeTestRule.onNodeWithText("Password field").performTextInput("secret123")
    composeTestRule.onNodeWithText("Confirm").performClick()

    // Провери че е навигирано (няма race condition)
    composeTestRule.waitUntil(3000) {
        navController.currentBackStackEntry?.destination?.route == "main_screen"
    }
}
```

---

## Отладка на race conditions

```kotlin
// Добави logging за debug
@Composable
fun PasswordScreenDebug(navController: NavController) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var shouldNavigate by remember { mutableStateOf(false) }

    LaunchedEffect(showPasswordDialog) {
        println("DEBUG: Dialog state changed to $showPasswordDialog")
    }

    LaunchedEffect(shouldNavigate) {
        println("DEBUG: Navigation trigger = $shouldNavigate")
        if (shouldNavigate) {
            println("DEBUG: Navigating now...")
            navController.navigate("main_screen")
            shouldNavigate = false
        }
    }

    // ... rest of composable
}
```

---

## Актуални Compose версии (Ноември 2025)

Убедете се, че имате актуални зависимости:

```gradle
dependencies {
    // Jetpack Compose
    implementation "androidx.compose.ui:ui:1.7.0"
    implementation "androidx.compose.material3:material3:1.2.0"
    implementation "androidx.compose.runtime:runtime:1.7.0"
    
    // Navigation
    implementation "androidx.navigation:navigation-compose:2.8.0"
    
    // ViewModel & Lifecycle
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0"
    implementation "androidx.lifecycle:lifecycle-runtime-compose:2.8.0"
}
```

---

## Финални препоръки

1. **Используй решение 1** (LaunchedEffect) за новия код
2. **Замени delay()** с proper state-based timing
3. **Тества с различни устройства** за确认 няма timing issues
4. **Мониториор memory** с Android Profiler
5. **Използовай state machine** за критичния код в production

