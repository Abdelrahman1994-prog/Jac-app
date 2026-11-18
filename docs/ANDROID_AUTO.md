# Android Auto — feasibility and decision

_Reviewed November 2026 against Google's current documentation._

## What Google allows

Android Auto doesn't show an ordinary phone app on the car screen. An app appears only if it
belongs to one of the supported categories and is built with the matching APIs: mostly the Android
for Cars App Library templates, or the media/messaging APIs. The current categories on
developer.android.com/training/cars are:

| Category | What it is for | Android Auto |
|---|---|---|
| Media (audio) | Music, radio, audiobooks | ✔ |
| Messaging, calling | Messages, notifications, VoIP calls (calling and templated messaging are still testing-track only) | ✔ |
| Navigation | Turn-by-turn directions | ✔ |
| Point of interest | Finding and navigating to parking, charging and fuel stations | ✔ |
| Internet of Things | *"take relevant actions on connected devices … opening a garage door, flipping home light switches, enabling home security"* | ✔ |
| Weather | Weather at your location or along the route | ✔ |
| Games | **Only while parked** | ✔ |
| Video, browsers | Parked only, Android Automotive OS only | ✗ |

All of these render through Google's driver-safety templates (lists, grids, panes, maps, and so
on). An app can't draw its own UI, such as custom gauges, on the Android Auto screen. Apps outside
these categories aren't accepted on Google Play for Android Auto.

## Where JAC DRIVE fits

JAC DRIVE is a **vehicle monitoring and diagnostics** app. That isn't one of the categories:

* It isn't media, messaging, navigation, a parking/charging/fuel locator, weather or a parked game.
* **IoT** is for *controlling external connected devices* (garage doors, home lights). Showing
  your own car's engine data isn't that. Publishing under IoT would misdescribe the app and
  would be expected to fail review.
* A full digital instrument cluster needs custom drawing, which the templates don't allow on
  Android Auto even inside a supported category.

**Decision:** JAC DRIVE does **not** implement an Android Auto car-screen interface, and it doesn't
use screen mirroring, the Android Auto developer "unknown sources" setting, or any other
workaround to get one. The full cockpit stays on the phone, mounted in landscape.

## What works with Android Auto

* **Voice alerts through the car's speakers.** JAC DRIVE speaks alerts with
  `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` audio and transient ducking focus. With Android Auto or
  Bluetooth audio connected, the head unit plays them over the music, like a navigation prompt
  (engine temperature, battery voltage, check-engine lamp, adapter disconnected, high RPM).
* **The phone cockpit and Android Auto can run at the same time.** The OBD link runs in a
  foreground service, so live data, trip recording and alerts keep working while Android Auto
  uses the head unit.

## Future platforms

* If Google adds a category that fits vehicle-information apps, a template-based companion (for
  example a `PaneTemplate` showing coolant temperature, battery voltage and active fault codes)
  could be added in a new `auto` module. `core` and `obd` have no phone-UI dependencies and
  can be reused unchanged.
* **Android Automotive OS** (an infotainment system running Android natively) has the same
  category rules for driving use. The JAC J7's head unit isn't documented as Android Automotive
  OS, so nothing is assumed here.

## Sources

* Supported app categories: https://developer.android.com/training/cars
* Car app quality guidelines: https://developer.android.com/docs/quality-guidelines/car-app-quality
* Building IoT apps for cars: https://developer.android.com/training/cars/apps/iot
