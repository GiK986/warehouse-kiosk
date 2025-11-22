# Quick Reference: Jetpack Compose AlertDialog Best Practices

## 🎯 TL;DR - За занетите программисти

### Вашата грешка:
```kotlin
// ❌ ПРОБЛЕМ: delay() создает race condition
delay(100) // Line 46 в PasswordDialog.kt
```

### Решението в един ред:
```kotlin
// ✅ РЕШЕНИЕ: Замени delay() със LaunchedEffect
LaunchedEffect(shouldNavigate) { if (shouldNavigate) navController.navigate(...) }
```

---

## 📊 Быстрые таблицы

### Таблица 1: Какво избираш според твоя случай?

| Случай | Решение | Код |
|--------|---------|-----|
| **Прост диалог** | LaunchedEffect | Решение 1 ↓ |
| **Complex validation** | ViewModel + Flow | Решение 2 ↓ |
| **Critical UI** | State Machine | Решение 3 ↓ |
| **Quick fix** | yield() | Решение 4 ↓ |

### Таблица 2: Что вызывает race condition?

| Фактор | ❌ Грешно | ✅ Правилно |
|--------|----------|-----------|
| **Timing** | `delay(100)` | `LaunchedEffect` |
| **Input cleanup** | Липсва `onDispose` | `DisposableEffect { onDispose {} }` |
| **Dialog removal** | Преди navigation | След composition update |
| **State management** | Local `remember` | ViewModel `StateFlow` |
| **Navigation trigger** | Директна call | Event-based trigger |

### Таблица 3: Lifecycle events във Compose

```
TIMELINE:
┌─────────────────────────────────────────────────────────┐
│ showDialog = true                                       │
├─────────────────────────────────────────────────────────┤
│ AlertDialog composable се invocates                     │
│ Input listeners registered                             │
│ Dialog visible на screen                               │
├─────────────────────────────────────────────────────────┤
│ User clicks "Confirm"                                  │
│ showDialog = false                                     │
├─────────────────────────────────────────────────────────┤
│ Composition re-runs                                    │
│ AlertDialog удаляется from tree                        │
│ Input listeners unregistered (DisposableEffect)        │
│ Layout updates finalized                               │
├─────────────────────────────────────────────────────────┤
│ LaunchedEffect(showDialog) triggers                    │
│ NOW SAFE TO NAVIGATE!                                  │
│ navController.navigate()                               │
└─────────────────────────────────────────────────────────┘
```

---

## 🔧 Шпаргалка за копирай-пасирай

### Вариант 1: Минимална промяна (copy-paste ready)

```kotlin
// Твоя текущ код:
if (showPasswordDialog) {
    AlertDialog(
        onDismissRequest = { showPasswordDialog = false },
        confirmButton = {
            Button(onClick = {
                showPasswordDialog = false
                viewModelScope.launch {
                    delay(100) // ← ПРЕМАХНИ ТОВА!
                    navController.navigate("main_screen")
                }
            }) { Text("OK") }
        }
    )
}

// Преобразување в правилното решение:
var showPasswordDialog by remember { mutableStateOf(false) }
var shouldNavigate by remember { mutableStateOf(false) }

LaunchedEffect(shouldNavigate) {  // ← ДОБАВИ ТОВА
    if (shouldNavigate) {
        navController.navigate("main_screen")
        shouldNavigate = false
    }
}

if (showPasswordDialog) {
    AlertDialog(
        onDismissRequest = { showPasswordDialog = false },
        confirmButton = {
            Button(onClick = {
                showPasswordDialog = false
                shouldNavigate = true  // ← ЗАМЕНИ delay() с това
            }) { Text("OK") }
        }
    )
}
```

### Вариант 2: С DisposableEffect (за ресурси)

```kotlin
DisposableEffect(showPasswordDialog) {
    if (showPasswordDialog) {
        // Setup - dialog e visible
    }
    onDispose {
        // Cleanup - dialog се dispose
    }
}
```

### Вариант 3: С ViewModel (за сложни случаи)

```kotlin
class MyViewModel : ViewModel() {
    private val _navigate = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigate = _navigate.asSharedFlow()

    fun confirmAndNavigate(destination: String) {
        viewModelScope.launch {
            _navigate.emit(destination)
        }
    }
}

// В Composable:
LaunchedEffect(viewModel.navigate.collectAsStateWithLifecycle()) {
    viewModel.navigate.collect { destination ->
        navController.navigate(destination)
    }
}
```

---

## 🚨 Симптома / Диагноза таблица

| Симптом | Диагноза |治療 |
|----------|-----------|------|
| **Крах "Input channel disposed"** | Dialog removing race condition | Решение 1 или 2 |
| **Случайни crashes** | Timing-dependent | Обновча Compose версия |
| **Hang/freeze** | Main thread blocking | Използовай viewModelScope |
| **Memory leak** | Unregistered listeners | Добави DisposableEffect |
| **Double navigation** | State не се обновява правилно | Използовай StateFlow |
| **Input не работи** | Input listeners disposed | Чакай composition update |

---

## 📝 Чекълист за імплементация

### Пред промяната:
- [ ] Прочетох всички 3 документа
- [ ] Разбирам своя текущ код
- [ ] Имам backup на PasswordDialog.kt
- [ ] Знам на кой ред е проблемът (line 46)
- [ ] Имам тест за да проверя промяната

### След промяната:
- [ ] Заменихо delay() със LaunchedEffect
- [ ] Компилира без грешки
- [ ] Тестирах на физическо устройство
- [ ] Тестирах при быстрих операции
- [ ] Провера в Android Profiler
- [ ] Няма грешки в logcat
- [ ] Memory leak check е прошъл

### За production:
- [ ] Code review прошъл
- [ ] Добавихо logging/analytics
- [ ] Покрит с unit tests
- [ ] Тестирано на multiple devices
- [ ] Firebase Crashlytics integration
- [ ] Моніторирам performance metrics

---

## 🔍 Debugging tips

### Вкючи verbose logging:

```kotlin
// В вашия код добави:
android {
    defaultConfig {
        // ... 
    }
    
    // За debug builds
    if (BuildConfig.DEBUG) {
        logger.info("PasswordDialog debugging enabled")
    }
}
```

### Monitor в logcat:

```bash
# Филтрирай samo твоите logs
logcat | grep "PasswordDialog\|AlertDialog\|navigate"

# Намери crashes
logcat | grep "CRASH\|Exception\|Input channel"

# Timing analysis
logcat | grep "Button clicked\|Before navigate"
```

### Android Studio Debugger:

```
1. Set breakpoint на "showPasswordDialog = false"
2. Set breakpoint на "navController.navigate()"
3. Run with debugger (Shift+F9)
4. Inspect variable state при каждо breakpoint
5. Step through execution
```

---

## 📚 Документация referencias

### Official Android docs:
- https://developer.android.com/develop/ui/compose/components/dialog
- https://developer.android.com/jetpack/compose/state
- https://developer.android.com/jetpack/compose/side-effects

### Key takeaways за вас:
1. **AlertDialog е declarative** - контролирано от state
2. **Navigation трябва да идва СЛЕД state change** - не паралелно
3. **DisposableEffect трябва да cleanup** - или memory leak
4. **LaunchedEffect управлява timing** - по-добре от delay()
5. **ViewModel управлява state** - най-масштабируемо решение

---

## ⚡ Performance tips

### За оптимална production:

```kotlin
// 1. Минимизирай recompositions
val showDialog by viewModel.showDialog.collectAsState()  // Вместо remember

// 2. Memoize callbacks
val onDismiss = remember { { showDialog = false } }

// 3. Use efficient state management
private val _dialog = MutableStateFlow(DialogState.Hidden)  // Вместо mutableStateOf

// 4. Profile frequently
// Използовай Layout Inspector при разработка
// Проверявай recomposition count

// 5. Don't do heavy work in composition
// Преместете computation в ViewModel/Presenter
```

---

## 🎓 Най-важните концепции

### 1. **Composition** 
- Dialog composable е част от UI tree
- State промяна → recomposition
- Премахване от tree → cleanup

### 2. **Lifecycle Events**
- Enter composition → setup
- Leave composition → cleanup (onDispose)
- State change → recomposition

### 3. **Race Conditions**
- Възникват когато eventos не са синхронизирани
- Input channel disposal може да коловете navigation
- Решение: Синхронизирай със state changes

### 4. **Best Practice Pattern**
```
State Change (showDialog = false)
    ↓
Composition Update (AlertDialog removed)
    ↓
LaunchedEffect Trigger (if state changed)
    ↓
Safe Navigation (navController.navigate)
```

---

## 🆘 Ако все още има проблем

### Стъпки за решаване:

1. **Провери версиите** (трябвата Compose 1.7.0, Navigation 2.8.0)
2. **Добави logging** на всички ключни точки
3. **Прочетете диагностичния документ** (alertdialog_diagnostics.md)
4. **Тестирайте със всички решения** от password_dialog_solutions.kt
5. **Ако не работи** - качи stack trace в форумите със всички logs

### Полезни форуми:
- Stack Overflow: tag `android-jetpack-compose`
- Google Issue Tracker: `compose` project
- r/androiddev на Reddit

---

## 📊 Summary tabla

| Документ | За какво | Когда го читай |
|----------|----------|---|
| **compose_alertdialog_best_practices.md** | Теоретична база | Първо - за разбиране |
| **password_dialog_solutions.kt** | Практически примери | Второ - за избор на решение |
| **alertdialog_diagnostics.md** | Отладка и диагностика | Трето - ако има проблеми |
| **Този файл** | Quick reference | Винаги под ръка |

---

## ✅ Готов ли си?

### Запомни главното:
1. **❌ Премахни:** `delay(100)`
2. **✅ Добави:** `LaunchedEffect(shouldNavigate) { ... }`
3. **✅ Тества** на физическо устройство
4. **✅ Проверь** logcat за грешки
5. **✅ Готово!**

Дай си 15 минути и проблемът ще е решен! 💪

