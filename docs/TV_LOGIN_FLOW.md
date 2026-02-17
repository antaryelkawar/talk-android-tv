# TV Login Flow

## Overview

Android TV devices typically lack a good browser, making the standard Nextcloud Login Flow v2 (which opens an external browser) impractical. This document explains how the TV login works using a QR code-based device flow, and an alternative direct WebView login — all without any server-side changes.

## How It Works

The TV login leverages the existing **Nextcloud Login Flow v2** protocol, which already behaves like an OAuth 2.0 Device Authorization Grant:

1. The TV initiates a login session with the server
2. The server returns a login URL and a poll token
3. The TV displays the login URL (as a QR code) for the user to open on another device
4. The TV polls the server in the background until login completes
5. Once authenticated, the TV receives app credentials and proceeds

**No server-side changes are needed.** The Nextcloud server does not know or care whether the login URL was opened from a browser on the TV, a phone that scanned a QR code, or any other device — it is the same HTTP request to the same URL.

## Login Options on TV

The TV login screen presents two options side by side:

### Option 1: QR Code (Recommended)

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│   Sign in with your phone    │  Or sign in directly     │
│                               │  on this device          │
│        ┌──────────┐          │                           │
│        │ QR CODE  │          │  [ Sign in with password ]│
│        │          │          │                           │
│        └──────────┘          │  [ Cancel Login ]         │
│                               │                           │
│   Scan this QR code with     │                           │
│   your phone and complete    │                           │
│   the login there            │                           │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**Flow:**

1. TV calls `POST {baseUrl}/index.php/login/v2`
2. Server responds with:
   ```json
   {
     "poll": {
       "token": "mQUY...Wrs1",
       "endpoint": "https://cloud.example.com/login/v2/poll"
     },
     "login": "https://cloud.example.com/login/v2/flow/guyj...3YFg"
   }
   ```
3. TV encodes the `login` URL into a QR code and displays it
4. TV starts polling `POST {poll.endpoint}` with `token={poll.token}` every 250ms
5. User picks up their phone, opens the camera, and scans the QR code
6. Phone browser opens the login URL — user authenticates with any method the server supports (username/password, Google OAuth, SAML, etc.)
7. Once the user completes login on their phone, the server marks the session as authenticated
8. The TV's next poll returns:
   ```json
   {
     "server": "https://cloud.example.com",
     "loginName": "username",
     "appPassword": "yKTVA4..."
   }
   ```
9. TV proceeds to account verification and enters the app

**Why this works:** The QR code is simply a transport mechanism to get a long URL (~160 characters) from the TV screen to the user's phone without typing. The server does not differentiate between a browser opening the URL directly and a phone scanning it from a QR code — both result in the same HTTP request.

**Supports:** All authentication methods — username/password, Google OAuth, SAML, LDAP, two-factor authentication — because the actual login happens in the phone's browser.

### Option 2: Direct WebView Login

For users who prefer to log in directly on the TV (e.g., with a USB keyboard or the on-screen keyboard):

1. User selects "Sign in with password"
2. The screen switches to an embedded WebView showing the same Nextcloud login page
3. User enters credentials using the TV remote or keyboard
4. Login completes within the WebView
5. The background poll detects the completion and proceeds

**Note:** Google OAuth/social login may not work in the WebView due to Google's policy of blocking OAuth in embedded WebViews. Users who need social login should use the QR code option.

## Technical Details

### Key Files

| File | Purpose |
|------|---------|
| `BrowserLoginActivity.kt` | Main login activity, handles both QR and WebView paths on TV |
| `BrowserLoginActivityViewModel.kt` | Manages login state, initiates Login Flow v2, handles polling |
| `LoginRepository.kt` | Orchestrates network calls and polling loop |
| `NetworkLoginDataSource.kt` | Makes HTTP calls to Login Flow v2 endpoints |
| `layout-television/activity_web_view_login.xml` | TV-specific layout with QR code + WebView |

### QR Code Generation

QR codes are generated client-side using the ZXing library (`com.google.zxing:core`). The login URL is encoded into a 512x512 bitmap displayed in an `ImageView`.

### Polling Mechanism

The polling runs in a coroutine on `Dispatchers.IO`:
- Interval: 250ms
- Method: `POST` to the poll endpoint with the token
- Non-200 responses (typically 404 = "not yet authenticated") trigger a retry
- 200 response = login complete, credentials returned
- Cancelled via `cancelLoginFlow()` which sets `shouldLoop = false`

### State Flow (TV Path)

```
onCreate
  → startWebBrowserLogin(baseUrl)
    → POST /index.php/login/v2
    → InitialLoginRequestSuccess(loginUrl)
      → showQrCode(loginUrl)        // Display QR
      → handleWebBrowserLogin()     // Start polling immediately
        → pollLogin(response)       // Loop until 200 or cancel
          → PostLoginContinue(bundle)
            → startAccountVerification(bundle)
```

On non-TV devices, the flow is different: the app opens an external browser and waits for the user to return (activity lifecycle triggers the poll). On TV, the QR code stays visible and polling starts immediately since the user never leaves the activity.

### Back Navigation

- **On QR screen:** Back cancels login and returns to server selection
- **On WebView screen:** Back navigates WebView history, then returns to QR screen
- **"Back to QR code" button:** Returns from WebView to QR screen

## Comparison with Other TV Login Approaches

| Approach | Server Changes | Browser Needed | Social Login | Implemented |
|----------|---------------|----------------|--------------|-------------|
| **QR Code (current)** | None | No (on TV) | Yes (via phone) | Yes |
| Direct WebView | None | No | Limited | Yes |
| External browser | None | Yes | Yes | Fallback only |
| Pairing code (YouTube-style) | Yes (new endpoints) | No | Yes | Not yet |

## Future Enhancements

- **Pairing code login:** A server-side implementation could provide a short numeric code (like YouTube TV) instead of a QR code. See `TV_PAIRING_CODE_IMPLEMENTATION.md` for the proposed design.
- **Increased poll interval:** The current 250ms polling could be increased to 1-2 seconds for the TV path since the user may take 30-60 seconds to complete login on their phone.
