# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Mobile device users requiring continuous, non-intrusive biometric security. Users enroll their natural keystroke rhythm on an Android custom keyboard (IME), protecting device sessions from impostors, unauthorized access, and physical theft.

## Product Purpose

Continuous keystroke dynamics authentication for Android that monitors user typing timing patterns (flight times, dwell times) in real-time. It detects behavioral anomalies using One-Class SVM (OCSVM) and Mahalanobis distance models to prevent unauthorized usage without disrupting legitimate users.

## Positioning

Unlike conventional one-time biometric authentication (fingerprint/face unlock at lock screen), PuKey provides zero-trust, continuous behavioral verification inside the active session. If an unauthorized individual takes over an unlocked phone, their distinct typing cadence triggers stepped-up authentication or automatic lockdown.

## Operating Context

- Android native application running on Android 8.0+ (API 26+) with Target SDK 34.
- Works as a custom Android Input Method Editor (IME) service (`KeystrokeIMEService`) integrated system-wide.
- Device Admin receiver (`ThesisDeviceAdminReceiver`) enabled to enforce device locking when risk thresholds are breached.
- Real-time scoring pipeline: raw keystroke events → feature extraction (statistical windowing) → dual anomaly detectors → risk composite engine → policy enforcement (ALLOW, WARN, STEP_UP_AUTH, BLOCK_SESSION).

## Capabilities and Constraints

- **Onboarding & Enrollment**: Multi-step onboarding guiding users through value proposition, keyboard enablement, device admin permissions, and guided prompt typing (pangrams/passages) to gather calibration vectors (250–500 keystrokes).
- **Dashboard & Verification**: Real-time biometric status monitor, live risk score cards, quick verification testing sandbox, and security event log audit trail.
- **Biometric Security Engine**: SQLite persistence for user profiles, local model persistence (JSON format for OCSVM and Mahalanobis matrices), local broadcast event bus for zero-latency UI updates.
- **Material 3 Expressive UI**: Vibrant Electric Cyan & Security Slate styling, expressive 24–28dp curved container cards, pill interactive elements, expressive typography and stepped progress indicators.

## Product Principles

1. **Seamless Biometric Guidance**: Make keyboard and permission enrollment effortless, transparent, and rewarding with guided typing prompts.
2. **Instant Visual Clarity**: Communicate security states (Allow, Warn, Step-Up, Block) with high-contrast, expressive status indicators and tactile feedback.
3. **Respect Android Idioms**: Native Material 3 components, strict 48dp touch targets, system back handling, full light and dark mode fidelity.
4. **Zero-Trust Continuous Verification**: Transparently score typing behavior without interrupting the legitimate user.

## Accessibility & Inclusion

- Follow Android accessibility standards: minimum 48x48dp touch targets with proper spacing.
- Support dynamic system font scaling with `sp` units and scalable layouts.
- Provide strong contrast ratios for text and indicators in both Light and Dark themes.
