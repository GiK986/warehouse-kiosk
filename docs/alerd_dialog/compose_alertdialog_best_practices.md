# Jetpack Compose AlertDialog: Best Practices за Lifecycle, Race Conditions и Navigation

## 📋 Съдържание
1. [Обяснение на грешката](#обяснение-на-грешката)
2. [Причини за грешката](#причини-за-грешката)
3. [Best Practices за избягване](#best-practices-за-избягване)
4. [Code Patterns и решения](#code-patterns-и-решения)
5. [Алтернативи на delay()](#алтернативи-на-delay)
6. [Стратегии за управление на Dialog lifecycle](#стратегии-за-управление-на-dialog-lifecycle)

---

## 1. Обяснение на грешката

### Съобщението за грешка:
```
"Input channel object was disposed without first being removed with the input manager"
```

### Какво означава:
Грешката възникава, когато input channel (входна комуникация между компоненти) бива освободена от памятта, докато все още е регистрирана с inputManager. Това се случва в следния сценарий:

1. **AlertDialog е активен и получава input events** (keyboard events, touch events)
2. **Composition се променя** (dialog state설定 false, navigation)
3. **Dialog бива премахнат от composable tree** преди input manager да го деregистрира правилно
4. **Input channel се dispose** докато все още е регистриран - **RACE CONDITION**

### Визуално:
```
Timeline на race condition:

T1: Dialog открит → Input Channel создаден & регистриран
T2: onDismissRequest() извикана → showDialog = false
T3: Dialog почва animation/removal (100ms delay)
T4: Navigation команда → navController.navigate() 
T5: Композиция се преглася → Dialog се премахва от tree
T6: Input channel seeks to unregister (в background thread)
T7: Но navigationController вече е уничтожил скрина!
T8: CRASH: Input channel disposed without unregistration

Проблем: T3-T8 интервалът создава race condition
```

---

## 2. Причини за грешката

### A. **Navigation преди Dialog cleanup**
```kotlin
// ❌ ГРЕШНО: Navigation без чакане на dialog cleanup
AlertDialog(
    onDismissRequest = {
        showDialog = false
        navController.navigate("next_screen") // Твърде рано!
    },
    // ...
)
```

**Причина:** Dialog по-има input listeners регистрирани. Когато navegате веднага, Compose garbage-collectы dialog resources докато listeners все още са активни.

### B. **Неправилна управление на DisposableEffect**
```kotlin
// ❌ ГРЕШНО: Липсва cleanup в onDispose
DisposableEffect(Unit) {
    // Някакви ресурси/слушатели
    someManager.registerListener(listener)
    // Липсва onDispose блок! Ресурсът никога не бива деregистриран
}
```

### C. **Timing мисматч между animation и navigation**
```kotlin
// ❌ ГРЕШНО: delay() не синхронизира правилно
Button(
    onClick = {
        showDialog = false
        // 100ms delay преди navigation
        viewModelScope.launch {
            delay(100) // Достатъчен ли е этот delay?
            navController.navigate("next")
        }
    }
)
```

**Проблем:** 
- 100ms delay може да не е достатъчно на всички устройства
- Устойчивост на натиск/быстри операции
- Системни задържане за GC на някой точке

### D. **Input events докато Dialog се dispose**
```kotlin
// Событие от keyboard може да пристигне докато:
// 1. Dialog è в процес на removal
// 2. Input channels се dispose
// 3. InputManager все още чака unregister
```

### E. **Основната причина - Compose architecture:**

В Jetpack Compose, когато composition се променя, трябва да се управлява lifecycle на composables включително техните ресурси. DialogПросто вкупен фенка, който зависи от:
- **State** (showDialog boolean)
- **Input handlers** (onDismissRequest, button clicks)
- **Lifecycle effects** (DisposableEffect, LaunchedEffect)

Ако не управляваме правилно тези слои, възникват race conditions.

---

## 3. Best Practices за избягване

### ✅ **Best Practice 1: Single Source of Truth в ViewModel**

```kotlin
class PasswordDialogViewModel : ViewModel() {
    private val _dialogState = MutableStateFlow<DialogState>(DialogState.Hidden)
    val dialogState = _dialogState.asStateFlow()

    fun showDialog() {
        _dialogState.value = DialogState.Visible
    }

    fun dismissDialog() {
        _dialogState.value = DialogState.Hidden
    }

    fun navigateAfterDismiss(navigationCallback: () -> Unit) {
        dismissDialog()
        viewModelScope.launch {
            // Чаке composition да се обновча, после navigate
            navigationCallback()
        }
    }

    sealed class DialogState {
        object Hidden : DialogState()
        object Visible : DialogState()
        object Dismissing : DialogState() // Ново състояние!
    }
}
```

**Защо работи:**
- Всички состояния управлявани на едно място
- Composition може да следи state changes правилно
- Lifecycle events синхронизирани с ViewModel lifecycle

### ✅ **Best Practice 2: Правилна употреба на DisposableEffect**

```kotlin
@Composable
fun PasswordDialogScreen(
    viewModel: PasswordDialogViewModel = viewModel()
) {
    val dialogState by viewModel.dialogState.collectAsState()

    // ✅ ПРАВИЛНО: DisposableEffect за cleanup
    DisposableEffect(dialogState) {
        // Инициализация при промяна на dialogState
        if (dialogState == DialogState.Visible) {
            // Setup input listeners, keyboard listeners и т.н.
            setupDialogListeners()
        }

        onDispose {
            // КРИТИЧНО: Очистка на всички ресурси!
            if (dialogState != DialogState.Hidden) {
                cleanupDialogListeners()
            }
        }
    }

    when (dialogState) {
        DialogState.Hidden -> {
            // Dialog не се показва
        }
        DialogState.Visible -> {
            PasswordAlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                onConfirm = { 
                    viewModel.navigateAfterDismiss {
                        navController.navigate("next_screen")
                    }
                }
            )
        }
        DialogState.Dismissing -> {
            // Transition state за animation
        }
    }
}

private fun setupDialogListeners() {
    // Например: регистрирай keyboard listener
    val listener = KeyboardListener { ... }
    InputManager.registerListener(listener)
}

private fun cleanupDialogListeners() {
    // ВАЖНО: Деregистрирай всички listeners!
    InputManager.unregisterListener(listener)
}
```

**Ключови моменти:**
- DisposableEffect зависи от `dialogState`
- onDispose се вика гарантирано при смяна на key
- Cleanup логика е **най-важната част**

### ✅ **Best Practice 3: LaunchedEffect за navigation timing**

```kotlin
@Composable
fun PasswordDialogScreen(
    viewModel: PasswordDialogViewModel = viewModel(),
    navController: NavController
) {
    val dialogState by viewModel.dialogState.collectAsState()
    val navigationEvent by viewModel.navigationEvent.collectAsState()

    // ✅ ПРАВИЛНО: LaunchedEffect за navigation
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { destination ->
            // Compose гарантира, че dialog е already removed от composition
            // преди този код да се изпълни
            navController.navigate(destination)
        }
    }

    // Dialog UI...
}
```

**Защо работи:**
- LaunchedEffect автоматично отказва coroutine когато composable е премахнат от composition
- Navigation се вика СЛЕД composition update
- Няма risk от race condition

### ✅ **Best Practice 4: Proper DialogProperties**

```kotlin
// ✅ ПРАВИЛНО: Контролирай dialog properties
AlertDialog(
    onDismissRequest = { showDialog = false },
    title = { Text("Password") },
    text = { PasswordField() },
    confirmButton = {
        Button(
            onClick = {
                // Dismiss dialog ПЪРВО, после navigate
                showDialog = false
                // Navigation ще се обработи във separate effect
            }
        ) { Text("Submit") }
    },
    dismissButton = {
        Button(onClick = { showDialog = false }) { Text("Cancel") }
    },
    properties = DialogProperties(
        // Контролирай кога dialog може да бъде dismissed
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        // Опционално: предотврати dismiss по определени условия
        // dismissOnBackPress = false, // ако validation е в прогрес
    )
)
```

### ✅ **Best Practice 5: Animation-aware dismissal**

```kotlin
@Composable
fun PasswordAlertDialogWithAnimation(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        label = "PasswordDialogAnimation"
    ) {
        AlertDialog(
            onDismissRequest = {
                // Animation會 handle the exit
                // No manual delay needed!
                onDismissRequest()
            },
            // ...
        )
    }
}
```

**Защо работи:**
- AnimatedVisibility гарантира всички animations са завършили преди cleanup
- Няма нужда от manual delay()

---

## 4. Code Patterns и решения

### Pattern 1: ViewModel-based Dialog Management (ПРЕПОРЪЧАНО)

```kotlin
// ViewModel
class PasswordDialogViewModel : ViewModel() {
    private val _showDialog = MutableStateFlow(false)
    val showDialog = _showDialog.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    fun showDialog() {
        _showDialog.value = true
    }

    fun dismissDialog() {
        _showDialog.value = false
    }

    fun confirmPassword(password: String) {
        // Валидиране на парола...
        dismissDialog()
        
        viewModelScope.launch {
            // Composition ще се обновча след dismissDialog()
            // Това е най-добрата точка за navigation
            _navigationEvent.emit("next_screen")
        }
    }
}

// Composable
@Composable
fun PasswordScreen(
    viewModel: PasswordDialogViewModel = viewModel(),
    navController: NavController
) {
    val showDialog by viewModel.showDialog.collectAsState()
    val navigationEvent = viewModel.navigationEvent.collectAsStateWithLifecycle()

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { navController.navigate(it) }
    }

    Column {
        Button(onClick = { viewModel.showDialog() }) {
            Text("Show Password Dialog")
        }

        if (showDialog) {
            PasswordAlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                onConfirm = { password ->
                    viewModel.confirmPassword(password)
                }
            )
        }
    }
}
```

### Pattern 2: Navigation-aware Dialog

```kotlin
@Composable
fun PasswordScreen(navController: NavController) {
    var showDialog by remember { mutableStateOf(false) }

    // ✅ ПРАВИЛНО: Използвай NavBackStackEntry за state management
    val navBackStackEntry = rememberNavBackStackEntry()
    
    // Чакай на composition update ПРЕДИ navigation
    LaunchedEffect(showDialog) {
        if (!showDialog) {
            // Dialog е byt dismissed, безпечно е да navigate
            // Но НЕ веднага - чакай един composition cycle
            yield() // Дай време на Compose да обработи промяната
            navController.navigate("next_screen")
        }
    }

    if (showDialog) {
        PasswordAlertDialog(
            onDismissRequest = { showDialog = false },
            onConfirm = { showDialog = false }
        )
    }
}
```

### Pattern 3: State Machine for Dialog Lifecycle

```kotlin
sealed class DialofState {
    object Hidden : DialogState()
    object Visible : DialogState()
    object Animating : DialogState() // Animating dismiss
    data class Navigating(val destination: String) : DialogState()
}

@Composable
fun DialogWithStateMachine() {
    var state by remember { mutableStateOf<DialogState>(DialogState.Hidden) }

    // Handle navigation after state settles
    LaunchedEffect(state) {
        if (state is DialogState.Navigating) {
            val destination = (state as DialogState.Navigating).destination
            navController.navigate(destination)
            state = DialogState.Hidden
        }
    }

    when (state) {
        DialogState.Hidden -> {
            Button(onClick = { state = DialogState.Visible }) {
                Text("Show")
            }
        }

        DialogState.Visible -> {
            AlertDialog(
                onDismissRequest = { state = DialogState.Hidden },
                confirmButton = {
                    Button(
                        onClick = {
                            state = DialogState.Navigating("next_screen")
                        }
                    ) { Text("Confirm") }
                }
            )
        }

        DialogState.Animating -> {
            // Transition state
        }

        is DialogState.Navigating -> {
            // Will be handled by LaunchedEffect above
        }
    }
}
```

---

## 5. Алтернативи на delay()

### ❌ Проблем с delay():

```kotlin
// ❌ ГРЕШНО: delay() е hack, не решение
Button(
    onClick = {
        showDialog = false
        viewModelScope.launch {
            delay(100) // Майчата сна нужен delay!
            navController.navigate("next")
        }
    }
)
```

**Защо е лошо:**
- Arbitrary magic number (100ms)
- Не гарантира completion на composition
- Различни устройства имат различни timing
- Не излиза на чист и масштабируем код

### ✅ Alternative 1: Composition snapshotting

```kotlin
// ✅ ПРАВИЛНО: Чакай композицията да се обновча
@Composable
fun PasswordDialog(navController: NavController) {
    var showDialog by remember { mutableStateOf(false) }
    var shouldNavigate by remember { mutableStateOf(false) }

    // Composite lifecycle-aware navigation
    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            // По този момент, composition вече е обновена
            // Dialog е byt premахнат
            navController.navigate("next_screen")
            shouldNavigate = false
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        shouldNavigate = true // Този флаг ще trigger LaunchedEffect
                    }
                ) { Text("OK") }
            }
        )
    }
}
```

**Как работи:**
1. showDialog = false → Compose recomposes
2. AlertDialog бива премахнат от composition
3. shouldNavigate = true → LaunchedEffect се trigger
4. navController.navigate() вече е безопасна операция

### ✅ Alternative 2: Coroutine.yield()

```kotlin
@Composable
fun PasswordDialog(navController: NavController) {
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showDialog) {
        if (!showDialog) {
            yield() // Даватиremove корутина контрол на scheduler
            // Composition е гарантирано обновена
            navController.navigate("next_screen")
        }
    }

    if (showDialog) {
        AlertDialog(
            // ...
        )
    }
}
```

**Предимства на yield():**
- Повече portable от delay()
- Гарантира composition update
- Без magic numbers

### ✅ Alternative 3: MutableSharedFlow for events

```kotlin
class PasswordDialogViewModel : ViewModel() {
    private val _dialogDismissed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dialogDismissed = _dialogDismissed.asSharedFlow()

    fun dismissAndNavigate() {
        viewModelScope.launch {
            _dialogDismissed.emit(Unit) // Broadcast event
        }
    }
}

@Composable
fun PasswordScreen(
    viewModel: PasswordDialogViewModel,
    navController: NavController
) {
    val dismissEvent = viewModel.dialogDismissed.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(dismissEvent.value) {
        dismissEvent.value?.let {
            // Event emitted means dialog dismissed
            navController.navigate("next_screen")
        }
    }

    // Dialog code...
}
```

**Предимства:**
- Reactive pattern
- Decoupled state management
- Легко за тестване

### ✅ Alternative 4: Explicit animation completion callback

```kotlin
@Composable
fun PasswordDialogWithCallback(
    show: Boolean,
    onDismissComplete: () -> Unit,
    onConfirm: () -> Unit
) {
    var isVisible by remember { state = show }
    
    LaunchedEffect(show) {
        isVisible = show
        if (!show) {
            // Wait for animation to complete
            delay(300) // Dialog exit animation duration
            onDismissComplete() // Now safe to navigate
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut() + slideOutVertically()
    ) {
        AlertDialog(
            onDismissRequest = { isVisible = false },
            // ...
        )
    }
}

// Usage
PasswordDialogWithCallback(
    show = showDialog,
    onDismissComplete = {
        navController.navigate("next_screen")
    },
    onConfirm = { password ->
        showDialog = false
    }
)
```

---

## 6. Стратегии за управление на Dialog lifecycle

### Стратегия 1: Complete Dialog Lifecycle Model

```kotlin
enum class DialogLifecycleState {
    HIDDEN,           // Dialog е скрит
    APPEARING,        // Animating in
    VISIBLE,          // Fully visible
    DISMISSING,       // Animating out
    DISPOSED          // Fully disposed (ready for navigation)
}

class DialogLifecycleManager(
    private val initialState: DialogLifecycleState = DialogLifecycleState.HIDDEN
) {
    private val _state = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    fun show() {
        _state.value = DialogLifecycleState.APPEARING
    }

    fun dismiss() {
        _state.value = DialogLifecycleState.DISMISSING
    }

    fun onAnimationComplete() {
        if (_state.value == DialogLifecycleState.DISMISSING) {
            _state.value = DialogLifecycleState.DISPOSED
        } else if (_state.value == DialogLifecycleState.APPEARING) {
            _state.value = DialogLifecycleState.VISIBLE
        }
    }

    fun isReadyForNavigation(): Boolean {
        return _state.value == DialogLifecycleState.DISPOSED
    }
}

// Usage in Composable
@Composable
fun DialogWithLifecycleAwareness(
    lifecycleManager: DialogLifecycleManager,
    navController: NavController
) {
    val state by lifecycleManager.state.collectAsState()

    // Only navigate when dialog is fully disposed
    LaunchedEffect(state) {
        if (state == DialogLifecycleState.DISPOSED) {
            navController.navigate("next_screen")
        }
    }

    when (state) {
        DialogLifecycleState.HIDDEN -> { /* Empty */ }
        DialogLifecycleState.APPEARING,
        DialogLifecycleState.VISIBLE,
        DialogLifecycleState.DISMISSING -> {
            AnimatedVisibility(
                visible = state != DialogLifecycleState.DISPOSED,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                onAnimationCompletion = {
                    lifecycleManager.onAnimationComplete()
                }
            ) {
                AlertDialog(
                    onDismissRequest = { lifecycleManager.dismiss() },
                    // ...
                )
            }
        }
        DialogLifecycleState.DISPOSED -> { /* Empty */ }
    }
}
```

### Стратегия 2: Resource Cleanup with DisposableEffect

```kotlin
@Composable
fun DialogWithProperResourceManagement(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    // Track dialog resources
    var inputListener: InputListener? = remember { null }
    var keyboardListener: KeyboardListener? = remember { null }

    DisposableEffect(show) {
        if (show) {
            // Initialize resources
            inputListener = InputListener { /* handle */ }.also {
                InputManager.register(it)
            }
            keyboardListener = KeyboardListener { /* handle */ }.also {
                KeyboardManager.register(it)
            }
        }

        onDispose {
            // Cleanup resources GUARANTEED
            inputListener?.let { InputManager.unregister(it) }
            keyboardListener?.let { KeyboardManager.unregister(it) }
            inputListener = null
            keyboardListener = null
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            // ...
        )
    }
}
```

---

## 7. Практически примери по ваш случай

### Ваш текущ код (PasswordDialog.kt):

```kotlin
// ❌ ТЕКУЩО (с потенциален проблем):
Button(
    onClick = {
        showPasswordDialog = false
        viewModelScope.launch {
            delay(100) // Line 46 - Проблемът
            navController.navigate(...)
        }
    }
)
```

### Решение 1: Использовать LaunchedEffect

```kotlin
// ✅ РЕШЕНИЕ 1:
@Composable
fun PasswordDialog(
    navController: NavController,
    viewModel: PasswordViewModel = viewModel()
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var shouldNavigate by remember { mutableStateOf(false) }

    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            navController.navigate("destination")
            shouldNavigate = false
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        // Dismiss dialog first
                        showPasswordDialog = false
                        // Then trigger navigation (in separate effect)
                        shouldNavigate = true
                    }
                ) { Text("Confirm") }
            }
        )
    }
}
```

### Решение 2: Използовать State Machine

```kotlin
// ✅ РЕШЕНИЕ 2:
sealed class PasswordDialogState {
    object Hidden : PasswordDialogState()
    object Visible : PasswordDialogState()
    data class Confirming(val password: String) : PasswordDialogState()
    object Navigating : PasswordDialogState()
}

class PasswordViewModel : ViewModel() {
    private val _dialogState = MutableStateFlow<PasswordDialogState>(PasswordDialogState.Hidden)
    val dialogState = _dialogState.asStateFlow()

    fun showDialog() {
        _dialogState.value = PasswordDialogState.Visible
    }

    fun confirmPassword(password: String) {
        _dialogState.value = PasswordDialogState.Confirming(password)
        // Validation и navigation логика
        _dialogState.value = PasswordDialogState.Navigating
    }
}

@Composable
fun PasswordScreen(
    viewModel: PasswordViewModel = viewModel(),
    navController: NavController
) {
    val state by viewModel.dialogState.collectAsState()

    LaunchedEffect(state) {
        if (state == PasswordDialogState.Navigating) {
            navController.navigate("next_screen")
        }
    }

    when (state) {
        PasswordDialogState.Hidden -> {
            Button(onClick = { viewModel.showDialog() }) { Text("Login") }
        }

        PasswordDialogState.Visible -> {
            PasswordAlertDialog(
                onDismissRequest = { viewModel.showDialog() },
                onConfirm = { password -> viewModel.confirmPassword(password) }
            )
        }

        else -> {} // Confirming, Navigating states
    }
}
```

### Решение 3:완полноценно управление с DisposableEffect

```kotlin
// ✅ РЕШЕНИЕ 3:
@Composable
fun PasswordDialogComplete(
    navController: NavController,
    onNavigate: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    DisposableEffect(showDialog) {
        if (showDialog) {
            // Setup
            println("Dialog shown - setting up resources")
        }

        onDispose {
            // Cleanup
            if (!showDialog) {
                println("Dialog dismissed - cleaning up resources")
                // At this point, it's safe to navigate
                // But do it through callback to avoid direct call in onDispose
            }
        }
    }

    LaunchedEffect(showDialog) {
        if (!showDialog) {
            // Dialog dismissed, composition updated
            // Now safe to navigate
            yield() // Ensure composition is fully updated
            onNavigate("next_screen")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                Button(onClick = { showDialog = false }) { Text("OK") }
            }
        )
    }
}
```

---

## Резюме на препоръки

| Проблем | Решение | Причина |
|---------|---------|---------|
| Race condition при navigate | Използовай LaunchedEffect със state change | Гарантира composition update преди navigation |
| Липсва cleanup на ресурси | DisposableEffect с onDispose блок | Гарантира освобождаване на ресурси |
| Delay() е непредсказуем | Използовай state-based navigation trigger | Композиция управлява timing автоматично |
| Keyboard events след dismiss | Proper DialogProperties + cleanup | Спира input events преди removal |
| Memory leaks | ViewModel-based state management | Lifecycle-aware state handling |

---

## Официални препоръки от Android docs

AlertDialog в Jetpack Compose предоставя параметри за обработка на dismiss действия, включително onDismissRequest, която трябва да се използва за управление на диалоговото състояние.

Централизирането на диалоговото състояние в ViewModel или shared state holder помага да се избегне inconsistent UI behavior.

