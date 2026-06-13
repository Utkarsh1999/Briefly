# Engineering notes — what shipping an on-device LLM actually looked like

Briefly runs a Qwen2.5-1.5B model on-device through llama.cpp. This doc records
three problems I hit getting it working on real hardware, what the symptom
looked like, what the cause turned out to be, and where it lives in the code.

Test device: **OnePlus CPH2707** (Android 16), CPU-only inference.

---

## 1. The capability check — why it exists, and why caching its verdict is risky

### Symptom

App home screen was empty. No summary card, no error, no spinner. Logs showed
the model wasn't being called at all:

```
06-08 11:06:12  D Summarizer: --- JSON OUTPUT ---
06-08 11:06:12  W Summarizer: Device marked incapable of on-device inference; skipping LLM call.
```

The warning comes from
[`NotificationSummarizer.kt:32`](app/src/main/java/com/notifyai/ai/summarizer/NotificationSummarizer.kt#L32),
inside the early-exit guarded by `llmEngine.isDeviceCapable.value == false`
([line 31](app/src/main/java/com/notifyai/ai/summarizer/NotificationSummarizer.kt#L31)).

### Why the check exists

The codebase runs a one-time benchmark on first model load —
[`probeDeviceCapabilityIfNeeded`](app/src/main/java/com/notifyai/ai/engine/LlamaCppEngine.kt#L175)
in `LlamaCppEngine.kt`. The reason isn't UX polish: Android's runtime kills
processes whose threads don't yield control back to the OS within a fixed
window. Long-running native compute (which is exactly what LLM inference is) is
a textbook trigger for that. The probe runs a tiny bounded workload
(`BENCH_PROMPT_TOKENS = 16`, `BENCH_GEN_TOKENS = 16`) and decides whether the
device can run real inference fast enough to avoid getting the process killed.

If the elapsed time exceeds `BENCH_THRESHOLD_MS`
([line 211](app/src/main/java/com/notifyai/ai/engine/LlamaCppEngine.kt#L211)),
the device gets flagged incapable and the summarizer falls back to a
placeholder result.

### The hidden problem: the verdict is cached forever

The probe persists its verdict to `SharedPreferences`
([lines 191–195](app/src/main/java/com/notifyai/ai/engine/LlamaCppEngine.kt#L191)):

```kotlin
prefs.edit()
    .putBoolean(PREF_KEY_IS_DEVICE_CAPABLE, capable)
    .putLong(PREF_KEY_BENCHMARKED_AT, System.currentTimeMillis())
    .apply()
```

There's no expiry, no re-probe path. Once a device is marked too slow, it
stays marked — for the lifetime of the install. That means a single noisy
benchmark run (thermal throttling, a CPU-heavy app in the background at probe
time, anything transient) can lock a device out forever, even after the actual
conditions change.

To recover during development I had to clear the prefs manually:

```bash
adb shell run-as com.notifyai.debug \
  rm /data/data/com.notifyai.debug/shared_prefs/notifyai_llm_capability.xml
```

A real fix would be: expire the verdict after N days, re-probe on app upgrade,
or expose a "re-check this device" button. None of that exists yet.

### Takeaway

Capability gates on mobile AI are load-bearing, not cosmetic — but persisting
a one-shot benchmark result indefinitely is the wrong default. Treat the
verdict as a cache with a TTL, not a fact.

---

## 2. The gap between "technically can run" and "should run"

After the silent-skip was understood, the obvious move was to relax the
threshold so the test device could pass the probe. I raised
`BENCH_THRESHOLD_MS` from 30s to 90s
([LlamaCppEngine.kt:211](app/src/main/java/com/notifyai/ai/engine/LlamaCppEngine.kt#L211))
and cleared the cached verdict.

The device passed:

```xml
<!-- shared_prefs/notifyai_llm_capability.xml after re-probe -->
<boolean name="is_device_capable" value="true" />
```

The app started actually generating tokens. Output, one token at a time:

```
11:09:13.680  V Summarizer: token received: {
11:09:15.651  V Summarizer: token received:
11:09:17.624  V Summarizer: token received:  "
11:09:21.598  V Summarizer: token received: important
11:09:23.609  V Summarizer: token received: ":
11:09:25.614  V Summarizer: token received:  ["
11:09:27.580  V Summarizer: token received: 3
```

That's roughly **2 seconds per token**.

I let it keep running. After ~20 minutes, the app process was still grinding:

```
$ adb shell dumpsys cpuinfo
424% 7752/com.notifyai.debug: 413% user + 11% kernel / faults: 4641 minor 406 major

$ adb shell ps -T -p 7752
... TIME: 21:38.75  com.notifyai.debug
```

So: ~420–455% CPU sustained, 21+ minutes of accumulated CPU time, **zero
finished summaries**. While that was happening, the rest of the OS started
visibly degrading. Two warnings showed up repeatedly in logcat:

- **SurfaceFlinger:** `Out of order buffers detected for RequestedLayerState{...}`
  — the compositor was missing frames because the GPU/main thread couldn't be
  scheduled in time.
- **ActivityManager:** `Freezer deadlock watchdog` — the system process
  freezer was getting starved.

Both are symptoms of CPU starvation across the whole device, not of a bug in
the app. The model was technically running. It just wasn't a reasonable thing
to ask the phone to do.

### Takeaway

"Passes the capability gate" and "produces a usable experience" are different
bars. The threshold should be calibrated to *good UX under realistic load*,
not to *the workload finishes eventually*. For this device, with this model,
on CPU only, the answer is that the model is too heavy regardless of how
patient the gate is.

The real fixes are downstream: a smaller model, GPU/NPU delegation, or both.
Loosening the threshold without those is just letting the device fail more
expensively.

---

## 3. The APK was ~1 GB. It didn't need to be.

The model file was being shipped inside the APK, as a raw asset:

```
app/src/main/assets/models/qwen2.5-1.5b-instruct-q4_k_m.gguf   1.0 GB
```

That alone is the dominant cost. There were also some unused llama.cpp
binaries shipping that the app never calls into:

```
app/src/main/assets/llama/libllama-server-impl.so   64 MB   (unused)
app/src/main/assets/llama/libllama-cli-impl.so      20 MB   (unused)
app/src/main/assets/llama/llama-server               8 KB   (unused)
```

### The fix

Two changes:

1. **Deleted the unused server/CLI binaries** — straight removal, no code
   changes needed.
2. **Moved the model out of assets and into a one-time download on first run**
   — see [`ModelDownloader.kt`](app/src/main/java/com/notifyai/ai/engine/ModelDownloader.kt).

The downloader streams the GGUF from a CDN into `filesDir/notifyai-model.gguf.part`,
then renames atomically to `notifyai-model.gguf` on success
([ModelDownloader.kt:51–119](app/src/main/java/com/notifyai/ai/engine/ModelDownloader.kt#L51)).
That way an interrupted download can never be mistaken for a complete model on
the next launch — the loader keys off the final filename. Progress is exposed
as a `StateFlow<State>` and surfaced in the UI via a download card
([HomeScreen.kt:262](app/src/main/java/com/notifyai/ui/home/HomeScreen.kt#L262))
so the first-run wait isn't a blank screen.

`LlamaCppEngine.loadModelLocked` was updated to call
`modelDownloader.ensureModelDownloaded(modelFile)` before
`eng.loadModel(...)`, in place of the old asset-copy path.

### Result

| | Before | After |
|---|---|---|
| Assets directory | 1.3 GB | 150 MB |
| Built debug APK | ~1.0 GB | **114 MB** |

An 88% drop, none of it from clever compression — just from not shipping
things that don't belong in an APK.

### Takeaway

Bundling models in assets feels convenient during prototyping but is the
wrong default the moment you think about distribution: every user pays the
download/storage cost on install, including users whose device can't run the
model anyway. On-demand download with a visible progress UI is the production
pattern.

---

## Where this leaves things

The on-device summarizer doesn't actually produce useful output on the test
device yet. The 1.5B model is too heavy for CPU-only inference on this
hardware — the work to fix that is downstream from anything above.

Next iteration is one of:

- A smaller model (e.g. Qwen2.5-0.5B) in the same Q4 quant
- GPU/NPU delegation via a Vulkan or OpenCL llama.cpp backend (none currently
  bundled — confirm via `ls app/src/main/assets/llama/`)
- Both

The capability probe's "cache forever" behavior should also be fixed
regardless of which model ships.

---

## Reproducing the timeline

If you want to verify the claims above on your own hardware:

```bash
# 1. Wipe the cached capability verdict
adb shell run-as com.notifyai.debug \
  rm /data/data/com.notifyai.debug/shared_prefs/notifyai_llm_capability.xml

# 2. Relaunch and watch the probe + generation
adb shell am force-stop com.notifyai.debug
adb shell monkey -p com.notifyai.debug -c android.intent.category.LAUNCHER 1
adb logcat -s 'Summarizer:*' 'InferenceEngineImpl:*'

# 3. While generation is running, observe sustained CPU on the app process
adb shell dumpsys cpuinfo | grep notifyai
```

A device that finishes a summary in a reasonable time will show the
`generation collect completed, output len=...` line in logcat from
[LlamaCppEngine.kt:138](app/src/main/java/com/notifyai/ai/engine/LlamaCppEngine.kt#L138).
A device that can't, won't.
