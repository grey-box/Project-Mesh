# Project Mesh

**Local communication between Android phones, with no internet or infrastructure.**

## What is Project Mesh?

Project Mesh is an open-source Android app by [Grey-Box](https://grey-box.ca) that lets nearby phones form their own network and talk to each other directly. Once two or more phones are connected, people can send messages and share files. A phone in the middle can also pass traffic along for others, so the network is not limited to devices within direct range of each other.

The app works entirely offline. It is built for places and moments where connectivity is unreliable, expensive, or absent: a classroom without Wi-Fi, a community without coverage, a field site, or an outage.

## Project Status

Project Mesh is an active research and development project. It is built and maintained largely by rotating student teams in partnership with Grey-Box.

The features listed under [What works today](#what-works-today) run on real phones, but the app is not yet a polished release. Ideas that are being investigated but are not implemented are listed under [What we are exploring](#what-we-are-exploring).

## Why Project Mesh exists

Mesh started with a question about UNI, Grey-Box's portable content server: if several UNI devices are deployed in the same area, could they connect to one another instead of operating as isolated hotspots?

Early research ran on rooted Android TV boxes, the hardware UNI used at the time. The project later moved to ordinary Android phones, which made it relevant well beyond UNI. That history left two guiding principles.

**1. Work with devices people already have.** In the environments Grey-Box serves, people are far more likely to own an Android phone than dedicated networking hardware. The goal is not to demonstrate mesh networking on ideal equipment but to make useful networking possible within the limits of consumer phones. Those limits are real: Android restricts networking permissions and Wi-Fi behavior, and phone models differ from one another. Teams learned early on: "Don't try to work against Android."

**2. Keep the network useful on its own.** Mesh does not need the internet to have value. A completely offline local network is the core of the vision. Extending a local network and providing internet access are separate problems, left for later expansion (see CyberCafe below).

Mesh is a separate project rather than a UNI feature because networking is its own area of work. Someone who only wants to send messages or files should not need a full UNI content server. Someone who needs hosted resources and persistent storage could use Mesh together with UNI. The applications stay independent and may later share libraries and integration points.

## What works today

- **Offline Wi-Fi mesh.** Phones connect to each other over Wi-Fi Direct or a local-only hotspot, with no router or internet. Routing between nodes is handled by [Meshrabiya](https://github.com/UstadMobile/Meshrabiya).
- **Join by QR code.** A phone that starts a hotspot shows a QR code. Scanning it from another phone connects the two. A connect link can be shared instead when scanning is not practical.
- **Messaging.** One-to-one chat with any node on the mesh. Conversations are stored on the phone.
- **File sharing.** Send files to another phone on the mesh, with transfer progress and the option to accept or decline on the receiving side.
- **Notifications.** New messages and incoming files raise a notification that opens the relevant chat or the Receive tab.
- **Network tools.** The Network tab lists the nodes on the mesh. Tap a node to see its ping time and hop count.
- **Log tab.** Logs can be viewed and copied in the app (debug builds only).
- **Settings.** Light, dark, or system theme, device name, and interface language (English, Spanish, French, Simplified Chinese).
- **Onboarding.** A short first-run flow explains what the app does and sets a username.

### Limits

- Android 8.0 (API 26) or newer is required.
- The app only works on physical devices. The Android emulator does not support the Wi-Fi features Mesh depends on.
- Extending the mesh beyond a single hotspot needs a phone that can host a hotspot and join another one at the same time. Not every phone supports this.
- How many hops a network can sustain, how far it can extend, and how it behaves as phones join and leave are open questions the team is measuring on real hardware.

## What we are exploring

These are ideas and research directions the team has discussed or prototyped. None of them are finished features.

- **Other transports.** Bluetooth and LoRa have been researched as alternatives or complements to Wi-Fi. LoRa is interesting even at very low bandwidth, for cases where getting a message across eventually matters more than speed.
- **Store-and-forward and moving devices.** Could a phone carry messages between groups that are never connected at the same time?
- **Identity and delivery confirmation.** How does a sender know a message is going to the intended person, and that it was actually delivered?
- **Content distribution over the mesh.** A proposal to run IPFS over Meshrabiya, so that files, videos, and documents can be located and fetched across multiple hops.
- **A local "old web".** Once people can run their own network, simple forums, local pages, and curated resources could make it more useful. This is an aspirational direction, but an interesting one.

An offline network is more resilient and gives communities more local control, but it should not automatically be described as private or secure. Those properties need to be designed and verified. They are part of the research agenda, not current guarantees.

## How Mesh fits into other Grey-Box projects

- **UNI** hosts content and resources on a portable server. Mesh could extend UNI's reach across many phones instead of one hotspot. Storage pooling and resource replication are possibilities to assess, not current integrations.
- **CyberCafe** manages and shares an available internet connection. Mesh would complement it by handling local connectivity, while CyberCafe handles external connectivity when it exists.
- **Scraper** prepares websites and content for offline use. Content prepared by Scraper and hosted by UNI could be reached over a Mesh network.

Mesh remains useful on its own, so these integrations are opportunities, not dependencies.

## Getting Started

### Requirements

- Two or more physical Android phones running Android 8.0 or newer, with USB debugging turned on
- [Android Studio](https://developer.android.com/studio) with Android SDK Platform 35
- JDK 17 if you build from the command line (Android Studio bundles one)

### Build and install

The simplest route is Android Studio: open the project, connect a phone, and press **Run**.

From the command line:

```bash
git clone https://github.com/grey-box/Project-Mesh.git
cd Project-Mesh
./gradlew :app:installDebug
```

This builds the debug app and installs it on the connected phone. To build without installing, run `./gradlew :app:assembleDebug`. The APK is written to `app/build/outputs/apk/debug/`.

Install the app on every phone that will take part in the network. The first build downloads dependencies, so it needs an internet connection. The app itself runs offline.

### First run

1. Open the app and allow the permissions it asks for: nearby devices, location, notifications, photos and videos, and camera. Android requires the location and nearby devices permissions for Wi-Fi Direct and local hotspots. The camera is used to scan QR codes.
2. Follow the onboarding screens and choose a username.
3. On the first phone, open the **Home** tab and tap **Start Hotspot**. A QR code appears.
4. On a second phone, open the **Home** tab, tap **Connect via QR Code Scan**, and scan the first phone's code. Repeat for each phone you want to add. A phone only needs to scan one neighbor to join.
5. Open the **Chat** tab to message a connected phone, or the **Send** tab to share a file.

## Architecture

- **Language and UI:** Kotlin, Jetpack Compose, Material 3
- **Pattern:** MVVM with a single UI state holder per screen. Screens are being migrated to this pattern, so older code may differ.
- **Networking:** Meshrabiya provides the virtual mesh network and manages the Wi-Fi Direct and hotspot connections. Application traffic (messages and file transfers) travels over HTTP. Each phone runs an embedded NanoHTTPD server and calls its peers with OkHttp.
- **Storage:** Room for conversations, messages, and users. SharedPreferences for settings.
- **Dependency injection:** Kodein

Packages live under `app/src/main/java/com/greybox/projectmesh/`:

| Package | Contents |
|---|---|
| `views/`, `viewModel/` | Screens and their ViewModels |
| `messaging/` | Chat UI, message and conversation storage, and the network handler for incoming messages |
| `server/` | The embedded HTTP server |
| `navigation/` | Bottom navigation and the app-level navigation host |
| `data/`, `db/`, `user/` | Settings repository, Room database, and user records |
| `arch/`, `app/` | Shared ViewModel base class and the app-level ViewModel |
| `components/`, `ui/theme/` | Reusable UI components, design tokens, and theme |
| `debug/`, `logging/` | Crash handling and log trees |
| `util/`, `extension/` | Helpers for locale, notifications, and networking |

## A guide to contributing

Project Mesh is developed in large part through university partnerships and capstone projects. Each team works on a focused mandate while the project maintains continuity across teams. If you are joining as a contributor:

1. **Branch from the current team branch, not from `main`.** Ask your Grey-Box lead which branch is active.
2. **Keep user-facing language simple.** The interface should feel like a communication tool, not a network administration console. People should not need to understand "client" and "server" to send a message.
3. **Keep what works and what is planned visibly separate.** Do not present planned functionality as if it works.
4. **Test on real phones**, from more than one manufacturer where you can. Wi-Fi behavior varies by device.
5. **Run the unit tests** before opening a pull request: `./gradlew :app:testDebugUnitTest`

Bugs and ideas are welcome as [issues](https://github.com/grey-box/Project-Mesh/issues).

## Project Links

- Website: [grey-box.ca](https://grey-box.ca)
- Source: [github.com/grey-box/Project-Mesh](https://github.com/grey-box/Project-Mesh)
- Networking library: [Meshrabiya](https://github.com/UstadMobile/Meshrabiya) by UstadMobile

## License

Project Mesh follows Grey-Box's open-source, open-core approach. The application is open source under the [MIT License](LICENSE). Specific integrations, deployment support, or extensions may be funded or paid work.

MIT © Grey-Box
