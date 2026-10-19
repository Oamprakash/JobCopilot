---
title: "Jetpack Compose state and recomposition: the interview questions, with answers and code"
description: The Compose state questions Android interviews keep coming back to - remember, hoisting, stability, derivedStateOf, side effects - with short code and what a strong answer sounds like.
date: 2026-10-19
draft: false
---

If you're interviewing for an Android role in 2026, expect Compose. And within Compose, expect state: it's where the framework is most different from Views, and where a few minutes of questions show whether someone has shipped Compose screens or only followed tutorials.

These are the questions I find most useful when interviewing, with the kind of answer that convinces me. Each one has a short code example; type it out and run it once before your interview, because "I've seen this" and "I've debugged this" sound different.

## 1. "What causes a composable to recompose?"

A composable recomposes when **a state object it read during composition changes**. Compose tracks reads of `State` (for example from `mutableStateOf`) while a composable runs, and when that value changes it schedules only the scopes that read it to run again.

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    Button(onClick = { count++ }) {
        Text("Clicked $count times") // reads count, so this scope recomposes
    }
}
```

**What makes the answer strong:** mentioning that it's the *read* that matters, not where the state was created, and that Compose can **skip** a composable whose inputs haven't changed. Weak answers say "when the state changes, the screen redraws".

## 2. "What's the difference between `remember` and `rememberSaveable`?"

- `remember` keeps a value across **recompositions**. It's lost when the activity is recreated (rotation, for example) or the process dies.
- `rememberSaveable` also survives **configuration changes and process death**, by saving the value into the saved instance state. It works for types that fit in a `Bundle`; for anything else you give it a `Saver`.

```kotlin
var query by rememberSaveable { mutableStateOf("") } // survives rotation
val listState = rememberLazyListState()               // saveable under the hood
```

**Follow-up I usually ask:** "Would you put a whole screen's data in `rememberSaveable`?" The good answer is no: screen data belongs in a `ViewModel` (with `SavedStateHandle` for the small things that must survive process death); `rememberSaveable` is for UI element state like a text field or scroll position.

## 3. "What is state hoisting, and why bother?"

Moving state up to the caller so the composable becomes **stateless**: it receives the value and reports events.

```kotlin
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(value = query, onValueChange = onQueryChange)
}
```

Why: a single source of truth, composables that are easy to preview and test, and state that can be shared or moved into a `ViewModel` without touching the UI code. A strong answer also says *where* to hoist to: the lowest common parent that needs to read or change it, not automatically all the way to the top.

## 4. "This composable resets its value every time. Why?"

A classic debugging question:

```kotlin
@Composable
fun Broken() {
    var count by mutableStateOf(0)   // no remember
    Button(onClick = { count++ }) { Text("$count") }
}
```

Without `remember`, a new state object is created on every recomposition, so the count goes back to 0. The fix is `remember { mutableStateOf(0) }`. Good candidates spot this in seconds; it's one of the most common Compose bugs in real code reviews.

A related trap: `remember` without the right **key**. If the remembered value depends on a parameter, pass it as a key so it's recalculated when that parameter changes:

```kotlin
val formatted = remember(price, currency) { format(price, currency) }
```

## 5. "What does stability mean, and why can a `List` parameter cause extra recompositions?"

Compose skips a composable when its parameters are unchanged, but it needs to know it can trust a type's `equals` to decide that. Types it can trust are **stable**: primitives, `String`, and classes whose public properties are `val`s of stable types, or that you mark with `@Immutable` or `@Stable`.

`List<T>` is an interface, so the compiler can't be sure the instance isn't a mutable list underneath, and treats it as **unstable**. Before strong skipping, a composable taking a `List` often couldn't be skipped at all.

**The up-to-date part of a strong answer:** with **strong skipping mode** (on by default in the Compose compiler that ships with Kotlin 2.0.20 and later), composables with unstable parameters *can* be skipped; unstable parameters are compared by instance (`===`) instead of `equals`. So the practical advice today is: keep passing the same list instance when nothing changed, or use immutable collections, and measure with the Layout Inspector's recomposition counts before annotating everything.

## 6. "When would you use `derivedStateOf`?"

When a state changes **often**, but the value you compute from it changes **rarely**. The classic example: showing a "scroll to top" button.

```kotlin
val listState = rememberLazyListState()
val showScrollToTop by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 0 }
}
```

`firstVisibleItemIndex` changes on every scroll; the boolean only flips occasionally. `derivedStateOf` means readers of `showScrollToTop` recompose only when the boolean actually changes.

**Watch out for the opposite mistake**, which I see a lot: wrapping something that changes as often as its input, like `derivedStateOf { "$firstName $lastName" }`. That adds overhead and saves nothing; a plain calculation (or `remember` with keys) is better.

## 7. "How do you run a side effect from a composable?"

Composables can run many times, in any order, and may be abandoned, so work that touches the outside world goes into effect APIs:

- `LaunchedEffect(key)`: run a suspend function when the composable enters, and restart it when the key changes.
- `DisposableEffect(key)`: register something and clean it up in `onDispose`.
- `rememberUpdatedState`: let a long-running effect use the latest value of a lambda or parameter without restarting.

```kotlin
@Composable
fun Toast(message: String, onShown: () -> Unit) {
    val latestOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(message) {
        delay(3_000)
        latestOnShown()
    }
}
```

**A strong answer** explains why `onShown` isn't a key here: we don't want to restart the 3-second timer just because the caller passed a new lambda.

## 8. "How do you collect a Flow in Compose?"

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

`collectAsStateWithLifecycle` (from `lifecycle-runtime-compose`) stops collecting when the app goes to the background, which `collectAsState` doesn't. Mentioning that difference, and why it matters for battery and for upstream flows doing work, is what separates a good answer from a memorised one.

## 9. Bonus: "How would you stop an animation from recomposing the whole screen?"

Read fast-changing state as late as possible. Compose has three phases: composition, layout and drawing. If a value is only needed for layout or drawing, read it there:

```kotlin
// Recomposes on every frame:
Box(Modifier.offset(y = offsetY.value.dp))

// Only re-runs layout, no recomposition:
Box(Modifier.offset { IntOffset(0, offsetY.value.roundToInt()) })
```

Candidates who know the lambda versions of modifiers (`offset { }`, `graphicsLayer { }`, `drawBehind { }`) usually have real performance work behind them.

## How to prepare in an evening

1. Build one small screen with a text field, a list and a button, using hoisted state and a `ViewModel`.
2. Turn on recomposition counts in the Layout Inspector and make something recompose too often, then fix it.
3. Practise saying each answer above out loud in two or three sentences. Interviews reward clear explanations as much as correct ones.

If you're applying for Android roles now, [check your resume against a real Android job](/#try): it shows which skills the job asks for that your resume doesn't show yet, in about a minute.

{{cta}}
