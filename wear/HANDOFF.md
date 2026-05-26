# SoundGuard — Documentație tehnică completă (Hackathon Google România, 24h)

> **Un companion de conștientizare audio pentru utilizatorii de căști.**
> Ceasul captează microfonul și streamează audio brut spre telefon. Telefonul rulează YAMNet local
> (MediaPipe Tasks Audio), clasifică sunetele și trimite alerte structurate înapoi pe ceas.
> Ceasul redă un overlay fullscreen + haptice specifice per clasă de sunet.
> Construit pentru **Health · Safety · Accessibility** — toate trei, pe fiecare ecran.

---

## 1. Pitch în 3 propoziții

SoundGuard transformă ceasul tău smartwatch într-un microfon ambient always-on și telefonul într-un creier care clasifică ce aude. Când YAMNet (rulând complet local pe telefon, via MediaPipe Tasks Audio) detectează o sirenă, alarmă de incendiu, strigăt de ajutor, claxon, plâns de bebeluș, geam spart sau lătrat de câine, telefonul dispatch-ează o alertă înapoi pe ceas — icon fullscreen, culoare codificată pe severitate și un pattern haptic unic per clasă. Tracker-ul de doză de zgomot de pe telefon (OMS Safe Listening / NIOSH 85 dB+) și coachul AI bazat pe Gemini 2.5 Flash Lite completează bucla pentru sănătatea auzului.

---

## 2. De ce câștigăm

| Temă | Implementare concretă |
|------|----------------------|
| **Safety** | Ceasul streamează PCM 16 kHz mono → telefon clasifică <1s → alertă ajunge pe ceas cu haptic + culoare specifică clasei + ecran fullscreen + escaladare automată prin email cu coordonate GPS. |
| **Health** | Repository de detectări persistat în Room (SQLite). HealthScreen cu bar chart pe 7 zile + doză 24h. Briefing zilnic AI: Gemini 2.5 Flash Lite rezumă ce ai auzit azi și ce ar trebui să faci. |
| **Accessibility** | Visual + haptic alerts funcționează chiar dacă audio e mascat sau auzul e afectat. Haptice per clasă, intensitate 255 (maxim). Culori Material You + high-contrast. Font scale respectat. TalkBack labels. |

**Gap-ul pe piață.** Apple Sound Recognition e iOS-only. Google Live Transcribe e aplicație de transcriere, nu sistem de siguranță. Samsung detectoarele de sunet nu ajung pe Wear OS. **Nimic pe Android nu combină** captura de microfon pe încheietură + clasificare on-device + alerte haptice pe ceas + coach AI + hartă crowd-sourced de alerte. SoundGuard este primul.

---

## 3. Protocoalele Watch ↔ Phone (canonice)

```
                        Wearable Data Layer (BT)
     ┌────────────┐   /pulse/audio/stream/chunk    ┌────────────┐
     │   WATCH    │ ─── PCM 16-bit LE, 16 kHz ───► │   PHONE    │
     │  (mic)     │       500 ms / ~16 KB           │  (YAMNet)  │
     │            │ ◄─── alert payload ───────────  │            │
     └────────────┘   /pulse/event/sound_alert      └────────────┘
```

### Watch → Phone: `/pulse/audio/stream/chunk`
- **Format**: PCM 16-bit signed, little-endian, 16 kHz mono
- **Dimensiune chunk**: 500 ms = 8 000 de sample-uri = 16 000 de bytes
- **Rată**: ~2 chunks/secundă, continuu atât timp cât serviciul rulează
- **Transport**: `MessageClient.sendMessage()` din Wearable Data Layer

### Phone → Watch: `/pulse/event/sound_alert`
- **Format**: string UTF-8 `TYPE|DIRECTION|CONFIDENCE|TIMESTAMP_MS`
- **Exemplu**: `FIRE_ALARM|CENTER|0.872|1714050000000`
- **Debounce**: 5 secunde per TYPE pe telefon. Ceasul nu face deduplicare.

### Valori canonice TYPE
`SIREN`, `FIRE_ALARM`, `SHOUT_HELP`, `CAR_HORN`, `BABY_CRY`, `GLASS_BREAK`, `DOG_BARK`, `RO_ALERT`

### Severitate implicită per TYPE (derivată pe ceas din TYPE singur)
- **CRITICAL**: `FIRE_ALARM`, `SHOUT_HELP`, `RO_ALERT`
- **HIGH**: `SIREN`, `CAR_HORN`, `GLASS_BREAK`
- **MEDIUM**: `BABY_CRY`, `DOG_BARK`

`DIRECTION` este `CENTER` (mono, fără DOA). `LEFT`/`RIGHT` rezervat pentru stretch goal cu microfonul stereo al telefonului.

---

## 4. Stack tehnologic complet

### Modul `:app` (telefon)
| Strat | Tehnologie |
|-------|-----------|
| Limbaj | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 + Material You (dynamic color) |
| DI | Hilt (Dagger 2) |
| Persistență | Room (SQLite) — tabel `alert_events` |
| Preferințe | DataStore Preferences (sesiune auth, setări user) |
| Navigare | Navigation Compose — 5 tab-uri (Home, History, Health, Coach, Settings) |
| ML on-device | MediaPipe Tasks Audio — `AudioClassifier` cu model `yamnet.tflite` în modul `AUDIO_STREAM` |
| Watch ↔ Phone | `play-services-wearable` — `WearableListenerService` (inbound PCM), `MessageClient` (outbound alerts) |
| Backend crowd-sourced | `HttpURLConnection` → Railway (MongoDB) la `https://googlealerts.up.railway.app` |
| AI Coach | Google Gemini 2.5 Flash Lite via `generativeai` SDK — streaming răspunsuri token-by-token |
| Auth | Google Sign-In via Credential Manager API + modul guest |
| Email SOS | Gmail OAuth 2.0 + `GmailSender` (MIME, Dispatchers.IO) |
| RO-ALERT | `NotificationListenerService` — interceptează notificările cell broadcast |
| minSdk / targetSdk | 28 / 35 |
| Build | AGP 8.7.3, JDK 17 |

### Modul `:wear` (ceas)
| Strat | Tehnologie |
|-------|-----------|
| Limbaj | Kotlin |
| UI | Wear Compose + Material 3 for Wear |
| Captură audio | `AudioRecord` (MediaRecorder.AudioSource.MIC, 16 kHz, PCM_16BIT, CHANNEL_IN_MONO) |
| Transmisie | `MessageClient.sendMessage()` din Wearable Data Layer |
| Haptice | `VibrationEffect.createWaveform()` — pattern diferit per fiecare clasă de sunet |
| SOS email | JavaMail Android (com.sun.mail:android-mail 1.6.7) — SMTP over STARTTLS |
| GPS SOS | `LocationManager` — `getLastKnownLocation()` cu fallback pe mai mulți provideri |
| Alertare server | `HttpURLConnection` — POST `https://googlealerts.up.railway.app/api/alerts` |
| Biometric | `BiometricMonitor` — stub `PassiveMonitoringClient` (demo: one-shot flag armat cu long-press) |
| Foreground service | `SoundGuardService` cu `PARTIAL_WAKE_LOCK` + `FOREGROUND_SERVICE_TYPE_MICROPHONE` |
| applicationId | `com.soundguard.app` (identic cu `:app` pentru auto-pairing Wearable Data Layer) |

---

## 5. Arhitectura software `:app` (telefon)

```
com.soundguard.app/
├── SoundGuardApplication          @HiltAndroidApp
│   ├── bridge coroutine: SoundClassifier.detections → AlertDispatcher.dispatch()
│   ├── heartbeat: pruneStale() la 1s + isListening state
│   └── diagnostic: loghează noduri Wear conectate la 10s
│
├── audio/
│   └── AudioChunkReceiver         WearableListenerService
│       ├── onMessageReceived(path=/pulse/audio/stream/chunk)
│       │   ├── decodePcm16Le(ByteArray) → ShortArray → FloatArray (÷32768)
│       │   ├── SoundClassifier.feed(floats, samplesRead, sampleRate=16000, timestampMs)
│       │   └── DetectionRepository.markChunkReceived()
│       ├── onDataChanged()         (logging pentru debug transport)
│       ├── onChannelOpened()       (logging)
│       └── onCapabilityChanged()   (logging)
│
├── ml/
│   ├── SoundCategory              enum cu 8 clase canonice
│   │   ├── yamnetLabels: Set<String>  (mapare label YAMNet → categorie)
│   │   ├── displayName: String
│   │   ├── severity: Severity (CRITICAL/HIGH/MEDIUM)
│   │   ├── threshold: Float (0.40–0.50f per clasă)
│   │   └── fromYamnetLabel(label): SoundCategory?  (lookup O(1) via Map lazy)
│   ├── Detection                  data class (category, score, timestampMs)
│   └── SoundClassifier            @Singleton, @Inject
│       ├── AudioClassifier (MediaPipe AUDIO_STREAM mode, yamnet.tflite, maxResults=10)
│       ├── feed(FloatArray, samplesRead, sampleRate, timestampMs) → classifyAsync()
│       ├── onResult() → filtrare per threshold → _detections.tryEmit()
│       └── detections: SharedFlow<Detection>  (replay=0, buffer=32, DROP_OLDEST)
│
├── alert/
│   ├── AlertDispatcher            @Singleton
│   │   ├── dispatch(Detection)
│   │   │   ├── DetectionRepository.emit(detection)
│   │   │   ├── debounce 5s per SoundCategory (mutableMapOf)
│   │   │   ├── AlertEventDao.insert() → Room/SQLite
│   │   │   ├── sendToWear() → Wearable.getNodeClient → MessageClient.sendMessage
│   │   │   │   payload: "TYPE|CENTER|score|timestampMs".toByteArray(UTF-8)
│   │   │   └── reportToServer() → AlertApi.reportAlert() (fire-and-forget, networkScope)
│   ├── RoAlertListener            NotificationListenerService
│   │   ├── onNotificationPosted() → isRoAlert() → dispatcher.dispatch(Detection(RO_ALERT, 1.0f))
│   │   ├── isRoAlert(): pachet în CELL_BROADCAST_PACKAGES SAU keyword în title/text
│   │   └── dedupe 60s per sbn.key
│   ├── NotificationListenerHelper  verifică dacă permisiunea e acordată + deschide Settings
│   └── SosRelayReceiver            BroadcastReceiver pentru SOS manual de pe ceas
│
├── data/
│   ├── DetectionRepository        @Singleton
│   │   ├── last: StateFlow<Detection?>
│   │   ├── isListening: StateFlow<Boolean>  (true dacă chunk primit în ultimele 3s)
│   │   ├── emit(detection) + markChunkReceived()
│   │   └── pruneStale()  (resetează isListening dacă nu au venit chunks)
│   ├── HealthRepository           @Singleton
│   │   ├── summary: Flow<HealthSummary>  (observeRecent() → map)
│   │   └── HealthSummary: total24h, criticalCount24h, lastAlertMs, topCategory, sevenDayBuckets[7]
│   ├── PreferencesRepository      DataStore Preferences — setări per user
│   └── TrustedContactsRepository  DataStore — lista de contacte de urgență
│
├── data/db/
│   ├── SoundGuardDatabase         @Database(entities=[AlertEventEntity], version=1)
│   ├── AlertEventEntity           @Entity("alert_events") — id, category, score, timestampMs
│   └── AlertEventDao              @Dao — insert, observeRecent (Flow<List>), pruneOlderThan
│
├── network/
│   ├── AlertApi                   object, HttpURLConnection (fără lib extern)
│   │   ├── reportAlert(context, alertType, severity) → POST /api/alerts
│   │   │   body: {alertType, severity, location:{lat,lng}}
│   │   └── checkLocation(context) → POST /api/check-location → CheckResult
│   └── LocationProvider           lastKnownLocation() cu fallback pe GPS/NETWORK/FUSED
│
├── ai/
│   ├── GeminiClient               @Singleton, GenerativeModel("gemini-2.5-flash-lite")
│   │   ├── chatStream(history, CoachContext): Flow<String>  (streaming token-by-token)
│   │   ├── buildPrompt() — injectează profil user + stats 24h + istoricul alertelor + conversație
│   │   └── retry pe 429 cu backoff 30s (free tier: 1000 req/zi, 15 RPM)
│   └── CoachRepository            submitIntent(prompt, origin) → GeminiClient
│
├── auth/
│   ├── AuthRepository             @Singleton, DataStore Preferences
│   │   ├── signInWithGoogle(activity): User?  (Credential Manager API + Google ID Token)
│   │   ├── continueAsGuest(): User
│   │   └── userFlow: Flow<User?>
│   ├── GmailAuthorizer            OAuth 2.0 pentru email SOS de pe telefon
│   └── User                       data class (id, displayName, email, photoUrl, isGuest)
│
├── mail/
│   └── GmailSender                SMTP via Gmail OAuth — trimite SOS de pe telefon
│
├── voice/
│   └── AssistantSpeaker           TTS (TextToSpeech) — anunță vocal alertele prin BT headphones
│
├── navigation/
│   ├── SoundGuardNavGraph         NavigationBar cu 5 destinații
│   ├── SoundGuardDestinations     Home, History, Health, Coach, Settings
│   └── NavEventsViewModel         SharedFlow pentru navigare cross-tab (ex: Coach auto-switch)
│
└── ui/
    ├── home/
    │   ├── HomeScreen             pulsing orb isListening, last detection card, emergency banner,
    │   │                          location danger check, call 112, share safety message
    │   └── HomeViewModel          combine(isListening, last, listenerEnabled, tick, locationCheck)
    ├── history/
    │   ├── HistoryScreen          lista alertelor din Room + filtrare per severitate
    │   └── HistoryViewModel
    ├── health/
    │   ├── HealthScreen           DailyBarChart 7 zile, total24h, critical24h, AI insight, briefing
    │   ├── HealthViewModel        HealthRepository.summary → Gemini pentru insight
    │   └── components/BarChart    Canvas custom
    ├── coach/
    │   ├── CoachScreen            chat UI cu streaming Gemini token-by-token
    │   └── CoachViewModel
    ├── settings/
    │   ├── SettingsScreen         per-class toggles, sensitivity, SOS contact, sign-out
    │   └── SettingsViewModel
    ├── auth/
    │   ├── SignInScreen           Google Sign-In + Continue as Guest
    │   └── AuthViewModel
    └── theme/
        ├── Theme.kt               Material You dynamic color + high-contrast variant
        ├── Color.kt               brand palette: BrandIndigo, BrandViolet, BrandIndigoDeep
        ├── SeverityVisuals.kt     paletteFor(Severity), iconFor(SoundCategory), severityLabel()
        └── Type.kt                font scale respectat via MaterialTheme.typography
```

---

## 6. Arhitectura software `:wear` (ceas)

```
com.example.pulsewatch/
│
├── service/
│   ├── SoundGuardService          Service foreground
│   │   ├── onCreate → AudioChunkSender(context).resolveNode()
│   │   ├── onStartCommand
│   │   │   ├── startForeground(FOREGROUND_SERVICE_TYPE_MICROPHONE)
│   │   │   ├── acquireWakeLock(PARTIAL_WAKE_LOCK)
│   │   │   ├── BiometricMonitor.start(context)
│   │   │   ├── isRunning = true
│   │   │   └── startStreaming()  → AudioCapture.start().collect → AudioChunkSender.send()
│   │   ├── onDestroy → releaseWakeLock, BiometricMonitor.stop, isRunning=false
│   │   └── companion: isRunning @Volatile (citit de MainActivity.onResume)
│   │
│   ├── SoundAlertReceiver         WearableListenerService
│   │   ├── onMessageReceived(path=/pulse/event/sound_alert)
│   │   │   ├── parsează "TYPE|DIR|CONF|TS" → SoundAlertPayload
│   │   │   ├── HapticController.play(payload.type)
│   │   │   ├── AlertApiClient.report(payload) → POST /api/alerts (fire-and-forget)
│   │   │   └── startActivity(MainActivity, EXTRA_ALERT_BYTES=payload.toBytes())
│   │   └── (și trimite spre /pulse/trusted_contacts în TrustedContactsReceiver)
│   │
│   └── TrustedContactsReceiver    WearableListenerService
│       └── onMessageReceived(path=/pulse/trusted_contacts) → TrustedContactsStore.save()
│
├── safety/sound/
│   ├── AudioCapture               Flow<ShortArray>
│   │   ├── AudioRecord(MIC, 16000, MONO, PCM_16BIT, bufferSize)
│   │   ├── read() în buclă până se umple un chunk de 8000 de sample-uri (500ms)
│   │   └── emit(buffer.copyOf()) pe Dispatchers.IO
│   ├── AudioChunkSender           trimite ShortArray la telefon
│   │   ├── resolveNode() → CapabilityClient / NodeClient pentru peer-ul telefon
│   │   └── send(ShortArray) → ByteBuffer.order(LITTLE_ENDIAN) → MessageClient.sendMessage
│   ├── AudioFeatures              calcul RMS / dB FS pentru ambient noise meter
│   └── SoundDetector              on-watch lightweight keyword (stretch goal)
│
├── safety/health/
│   └── BiometricMonitor           object singleton
│       ├── _demoArmed: MutableStateFlow<Boolean>
│       ├── toggleDemoArmed(): Boolean  (long-press pe Hero card din Home)
│       ├── shouldFastEscalate(SoundType): Boolean
│       │   ├── dacă tipul nu e FIRE_ALARM/SHOUT_HELP → false
│       │   ├── dacă _demoArmed.value == true → setează false (one-shot), returnează true
│       │   └── altfel → false (TODO: PassiveMonitoringClient SpO2 real)
│       ├── start(context) / stop(context)  → (stub, wiring Health Services viitor)
│       └── isDemoArmed: StateFlow<Boolean>  (observat de Home pentru badge SpO2 SIM)
│
├── safety/
│   ├── HapticController           object singleton
│   │   ├── play(SoundType) → selectează effectul corect
│   │   ├── sirenEffect()          pulsuri 550ms ON, 200ms OFF × 4, amplitudine 255
│   │   ├── fireAlarmEffect()      6 burst-uri scurte staccato la amplitudine 255
│   │   ├── shoutHelpEffect()      SOS Morse (3 scurt + 3 lung + 3 scurt)
│   │   ├── carHornEffect()        două impulsuri scurte cu pauză
│   │   ├── babyCryEffect()        ritm lent ondulat
│   │   ├── glassBreakEffect()     cresc rapid, descrescere lungă
│   │   ├── dogBarkEffect()        impulsuri triple cu pauze
│   │   └── directional(dir)       taper min 70% (stânga/dreapta atenuat, nu mut)
│   ├── LocationHelper             lastKnownLocation() cu fallback GPS/NETWORK/FUSED
│   └── AlertApiClient             HttpURLConnection → POST /api/alerts (fără lib extern)
│
├── presentation/
│   ├── MainActivity
│   │   ├── setContent → SoundGuardTheme
│   │   ├── handleIntent(intent) → parsează EXTRA_ALERT_BYTES → SoundAlertPayload
│   │   │   → currentAlertFastEscalate = BiometricMonitor.shouldFastEscalate(payload.type)
│   │   ├── rutare: dacă currentAlert != null → SoundAlertScreen, altfel → WearHomeScreen
│   │   ├── onResume() → SoundGuardService.isRunning (sync toggle)
│   │   └── permisiuni la launch: POST_NOTIFICATIONS, RECORD_AUDIO, ACCESS_FINE_LOCATION
│   │
│   ├── HomeScreen.kt              ecranul principal al ceasului
│   │   ├── BrandHeader            logo + titlu
│   │   ├── HeroStatusCard         status Listening/Idle + ListeningOrb (animație pulsatilă)
│   │   │   └── combinedClickable: onClick={}, onLongClick → BiometricMonitor.toggleDemoArmed()
│   │   ├── TodayStatsCard         număr alerte azi + ambient dB (placeholder)
│   │   ├── SosQuickRow            buton SOS manual instant
│   │   └── PrimaryToggleButton    Start/Stop SoundGuardService
│   │
│   ├── SoundAlertScreen.kt        overlay fullscreen la primirea unei alerte
│   │   ├── Layout: Column(fillMaxSize, SpaceBetween, padding horizontal=18dp, vertical=14dp)
│   │   │   ├── TOP: emoji (28sp) + label (maxLines=1)
│   │   │   ├── MIDDLE: DirectionIndicator (42dp) + confidence chip + countdown chip
│   │   │   │   └── countdown chip: "📡 SOS in Ns" (alb→chihlimbar când ≤3s)
│   │   │   │                    sau "🩸 LOW SpO2 · SOS in Ns" (roz, fast escalate)
│   │   │   └── BOTTOM: Row → buton ✕ Cancel + buton ✉️ SOS
│   │   ├── LaunchedEffect(payload.timestamp) → countdown 1s/tick
│   │   │   └── la 0: resolved || onTimeoutEscalate()
│   │   ├── totalSec = if(fastEscalate) 2s else 10s
│   │   └── resolved flag → previne double-fire (countdown + tap simultan)
│   │
│   ├── TrustedContactsScreen.kt   lista contacte de urgență de pe ceas
│   │
│   ├── SosAction.kt               entry point SOS
│   │   ├── triggerSos(context, alertLabel?, auto, biometricSpike)
│   │   ├── lastKnownLocation() via LocationHelper
│   │   ├── buildSosMessage() — subject ramificat pe biometricSpike/auto/manual
│   │   └── EmailSender.send(to, subject, body) — suspend, Dispatchers.IO
│   │
│   ├── EmailSender.kt             Gmail SMTP over STARTTLS (JavaMail Android)
│   │   ├── SMTP_USER, SMTP_APP_PASSWORD (de înlocuit înainte de demo)
│   │   ├── 5s connect/read/write timeout
│   │   └── Display name: "SoundGuard Watch"
│   │
│   └── theme/Theme.kt             Wear Compose theme cu culori severitate
│
└── data/
    ├── WearProtocol.kt            constante de protocol + enum SoundType + SoundSeverity
    │   ├── SoundType: SIREN, FIRE_ALARM, SHOUT_HELP, CAR_HORN, BABY_CRY,
    │   │             GLASS_BREAK, DOG_BARK, RO_ALERT (CRITICAL, emoji 📢)
    │   ├── SOS_EMAIL_TO, SOS_ESCALATION_SECONDS=10, SOS_FAST_ESCALATION_SECONDS=2
    │   └── SoundAlertPayload.fromBytes(bytes) / toBytes()
    └── TrustedContactsStore.kt    DataStore — lista contacte sincronizată de pe telefon
```

---

## 7. Fluxul complet de date (end-to-end)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  WATCH (:wear)                                                                      │
│                                                                                     │
│  [Microfon] ──AudioRecord──► AudioCapture.start() → Flow<ShortArray>               │
│                                       │                                             │
│                                       ▼ (500ms = 8000 samples)                     │
│                               AudioChunkSender.send()                               │
│                               ByteBuffer LE → MessageClient.sendMessage             │
│                               path: /pulse/audio/stream/chunk                       │
└────────────────────────────────────────┬────────────────────────────────────────────┘
                                         │ Wearable Data Layer (BT/WiFi)
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  PHONE (:app)                                                                       │
│                                                                                     │
│  AudioChunkReceiver.onMessageReceived()                                             │
│    decodePcm16Le(bytes) → ShortArray                                                │
│    normalize → FloatArray (÷ 32768)                                                 │
│    SoundClassifier.feed(floats, sampleRate=16000, timestampMs)                      │
│         │                                                                           │
│         ▼ (async, classifyAsync)                                                    │
│    MediaPipe AudioClassifier (yamnet.tflite, AUDIO_STREAM mode)                     │
│    onResult() → iterează clasificările                                               │
│         ├── pentru fiecare label YAMNet cu score ≥ threshold:                       │
│         │   SoundCategory.fromYamnetLabel(label) → Detection(category, score, ts)   │
│         └── _detections.tryEmit(detection)  (SharedFlow, DROP_OLDEST)              │
│                                                                                     │
│  SoundGuardApplication (bridge coroutine)                                           │
│    classifier.detections.collect { detection →                                      │
│        AlertDispatcher.dispatch(detection)                                          │
│    }                                                                                │
│         │                                                                           │
│         ▼                                                                           │
│  AlertDispatcher.dispatch(detection)                                                │
│    ├── DetectionRepository.emit(detection) → StateFlow<Detection?> (UI)             │
│    ├── debounce 5s per SoundCategory                                                │
│    ├── AlertEventDao.insert() → Room/SQLite (persistent history)                    │
│    ├── sendToWear(): "TYPE|CENTER|score|ts".toByteArray(UTF-8)                      │
│    │   → MessageClient.sendMessage(nodeId, /pulse/event/sound_alert, payload)       │
│    └── reportToServer(): AlertApi.reportAlert() → POST /api/alerts (Railway/Mongo)  │
│                                                                                     │
│  [PARALEL] RoAlertListener.onNotificationPosted()                                   │
│    → isRoAlert() → dispatcher.dispatch(Detection(RO_ALERT, 1.0f))                  │
│    (aceeași cale ca mai sus)                                                        │
└──────────────────────────────────────────┬──────────────────────────────────────────┘
                                           │ Wearable Data Layer
                                           ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  WATCH (:wear) — primirea alertei                                                   │
│                                                                                     │
│  SoundAlertReceiver.onMessageReceived(path=/pulse/event/sound_alert)                │
│    parsează "TYPE|DIR|CONF|TS" → SoundAlertPayload                                  │
│    HapticController.play(payload.type) — pattern haptic imediat                     │
│    AlertApiClient.report(payload) — fire-and-forget (coordonate GPS + alertType)    │
│    startActivity(MainActivity, EXTRA_ALERT_BYTES)                                   │
│         │                                                                           │
│         ▼                                                                           │
│  MainActivity.handleIntent()                                                        │
│    payload = SoundAlertPayload.fromBytes(bytes)                                     │
│    fastEscalate = BiometricMonitor.shouldFastEscalate(payload.type)                 │
│    currentAlert = payload → UI → SoundAlertScreen                                  │
│         │                                                                           │
│         ▼                                                                           │
│  SoundAlertScreen                                                                   │
│    countdown = if(fastEscalate) 2s else 10s                                        │
│    opțiuni user:                                                                    │
│      ✕ Cancel → dismiss                                                             │
│      ✉️ SOS → triggerSos(manual)                                                   │
│      (nimic) → la timeout → triggerSos(auto=true, biometricSpike=fast)             │
│         │                                                                           │
│         ▼                                                                           │
│  SosAction.triggerSos()                                                             │
│    LocationHelper.lastKnownLocation() (GPS/NETWORK/FUSED fallback)                  │
│    buildSosMessage() — subiect + corp cu timestamp, coords, Maps link               │
│    EmailSender.send(to=contact, subject, body) — Gmail SMTP STARTTLS               │
└─────────────────────────────────────────────────────────────────────────────────────┘

[PARALEL, telefon]
AlertApi → POST https://googlealerts.up.railway.app/api/alerts
    body: {alertType, severity, location:{lat,lng}}
    server incrementează reportCount pentru aceeași locație/tip
    → hartă crowd-sourced de alerte în timp real
```

---

## 8. ML Pipeline — YAMNet pe MediaPipe

**Model**: `yamnet.tflite` (Google YAMNet — 521 de clase audio, antrenat pe AudioSet)
**Framework**: MediaPipe Tasks Audio — `AudioClassifier` în modul `AUDIO_STREAM`

**De ce AUDIO_STREAM (nu AUDIO_CLIPS)?**
- AUDIO_STREAM acceptă date în timp real fără să aștepte un clip complet
- Rezultatele vin asincron prin `ResultListener` — zero blocare pe thread-ul de rețea
- Compatibil cu streaming continuu de la ceas (~2 chunks/secundă)

**Pipeline intern**:
1. `AudioChunkReceiver` decodifică PCM 16-bit LE → float32 în `[-1.0, 1.0]`
2. `AudioData` (MediaPipe) construit cu `AudioDataFormat(channels=1, sampleRate=16000.0f)`
3. `classifier.classifyAsync(data, timestampMs)` — non-blocking
4. `onResult(AudioClassifierResult)` — iterează toate clasele, filtrează cu:
   - `BASE_SCORE_THRESHOLD = 0.05f` (threshold global MediaPipe — pentru logging)
   - `LOG_VISIBILITY_THRESHOLD = 0.20f` (ceea ce se loghează în Logcat)
   - Per-category threshold (0.40–0.50f) — ceea ce generează un `Detection`

**Mapare YAMNet → SoundCategory** (exemple):
| SoundCategory | YAMNet labels mapate |
|--------------|----------------------|
| SIREN | `"Siren"`, `"Emergency vehicle"`, `"Police car (siren)"`, `"Ambulance (siren)"`, `"Fire engine, fire truck (siren)"`, `"Civil defense siren"` |
| FIRE_ALARM | `"Smoke detector, smoke alarm"`, `"Fire alarm"` |
| SHOUT_HELP | `"Screaming"`, `"Shout"`, `"Yell"`, `"Children shouting"` |
| CAR_HORN | `"Vehicle horn, car horn, honking"`, `"Air horn, truck horn"`, `"Train horn"` |
| BABY_CRY | `"Baby cry, infant cry"`, `"Crying, sobbing"` |
| GLASS_BREAK | `"Glass"`, `"Shatter"` |
| DOG_BARK | `"Bark"`, `"Dog"` |
| RO_ALERT | — (nu vine din YAMNet, vine din NotificationListenerService) |

---

## 9. Backend crowd-sourced (Railway + MongoDB)

**URL**: `https://googlealerts.up.railway.app`
**Bază de date**: MongoDB Atlas prin Railway

### Schema Mongoose (server side)
```js
const alertSchema = new mongoose.Schema({
  reportCount: { type: Number, default: 1 },
  alertType:   { type: String, required: true },
  severity:    { type: String },
  location: {
    type:        { type: String, enum: ['Point'], default: 'Point' },
    coordinates: { type: [Number], required: true }  // [lng, lat] GeoJSON!
  },
  timestamp: { type: Date, default: Date.now }
});
alertSchema.index({ location: '2dsphere' });
```

### Endpoint-uri
| Method | Path | Payload | Răspuns |
|--------|------|---------|---------|
| POST | `/api/alerts` | `{alertType, severity, location:{lat,lng}}` | `{ok}` |
| POST | `/api/check-location` | `{location:{lat,lng}}` | `{isDangerous, nearbyAlertsCount}` |

### Cine apelează
- **`:app` `AlertDispatcher`** — la fiecare detecție ce trece debounce-ul de 5s
- **`:wear` `AlertApiClient`** — la fiecare alertă primită pe ceas (independent, fără GPS)
- **`HomeViewModel.checkLocationDanger()`** — la tap pe butonul "Check location" din Home

---

## 10. AI Coach — Gemini 2.5 Flash Lite

**Model**: `gemini-2.5-flash-lite` (cel mai mare free-tier zilnic: 1000 req/zi, 15 RPM)
**SDK**: `com.google.ai.client.generativeai`

**Contextul injectat în fiecare prompt**:
```
SYSTEM_PROMPT (rol + capabilități)
USER PROFILE: displayName, email, isGuest, hearingProfile.summary()
LAST 24H STATS: total24h, criticalCount24h, topCategory, lastAlertMs
RECENT ALERTS: ultimele 5 alerte cu displayName, severity, minutesAgo
CONVERSATION: istoricul complet al chat-ului (oldest first)
COACH: (model completează de aici)
```

**Caracteristici**:
- Răspunsuri streaming token-by-token via `generateContentStream()` → `Flow<String>` (delta)
- Retry automat pe eroare 429 cu backoff 30s (o singură reîncercare)
- Limbă adaptativă: răspunde în română sau engleză în funcție de ultima tură a userului
- `maxOutputTokens = 320`, `temperature = 0.6f`, `topP = 0.95f`
- `CoachRepository.submitIntent(prompt, origin)` + `NavEventsViewModel` → auto-switch pe tab Coach

**Tipuri de interacțiune**:
1. **Chat liber** (CoachScreen) — user pune orice întrebare despre sănătatea auzului
2. **Emergency coach** (HomeScreen) — la tap pe "Ask AI: what should I do?" de pe bannerul de urgență, un prompt pre-construit e trimis: *"I just got a {FIRE_ALARM} alert (just now). Walk me through what to do right now in 4 short steps."*
3. **Daily briefing** (HealthScreen) — rezumat auto al activității din ultima zi

---

## 11. RO-ALERT — Sistemul național de alertare

**Ce este**: RO-ALERT este sistemul românesc de alertare prin Cell Broadcast (CB). Când autoritățile emit o alertă națională (dezastre naturale, atacuri, urgențe civile), telefonul primește un CB message și generează o notificare specială.

**Cum interceptăm**:
- `RoAlertListener` extinde `NotificationListenerService`
- Verifică dacă notificarea vine dintr-un pachet CB cunoscut (`com.android.cellbroadcastreceiver`, `com.google.android.cellbroadcastreceiver` etc.)
- SAU dacă textul notificării conține keyword-uri: `RO-ALERT`, `ALERTĂ`, `EMERGENCY ALERT`, `PRESIDENTIAL ALERT`
- Dedupe: 60 de secunde per `sbn.key` (notificările CB se re-postează)

**Fluxul după detecție**:
- `dispatcher.dispatch(Detection(RO_ALERT, score=1.0f, timestampMs=now))`
- Aceeași cale ca orice altă alertă → Wear Data Layer → ceas → haptic CRITICAL (`fireAlarmEffect`) + ecran roșu + countdown 10s → email SOS dacă userul nu anulează

**De ce e important pentru pitch**: SoundGuard este una dintre puținele aplicații Android care integrează RO-ALERT la nivel de sistem, retransmițând alertele naționale pe ceas cu haptice CRITICAL — util în particular pentru persoanele care poartă căști și ar putea rata soneria/vibratia telefonului.

---

## 12. Haptice — Designul per clasă

Toate efectele folosesc `VibrationEffect.createWaveform(timings[], amplitudes[], -1)`. Amplitudinile sunt pinned la **255** (maxim) pe tot parcursul efectului. Duratele sunt suficient de lungi pentru a fi simțite chlar prin ecran de ceas.

| SoundType | Pattern haptic | Mnemonic senzorial |
|-----------|--------------|-------------------|
| SIREN | 4 × (550ms ON + 200ms OFF) | Puls lung regulat — urlet de sirenă |
| FIRE_ALARM | 6 × (80ms ON + 80ms OFF) | Staccato rapid — bipuri alarmă |
| SHOUT_HELP | SOS Morse: 3×100ms + 3×300ms + 3×100ms | Morse S-O-S — urgență maximă |
| CAR_HORN | 2 × (300ms ON + 150ms OFF) | Două impulsuri — claxon dublu |
| BABY_CRY | 3 × (200ms ON + 400ms OFF) | Ritm lent ondulat — plâns |
| GLASS_BREAK | 50ms crescendo + 600ms descrescere | Impact brusc + rezonanță |
| DOG_BARK | 3 × (150ms ON + 100ms OFF) + 400ms pauză | Triple impulsuri — lătrat |
| RO_ALERT | identic cu FIRE_ALARM | Recunoscut imediat ca CRITICAL |

`directional(dir)`: dacă `dir == LEFT` → dreapta tapering la 70%; dacă `RIGHT` → stânga tapering la 70%. Niciodată sub 70% — e o înmuiere, nu un mute.

---

## 13. Ecranele aplicației de telefon (`:app`)

### Home Tab
- **Status card** cu orb pulsatil (animație `infiniteRepeatable` + `scale`) când `isListening=true`
- **Emergency banner** (AnimatedVisibility expand/shrink) când o detecție CRITICAL a apărut în ultimele 5 minute — afișează: categoria, severitatea, buton "Call 112", buton "Share safety message", buton "Ask AI: what should I do?" (→ Coach)
- **Last detection card** cu icon culoare severitate + displayName + confidence
- **Location danger check**: buton → `AlertApi.checkLocation()` → afișează numărul de alerte din apropiere
- **Test buttons** (dev/demo mode): simulează fiecare categorie de alertă fără a mai aștepta ceasul
- **RO-ALERT toggle** + link Settings dacă NotificationListenerService nu e acordat

### History Tab  
- Lista completă a alertelor din Room (AlertEventDao), filtrabilă
- Fiecare item: emoji, displayName, severity chip (culoare), confidence %, timestamp relativ

### Health Tab
- **Bar chart 7 zile** (Canvas custom `DailyBarChart`) — nr. alerte per zi
- **Stats cards**: Total 24h, Critical 24h, Top category
- **AI Insight**: un call Gemini care rezumă ziua și dă sfaturi (buton refresh)
- **Daily Briefing**: narațiune completă a zilei cu contextualizare

### Coach Tab
- Chat Compose — buloane user (dreapta) + model (stânga) cu streaming token-by-token
- Input field cu send button
- Auto-scroll la ultimul mesaj
- Context injectat: profil + stats + ultime 5 alerte

### Settings Tab
- Per-class toggles și sensitivity sliders
- SOS contact configuration
- Google Sign-In / Sign-Out
- NotificationListener permission shortcut

---

## 14. Ecranele aplicației de ceas (`:wear`)

### Home Screen (ceas)
```
┌─────────────────────────────┐
│      🎧 SoundGuard          │ ← BrandHeader
│                             │
│  ┌─────────────────────┐    │
│  │  ● (orb pulsatil)   │    │ ← HeroStatusCard
│  │  LISTENING          │    │   long-press → SpO2 SIM
│  │  🩸 SpO2 SIM armed  │    │
│  └─────────────────────┘    │
│                             │
│  📊 Alerte azi: 3 · 🔊 —   │ ← TodayStatsCard
│                             │
│  [✉️ SOS Quick]             │ ← SosQuickRow
│                             │
│  [■ Stop SoundGuard]        │ ← PrimaryToggleButton
└─────────────────────────────┘
```

### Alert Screen (ceas)
```
┌─────────────────────────────┐
│  🔥                         │ ← emoji 28sp
│  FIRE ALARM                 │ ← label maxLines=1
│                             │
│      ↑                      │ ← DirectionIndicator 42dp
│   CENTER                    │
│   87% confidence            │
│  🩸 LOW SpO2 · SOS in 2s   │ ← countdown chip (roz=fast, alb=normal)
│                             │
│  [✕]        [✉️ SOS]        │ ← Cancel + SOS buttons
└─────────────────────────────┘
   Fundal: roșu CRITICAL / portocaliu HIGH / galben MEDIUM
```

---

## 15. Configurare pre-demo (OBLIGATORIE)

### EmailSender.kt (`:wear`)
```kotlin
// wear/src/main/java/com/example/pulsewatch/presentation/EmailSender.kt:25-26
private const val SMTP_USER = "REPLACE_ME@gmail.com"
private const val SMTP_APP_PASSWORD = "REPLACE_ME_APP_PASSWORD"
```
1. Activează 2-Step Verification pe contul Gmail
2. Generează App Password la https://myaccount.google.com/apppasswords
3. Înlocuiește constantele de mai sus

### local.properties (`:app`)
```properties
GEMINI_API_KEY=<cheia ta de la https://aistudio.google.com/apikey>
GOOGLE_WEB_CLIENT_ID=<Web Client ID din Google Cloud Console>
```

⚠️ Credențialele din `EmailSender.kt` ajung în APK. OK pentru demo — rotește App Password după hackathon.

---

## 16. Fluxul demo (8 minute)

1. **Telefon + ceas paired**, ambele pornite. Home pe telefon: orb pulsatil "Listening". Ceas: "Listening" pe Hero card.
2. **Long-press Hero card** pe ceas → haptic pulse + `"🩸 SpO2 SIM armed · long-press"` apare sub status.
3. **Redă un clip de alarmă de incendiu** de pe laptop în apropierea ceasului.
4. **În ~1s**: telefonul procesează, trimite alerta → ceasul flash roșu + haptic `fireAlarmEffect` + ecran FIRE ALARM cu `"🩸 LOW SpO2 · SOS in 2s"`.
5. **Nu atinge nimic** → countdown ajunge la 0 → email trimis automat cu subiect `🚨 SoundGuard CRITICAL: Fire Alarm detected + low SpO2` + coordonate GPS + Maps link.
6. **Pe telefon**: Home card actualizat "Last detected: Fire alarm · 87%". Bannerul de urgență apare → arată "Call 112" + "Ask AI". Tap pe Ask AI → Coach tab auto-switch, streaming răspuns Gemini cu pașii de urmat.
7. **Redă un clip de sirenă** → HIGH severity, countdown normal 10s, ribbon alb `"📡 SOS in 10s"`. Demonstrează escaladarea diferențiată.
8. **Health tab**: arată bar chart cu activitatea zilei. AI Insight generat de Gemini.
9. **(Stretch) RO-ALERT test**: toggle RO-ALERT din Home → simulează o notificare CB → ceasul afișează alerta CRITICAL cu emoji 📢 și label "RO-ALERT".

---

## 17. Known TODOs / limitări

| Item | Status | Detalii |
|------|--------|---------|
| Real SpO2 | TODO | `BiometricMonitor.start()` e stub. Wiring real: `PassiveMonitoringClient` + buffer 5min + threshold drop 4pp sau <92% absolut |
| Ambient dB pe ceas | Placeholder | `TodayStatsCard` afișează `—`. Wiring real: tap `AudioCapture` flow → RMS → dB FS + offset 94 |
| SMTP credentials hardcoded | Demo-only | Rotesc după hackathon |
| Stale GPS fix | Known | `lastKnownLocation()` returnează fix cached. Body email include `fix age: Ns`. Pentru fix proaspăt: `requestSingleUpdate()` cu timeout |
| `SOS_CONTACT_NUMBER` | Legacy, unused | Rămas în `WearProtocol.kt` în caz că se adaugă SMS/call ca fallback |
| Health Services dep | Unused classpath | Pregătit pentru SpO2 real |
| DOA (direction of arrival) | Stretch | Stereo mic de pe telefon → LEFT/RIGHT în payload |
| TTS announcements | Stretch | `AssistantSpeaker` existent în `:app`, ne-integrat în flow-ul principal |

---

## 18. Build / Run

Userul buildează în **Android Studio pe Windows** (`local.properties → C:\Users\ancas\…\Android\Sdk`).
**WSL este doar pentru editare** — nu rula Gradle din WSL (calea SDK e greșită).

Device demo: **Pixel Watch 4** + telefon Android. Wi-Fi trebuie pornit (SMTP + server reporting).

```bash
# Grep-uri utile pentru navigarea codului:
grep -rn "triggerSos"          wear/src   # unde se declanșează SOS
grep -rn "shouldFastEscalate"  wear/src   # logica biometric fast-escalate
grep -rn "EXTRA_ALERT_BYTES"   wear/src   # fluxul payload alert
grep -rn "classifyAsync"       app/src    # unde YAMNet primește datele
grep -rn "dispatch"            app/src    # fluxul AlertDispatcher
grep -rn "chatStream"          app/src    # streaming Gemini
```
