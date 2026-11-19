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

**Decision (personal build, at the owner's request):** the owner wanted the full cockpit on
the JAC J7 screen, for **personal use only**. Android Auto gives a freely drawable surface only to
**navigation** apps. This build therefore declares `androidx.car.app.category.NAVIGATION` and
draws the cockpit (two large gauges plus coolant, battery, fuel and load) on the map surface of a
`NavigationTemplate`.

> ⚠️ This category declaration is **not accurate** for this app. It must stay on Internal App
> Sharing or internal testing for the owner's own phone. Publishing it publicly would violate Google
> Play policy and could put the developer account at risk. Google may also change Android Auto so
> this stops working.

Side effects:
* Android Auto runs one navigation app at a time, so opening JAC DRIVE on the car screen replaces
  Google Maps until you switch back.
* The host's own controls overlay part of the surface. The cockpit is drawn only inside the
  visible area the host reports.

Driving-safety choices: no interaction is needed, the numerals are large, nothing blinks, it
redraws only when new data arrives (at most about 10 times per second), and demo data is labelled.
Faults and alerts open as standard list templates. Scanning and clearing stay on the phone, while
parked.

### Why Internal App Sharing is needed

Android Auto's "Unknown sources" developer option applies to media, messaging and parked apps,
**not** to Car App Library apps. Those must be installed from a trusted source: Google Play,
including Internal App Sharing and internal test tracks (developer.android.com/training/cars/testing).

### Install on your phone (one-time setup)

1. **Back up first:** JAC DRIVE → Settings → Backup. The Play-installed copy has a different
   signature, so the side-loaded copy must be uninstalled first.
2. Create a Google Play Console developer account (play.google.com/console, one-time fee).
3. Play Console → **Internal app sharing** → add your Google account as an uploader → upload
   `app-release.aab` (or the APK). Copy the link Play gives you.
4. On the phone: Play Store → Settings → About → tap **Play Store version** 7 times →
   **General → Developer options → Internal app sharing: ON**.
5. Uninstall the side-loaded JAC DRIVE, open the link on the phone, and install.
6. Open JAC DRIVE → Settings → **Restore from backup**.
7. Connect to the JAC J7. On the Android Auto screen, open the app launcher → **JAC DRIVE**. If it's
   missing: phone → Android Auto settings → **Customize launcher** → enable JAC DRIVE.

If Play refuses the upload because of the signing key, create your own release key (see the README's
keystore.properties section), rebuild and upload again. Keep that key private.

## What works with Android Auto

* **Voice alerts through the car's speakers.** JAC DRIVE speaks alerts with
  `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` audio and transient ducking focus. With Android Auto or
  Bluetooth audio connected, the head unit plays them over the music, like a navigation prompt
  (engine temperature, battery voltage, check-engine lamp, adapter disconnected, high RPM).
* **The phone cockpit and Android Auto can run at the same time.** The OBD link runs in a
  foreground service, so live data, trip recording and alerts keep working while Android Auto
  uses the head unit.

## Future platforms

* The Android Auto screens live in `app/src/main/java/com/jacdrive/app/auto`. `core` and `obd` have
  no phone-UI dependencies, so they can be reused unchanged.
* **Android Automotive OS** (an infotainment system running Android natively) has the same
  category rules for driving use. The JAC J7's head unit isn't documented as Android Automotive
  OS, so nothing is assumed here.

## Sources

* Supported app categories: https://developer.android.com/training/cars
* Car app quality guidelines: https://developer.android.com/docs/quality-guidelines/car-app-quality
* Building IoT apps for cars: https://developer.android.com/training/cars/apps/iot
* Testing in real vehicles / unknown sources: https://developer.android.com/training/cars/testing
* Internal App Sharing: https://support.google.com/googleplay/android-developer/answer/9844679
