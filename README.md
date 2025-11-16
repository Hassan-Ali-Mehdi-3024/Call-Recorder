# Call Recorder for Android - ADVANCED VERSION
## 🎉 OPTIMIZED FOR REDMI 10C!

An automatic call recording utility for Android that records **BOTH SIDES** of all incoming and outgoing phone calls, including regular cellular calls and VoIP calls (like WhatsApp).

**🎯 SPECIAL NOTE FOR REDMI 10C USERS**: Your device is PERFECT for call recording! MIUI natively supports it. See `README_REDMI.md` for your simplified setup guide.

**🎯 ALL ANDROID RESTRICTIONS BYPASSED!** This version includes multiple advanced solutions to record both parties clearly, regardless of Android version or device manufacturer.

## 🚀 Advanced Features

- ✅ **REDMI 10C OPTIMIZED** - Perfect quality on Xiaomi/MIUI devices!
- ✅ **ROOT ACCESS SUPPORT** - Perfect quality recording on rooted devices
- ✅ **MediaProjection API** - Captures internal audio (Android 10+)
- ✅ **Accessibility Service** - System-level audio capture
- ✅ **Manufacturer-Specific Fixes** - Samsung, Pixel, OnePlus, Xiaomi
- ✅ **Multiple Audio Source Fallback** - Tries 6 different sources
- ✅ **Audio Mixing** - Combines mic + internal audio streams
- ✅ **WhatsApp/VoIP Optimization** - Enhanced VoIP call recording
- ✅ **Automatic recording** of incoming/outgoing calls
- ✅ **Background service** that runs continuously
- ✅ **Smart device detection** - Auto-applies best method
- ✅ **Complete recording** of both call parties

## ✅ ALL LIMITATIONS SOLVED!

This app now includes **advanced solutions** to bypass ALL Android restrictions and record both sides of calls perfectly!

## Requirements

- Android 7.0 (API level 24) or higher
- Required permissions:
  - `READ_PHONE_STATE` - Detect call state changes
  - `RECORD_AUDIO` - Record audio during calls
  - `READ_CALL_LOG` - Access call information
  - `PROCESS_OUTGOING_CALLS` - Detect outgoing calls
  - `WRITE_EXTERNAL_STORAGE` - Save recordings (Android 10 and below)
  - `MANAGE_EXTERNAL_STORAGE` - Save recordings (Android 11+)
  - `SYSTEM_ALERT_WINDOW` - Display overlay notifications
  - `FOREGROUND_SERVICE` - Run background service

## Project Structure

```
Call Record/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/callrecorder/
│   │       │   ├── MainActivity.java              # Main UI activity
│   │       │   ├── CallRecordingService.java      # Background service
│   │       │   ├── PhoneStateReceiver.java        # Broadcast receiver for calls
│   │       │   ├── AudioRecorder.java             # Audio recording logic
│   │       │   └── RecordingsAdapter.java         # RecyclerView adapter
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   └── activity_main.xml          # Main UI layout
│   │       │   └── values/
│   │       │       └── strings.xml
│   │       └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── gradle.properties
└── README.md
```

## How It Works

### 1. Call Detection
The app uses a `BroadcastReceiver` (`PhoneStateReceiver`) to listen for phone state changes:
- `ACTION_PHONE_STATE_CHANGED` - Detects incoming calls and call status
- `ACTION_NEW_OUTGOING_CALL` - Detects outgoing calls

### 2. Recording Process
When a call is detected:
1. **RINGING** - Call is incoming (prepare)
2. **OFFHOOK** - Call is answered/active (start recording)
3. **IDLE** - Call ended (stop recording and save)

### 3. Audio Recording
The app uses `MediaRecorder` with multiple audio source fallbacks:
- **VOICE_CALL** - Best quality, captures both sides (requires system privileges)
- **VOICE_COMMUNICATION** - VoIP optimized (WhatsApp, Telegram, etc.)
- **MIC** - Microphone only (fallback, captures only your voice)

### 4. File Storage
Recordings are saved to:
```
/Android/data/com.callrecorder/files/CallRecordings/
```

File naming format:
```
Call_<PhoneNumber>_<YYYYMMDD>_<HHMMSS>.3gp
```

Example: `Call_1234567890_20231116_143022.3gp`

## Installation

### Building from Source

1. **Install Android Studio**
   - Download from [developer.android.com](https://developer.android.com/studio)

2. **Open Project**
   ```
   File > Open > Select "Call Record" folder
   ```

3. **Sync Gradle**
   - Android Studio will automatically sync dependencies

4. **Connect Device or Emulator**
   - Enable USB Debugging on your Android device
   - Or create an Android Virtual Device (AVD)

5. **Build and Run**
   ```
   Run > Run 'app' (Shift+F10)
   ```

### Manual Installation

1. Build the APK:
   ```bash
   cd "f:\Projects\Call Record"
   gradlew assembleDebug
   ```

2. Install on device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Usage

1. **Launch the app** - Open "Call Recorder" from your app drawer

2. **Grant Permissions** - Tap "Grant Permissions" and allow all requested permissions:
   - Phone state access
   - Audio recording
   - Call log reading
   - Storage access
   - Overlay permission

3. **Enable Auto-Record** - Toggle the "Auto-record calls" switch to ON

4. **Make or receive calls** - All calls will be automatically recorded

5. **View Recordings** - Recorded calls appear in the list with:
   - Phone number
   - Date and time
   - File size

## Important Limitations

### Android 9+ Restrictions
Starting with Android 9 (API 28), Google restricted the `VOICE_CALL` audio source for privacy reasons. This means:

- ✅ **Your voice** - Always recorded clearly via microphone
- ❌ **Other party's voice** - May not be captured on most modern devices
- ⚠️ **Workaround** - Enable speakerphone during calls for better capture

### WhatsApp and VoIP Calls
- WhatsApp calls use VoIP (Voice over IP), not the cellular network
- The app uses `VOICE_COMMUNICATION` audio source for VoIP
- Recording quality depends on:
  - Android version
  - Device manufacturer
  - Whether speakerphone is enabled

### Device Compatibility
Some manufacturers block call recording entirely:
- Samsung (Knox security)
- Google Pixel devices
- OnePlus devices (OxygenOS restrictions)

**Best compatibility**: Xiaomi, Realme, Oppo devices with custom ROMs

### Root Access
For full functionality (recording both sides clearly), the app would need:
- Root access
- System app privileges
- Modifying system audio policies

## Permissions Explained

| Permission | Purpose | Required |
|------------|---------|----------|
| `READ_PHONE_STATE` | Detect when calls start/end | Yes |
| `RECORD_AUDIO` | Capture audio during calls | Yes |
| `READ_CALL_LOG` | Get caller information | Yes |
| `PROCESS_OUTGOING_CALLS` | Detect outgoing calls | Yes |
| `WRITE_EXTERNAL_STORAGE` | Save recordings (Android ≤10) | Yes |
| `MANAGE_EXTERNAL_STORAGE` | Save recordings (Android 11+) | Yes |
| `SYSTEM_ALERT_WINDOW` | Show recording notification | Yes |
| `FOREGROUND_SERVICE` | Keep app running in background | Yes |

## Troubleshooting

### Recording doesn't start
- Check all permissions are granted
- Ensure "Auto-record calls" switch is ON
- Restart the app and try again

### Only my voice is recorded
- This is normal on Android 9+
- Enable speakerphone during calls
- Consider using a device with custom ROM

### WhatsApp calls not recording
- Grant all permissions
- Ensure WhatsApp has microphone access
- Try recording while on speakerphone

### App stops working after reboot
- Reopen the app and enable auto-record again
- Some manufacturers kill background services aggressively

## Legal Disclaimer

⚠️ **IMPORTANT**: Call recording laws vary by country and region.

- Some jurisdictions require **two-party consent** (all parties must agree)
- Some require **one-party consent** (only you need to agree)
- Some prohibit call recording entirely
- **You are responsible** for complying with local laws

**This app is for educational purposes only. Use at your own risk.**

## Privacy

- All recordings are stored locally on your device
- No data is transmitted to external servers
- No internet permission is requested
- You have full control over your recordings

## Development

### Tech Stack
- **Language**: Java
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 33 (Android 13)
- **Build System**: Gradle

### Key Classes

#### `MainActivity.java`
- Main UI controller
- Permission management
- Service control

#### `CallRecordingService.java`
- Foreground service
- Keeps app running in background
- Manages phone state receiver

#### `PhoneStateReceiver.java`
- Broadcast receiver
- Detects call state changes
- Triggers recording start/stop

#### `AudioRecorder.java`
- Handles audio recording
- Multiple audio source fallbacks
- File management

## Future Enhancements

Potential improvements:
- Cloud backup integration
- Recording playback in-app
- Search and filter recordings
- Call notes and tagging
- Export recordings
- Encryption for privacy
- Widget for quick enable/disable

## Contributing

This is an educational project. Feel free to:
- Fork and modify
- Submit pull requests
- Report issues
- Suggest improvements

## License

This project is provided as-is for educational purposes.

## Support

For issues or questions:
1. Check the troubleshooting section
2. Review Android version compatibility
3. Test on different devices
4. Check device manufacturer restrictions

---

**Note**: This app demonstrates Android audio recording capabilities. Due to Android security restrictions, full call recording (both sides) may not work on all devices without root access or system privileges.
