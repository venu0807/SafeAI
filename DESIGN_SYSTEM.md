# SafeGuard AI Design System

This document serves as the **Single Source of Truth** for the SafeGuard AI design language. It ensures visual consistency and structural harmony across the native Android application (Jetpack Compose) and the Companion Web Dashboard (Next.js).

---

## 1. Core Principles
- **Dark Mode First:** The aesthetic relies on deep space colors (`#0F111A`) to reduce battery consumption on mobile and minimize eye strain.
- **Glassmorphism:** Surfaces should use semi-transparent backgrounds with background blurs to create depth without harsh borders.
- **Urgency via Color:** Avoid using red/orange/yellow unless conveying an active threat, error, or warning.

---

## 2. Color Palette & Token Mapping

| Token Name | Hex Code | Android Compose (`Color.kt`) | Web CSS Variable (`globals.css`) | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Blue** | `#0A56D0` | `PrimaryBlue` | `--primary-blue` | Brand color, active states, primary buttons |
| **Primary Hover** | `#1B68E3` | `PrimaryHover` | `--primary-hover` | Button hover states, interactive feedback |
| **Background Dark** | `#0F111A` | `BackgroundDark` | `--bg-dark` | App backgrounds, lowest elevation |
| **Surface Dark (Glass)** | `#191C29` (60%) | `SurfaceDark` | `--surface-dark` | Cards, panels, sidebars (requires blur) |
| **Error / Critical** | `#D32F2F` | `ErrorRed` | `--error-red` | Active threats, destructive actions |
| **Success / Safe** | `#388E3C` | `SuccessGreen` | `--success-green` | System healthy, threats resolved |
| **Warning** | `#F57C00` | `WarningOrange` | `--warning-orange` | Unverified threats, low battery warnings |
| **Text Main** | `#FFFFFF` | `TextMain` | `--text-main` | Primary headings, body text |
| **Text Muted** | `#9BA1B0` | `TextMuted` | `--text-muted` | Subtitles, disabled text, table headers |

---

## 3. Glassmorphism Specifications

Glassmorphism is our primary method for elevating content off the deep background. It must be implemented consistently across platforms.

### CSS Implementation (Web)
```css
.glass-panel {
  background: rgba(25, 28, 41, 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
  border-radius: 16px;
}
```

### Jetpack Compose Equivalent (Android)
*Note: True backdrop-blur on Android requires API 31+. For older devices, fallback to a solid surface color.*
```kotlin
Modifier
    .background(Color(0x99191C29), shape = RoundedCornerShape(16.dp))
    .border(1.dp, Color(0x14FFFFFF), shape = RoundedCornerShape(16.dp))
    .shadow(8.dp, spotColor = Color(0x5E000000))
```

---

## 4. Typography Scale

| Hierarchy | Web (Inter) | Android (Default Sans) | Weight | Line Height | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Heading 1** | `2.5rem` (40px) | `32.sp` | SemiBold (600) | `1.2` / `40.sp` | Page titles, large metrics |
| **Heading 2** | `1.5rem` (24px) | `22.sp` | SemiBold (600) | `1.2` / `28.sp` | Section headers, card titles |
| **Body Large** | `1rem` (16px) | `16.sp` | Normal (400) | `1.5` / `24.sp` | Standard paragraph text, inputs |
| **Label Medium** | `0.875rem` (14px)| `12.sp` | Medium (500) | `1.5` / `16.sp` | Metadata, timestamps, table headers |

---

## 5. Micro-interactions & Animation

Animations must feel deliberate and premium. Avoid sudden jumps; prioritize smooth easing.

### The Threat Pulse Indicator
The pulse indicator represents the AI engine actively monitoring the environment. It must perfectly match on both platforms.

- **Animation Type:** Infinite Scaling & Fading (Reverse / Restart)
- **Duration:** `1500ms` (1.5 seconds)
- **Easing Curve:** Linear Out, Slow In (`cubic-bezier(0.4, 0, 0.2, 1)`)
- **Scale Range:** `1.0` to `1.5` (Android) / `10px` spread (CSS)
- **Opacity Range:** `1.0` to `0.0`

**CSS (Web):**
```css
@keyframes pulseGlow {
  0% { box-shadow: 0 0 0 0 rgba(10, 86, 208, 0.7); }
  70% { box-shadow: 0 0 0 10px rgba(10, 86, 208, 0); }
  100% { box-shadow: 0 0 0 0 rgba(10, 86, 208, 0); }
}
```

**Compose (Android):**
```kotlin
val infiniteTransition = rememberInfiniteTransition()
val scale by infiniteTransition.animateFloat(
    initialValue = 1f, targetValue = 1.5f,
    animationSpec = infiniteRepeatable(
        animation = tween(1500, easing = LinearOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )
)
```

---

## 6. Layout & Spacing
- **Base Grid:** 8px system. All margins, padding, and height constraints should be multiples of 8 (e.g., 8, 16, 24, 32, 48).
- **Minimum Touch Target (a11y):** All interactive elements (buttons, icons, list items) MUST have a minimum hit area of `48x48px` (Web) or `48.dp` (Android) to comply with mobile accessibility standards.
- **Border Radius:**
  - Small elements (Badges/Tags): `8px / 8.dp`
  - Medium elements (Cards/Inputs): `16px / 16.dp`
  - Large elements (Modals/Dialogs): `24px / 24.dp`
  - Full rounded (Pill buttons): `9999px / CircleShape`
