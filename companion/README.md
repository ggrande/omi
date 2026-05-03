# Omi Ambient Companion

Personal Android companion app for local-first ambient capture. The companion app owns Android permissions, foreground microphone capture, VAD, encrypted spool, accessibility/caption fallback, notification triggers, and direct sync to the official Omi API after user sign-in.

This is not an Omi plugin and does not modify the official Omi app. The Ambient Second Brain Controller plugin is optional for configuration, fallback segment storage, distribution, and accountability workflows.

## What It Does

- Shows a visible armed-status notification while idle without opening the microphone.
- Starts a visible microphone foreground service only after explicit microphone watch consent.
- Uses `AudioRecord` PCM16 mono 16 kHz.
- Runs lightweight RMS/VAD first with a RAM pre-roll buffer.
- Writes speech-triggered audio to encrypted app-private spool files.
- Uses AccessibilityService for foreground app and allowlisted caption/transcript fallback.
- Uses NotificationListenerService for meeting/call/Sound Notifications/Live Transcribe context triggers.
- Detects communication mode, mic silencing, low signal, network buffering, private mode, and storage limits.
- Signs in with Omi and uploads decrypted length-prefixed PCM spools directly to Omi's existing `/v2/sync-local-files` audio pipeline.
- Uploads degraded fallback-only transcript segments directly to Omi's developer conversation-from-segments endpoint when no raw audio is available and no plugin controller is configured.
- Optionally registers with `plugins/ambient-second-brain-controller` and pins its policy key.
- Optionally uploads telemetry, fallback segments, and backup audio spools to the controller backend.
- Tracks capture sessions, storage status, and local delete pending/synced/all-audio controls.
- Runs best-effort Android on-device speech recognition over finalized spools on Android 13+ when supported by the device.
- Supports explicit, user-approved MediaProjection audio capture for apps/audio usages Android allows.
- Uses context triggers such as meeting/call notifications, Live Transcribe/Sound Notifications, wired headset, Bluetooth audio, and SCO route changes. By default these keep the app armed and idle; automatic mic start from those triggers requires explicit continuous mic watch consent.
- Shows a structured diagnostics snapshot in the app UI for field testing.

## Build

```powershell
Copy-Item app\android\local.properties companion\android\local.properties
app\android\gradlew.bat -p companion\android :app:assembleDebug --no-build-cache
```

APK:

```text
companion/android/app/build/outputs/apk/debug/omi-ambient-companion-debug-v0.1.0.apk
```

The standalone companion APK must identify as:

```text
package: com.omi.ambientcompanion
label: Omi Ambient Companion
```

It installs next to the official/published Omi app and does not replace or modify it.

## Personal Setup

1. Install the APK on the Pixel.
2. Open `Omi Ambient Companion`.
3. Tap `Sign in with Omi` and complete Google/Apple auth.
4. Tap `Permissions & setup`, grant microphone and notifications.
5. Enable Omi Ambient Companion in Accessibility settings.
6. Enable Omi Ambient Companion in Notification Listener settings.
7. Allow unrestricted/background battery operation.
8. Accept microphone watch consent if you want to start mic capture.
9. Tap `Start`.
10. Speak for 30-60 seconds, tap `Stop`, then tap `Sync`.

Optional plugin setup:

1. Tap `Advanced settings`.
2. Enter the Ambient Second Brain Controller base URL.
3. Confirm the Omi user id.
4. Tap `Register plugin`.

The plugin URL, plugin device token, and pinned key are not required for direct Omi audio sync.

For the full field-test checklist, see `companion/TESTING.md`.

The app does not auto-record after reboot. Boot handling only resets stale recovery state.

## Safety

- Persistent notification is always visible while the mic service is running.
- The idle armed notification is not a microphone foreground service and does not show Android's microphone privacy indicator.
- Android's microphone privacy indicator is shown whenever `AudioRecord` capture is running. The app does not hide or suppress it.
- Private Mode stops active capture/upload locally.
- The app does not use `VoiceInteractionService`, SoundTrigger HAL, hidden recording, arbitrary screen scraping, or silent media sessions.
- Call/meeting capture is degraded when Android blocks audio. Captions/transcripts are labeled as fallback sources.

## Known Limits

- Local STT uses Android's on-device recognizer when available. It is not a bundled Whisper/Vosk model and may be unavailable or limited by the system recognizer.
- MediaProjection captures only audio Android and the source app permit. It does not bypass protected meeting/call audio.
- Direct audio upload targets Omi `/v2/sync-local-files` using the user's Omi auth token. The uploaded filename intentionally matches the official Omi WAL shape: `audio_phone_pcm16_16000_1_fs160_<timestamp>.bin`.
- Fallback caption/local-STT text is preserved locally. When uploaded directly to Omi it is explicitly prefixed with fallback source/health labels. When the optional plugin is configured, the plugin receives structured source labels and degraded metadata.
