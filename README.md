# XDroid360

**XDroid360** is an unofficial, community-developed launcher that runs Xbox 360 games on Android
phones. Today it plays **Dragon Age: Origins**, **State of Decay** and **Mass Effect**, with a gamepad
or the on-screen controls. The games' code runs natively on the phone's processor, and a native renderer
draws their graphics directly with Vulkan.

> [!NOTE]
> XDroid360 does not include any game. It installs **your own copy**: you add the game's disc image
> or a `.zip`, and the phone turns it into a game you can play. Nothing of the games comes with the
> app or is stored here.

> [!WARNING]
> **XDroid360 is in alpha (0.1.3).** It is developed on a **Snapdragon flagship**, and players run it
> on Mali phones too. Back up your saves before you play (the **Export** tab at the bottom does
> that).

## Download only from GitHub

Get XDroid360 only from the [Releases page](https://github.com/udarmolota/XDroid360/releases). It is
signed with my own key. Certificate fingerprint (SHA-256):

```
F8:5A:43:7A:50:90:FB:84:B9:EB:2A:4C:BF:F5:AB:C0:7E:4D:3B:A2:D5:C4:BE:02:59:63:6F:3A:62:4E:D2:C6
```

You can check it in apps like MT Manager or App Manager. The real XDroid360 also installs over itself
as an update: if Android refuses to update your copy with the one from GitHub, your copy is not the
original.

> [!IMPORTANT]
> **Antivirus warnings are false positives.** XDroid360 carries a compiler (clang, lld) and a
> recompiler that build each game's code on your phone, and some scanners don't like that kind of
> tool: Google (Chrome's download check, Google Drive, Play Protect) and a few antivirus apps may flag
> or block the APK. Download it from the Releases page here, in Chrome tap **Keep** if it asks (or use
> another browser), and check the certificate fingerprint above. Don't take it from re-uploads.
>
> Want a second opinion? Everything is open: give an AI assistant you trust this repository's link
> (or the APK) and ask what the app does and why scanners flag it. The APK is built from this code by
> GitHub's own servers ([Actions](https://github.com/udarmolota/XDroid360/actions)), with the build
> log public.

## How to play

1. Install XDroid360 from the [Releases page](https://github.com/udarmolota/XDroid360/releases).
2. Copy your game to the phone's own storage.
3. In the launcher, tap **+**, choose the game and pick its file.
4. Check the card (which game it is, the space it needs) and tap **Build**. Installing takes
   **5–20 minutes** for *Dragon Age: Origins* and *State of Decay*, and **30–60 minutes** for
   *Mass Effect* (it prepares about 30,000 shaders). You can lock the phone or leave the launcher
   meanwhile: the notification shows the progress.
5. Tap **Play**.

After an update of the launcher, the game asks to be updated when you tap **Play** (or use **Update
game**, ⋮ on the game's card): it rebuilds the game for the new version and keeps its files, saves and
settings. Until then it runs the old version.

## Supported games

| Game | What to add | State |
|---|---|---|
| **Dragon Age: Origins** | the Xbox 360 disc image (`.iso`, or a `.zip` with it): the English/Italian/Spanish edition, disc 1 of the Ultimate Edition, the Russian (*Начало*) or the German edition. Then, from the **Import** tab: your DLC packages and Title Update 6 or 7 | playable; frame rate limit 30, 60 or none. DLC such as Warden's Keep and The Stone Prisoner just need importing; **Awakening** and the later campaigns need Title Update 7 too |
| **State of Decay** | a `.zip` with the game's `584111E8` folder (the Xbox Live Arcade game as a console stores it). For now **one build of the base game only**: version 6, the package `000D0000/83B728DD…` (the original version 2, `575B340E…`, isn't supported yet). The base game alone is enough; an archive that also has the DLC **Breakdown** and **Lifeline** and Title Update 6 works as it is | playable at 30 or 60 fps. The DLC get installed; playing them hasn't been checked yet |
| **Mass Effect** | the Xbox 360 disc image (`.iso`, or a `.zip` with it): for now **one edition only**: the English/Spanish disc with `default.xex` version 0.0.0.5 | new in 0.1.3: plays with the native renderer only, at 30 fps for now; the first install is long (see above) |

### Supported editions

Other editions of a game may use a different version of its code. The launcher checks this when you
add the game and tells you if your edition isn't supported yet.

| Game | Edition | Disc's media ID |
|---|---|---|
| *Dragon Age: Origins* (`454108C0`) | English/Italian/Spanish (*Dragon Age - Origins (World) (En,Es,It)*), and disc 1 of the Ultimate Edition | `186A1115` |
| | Russian (*Dragon Age: Начало*) | `26111665` |
| | German (*Dragon Age - Origins (Germany)*) | `57EF80D8` |
| | Title updates: 6 and 7 for the English/Italian/Spanish edition; 7 for the Russian and the German | |
| *State of Decay* (`584111E8`) | the Xbox Live Arcade game, version 6: base package `83B728DD6B9CC37BDA757BACE34ACCE63D66BC8358`; the DLC Breakdown and Lifeline are installed if present; a title update isn't needed (the code is always built from the base game; only its Russian text is used) | `25FA348E` |
| *Mass Effect* (`4D5307E8`) | the English/Spanish edition with `default.xex` 0.0.0.5 | `572BA75D` |

Your disc's media ID is in its `default.xex`; if your edition isn't listed, a bug report from the
launcher (or the edition's name) helps add it.

## Features

- ✔️ **Native code**: each game's code is translated for the phone's ARM processor when you install
  it
- ✔️ **Native renderer**: the games' graphics drawn directly with Vulkan, with their shaders built on
  the phone from your copy. Xenia's emulation of the console's GPU stays one tap away in the game's
  settings
- ✔️ **Update game**: rebuilds a game for a new launcher version, keeping its files, saves and settings;
  the game asks for it when the launcher brings a newer version
- ✔️ **DLC and title updates** for *Dragon Age: Origins* from the **Import** tab
- ✔️ **Game settings**: frame rate limit, game language, texture quality and renderer, for each game
- ✔️ **On-screen controls with an editor**: move the buttons and sticks; every installed game keeps its
  own layout. Physical gamepads (Bluetooth or USB) work too
- ✔️ **Import / Export**: saves and the on-screen gamepad layout, to a `.zip` or a small file, for
  another phone or another game card
- ✔️ **Vulkan drivers**: Turnip is built in and used by default on Adreno 7xx and 8xx; each game can
  use it, the phone's own driver, or any other driver you install (a `.so`, or an Adreno Tools `.zip`)
- ✔️ **Info panel**: off, only FPS, or a full bar with GPU and CPU load, memory, power, temperature
  and an FPS graph
- ✔️ **Full screen**, with no system bars over the game
- ✔️ **Report a bug** from the menu: pick your mail app, write a few words, and the logs are attached.
  **Diagnostics** in the game's settings adds the GPU's measurements to them
- ✔️ **Projects**: my other launchers, one tap away
- ✔️ Vibration on the on-screen buttons

## Project status & what to expect

XDroid360 is built by **one person**, with the help of AI, and grew out of
[Zomdroid](https://github.com/udarmolota/zomdroid), [RimDroid](https://github.com/udarmolota/RimDroid)
and [ValDroid](https://github.com/udarmolota/ValDroid). A few honest notes:

- **Installing takes time and space.** The phone builds each game itself. *Dragon Age: Origins* needs
  about 8 GB (and about 8 GB more while a `.zip` is unpacked); *State of Decay* about 3 GB (and about
  2 GB more); *Mass Effect* about 9 GB. The card tells you before anything starts.
- **Phones get hot.** At 60 fps the GPU slows down after some minutes. The 30 fps limit (the game's
  card in the launcher), or a cooler, is much kinder to your hands and your battery.
- **The first minutes are slower.** The graphics' shaders are prepared once and cached; later sessions
  are smoother.
- **Text input** (a character's name) is Latin letters only: the games' fonts have no others.
- *Dragon Age: Origins*: the text and voices are what your disc has (English, Italian or Spanish;
  Russian or German on those editions). On the native renderer a white flash can show at the start
  of a conversation, and eyelashes may look white on the English disc.

## Device compatibility

| GPU | State |
|---|---|
| Adreno 8xx (Snapdragon 8 Elite) | tested on a Galaxy S25 Ultra |
| Adreno 7xx (Snapdragon 8 Gen 1 / 8+ Gen 1 / 8 Gen 2) | works, confirmed by players and tested on an ROG Phone 6 |
| Adreno 6xx | works from Adreno 640 (Snapdragon 855): *State of Decay* at about 20 fps on a Galaxy S10e. Snapdragon 845 (Adreno 630) and older on their stock driver: no picture on the native renderer |
| Mali | newer Mali (G57, G77, G610/G615, G7xx) works: *Dragon Age: Origins* and *Mass Effect* play even on budget Mali-G57 phones; 0.1.3 fixes State of Decay's pink and black patches. Older Mali (G51, G52, G71, G72, G76: Helio G85, G90T, G95…) can't run the native renderer |
| Xclipse | works: *State of Decay* at 20–30 fps on a Galaxy S24+ (Exynos 2400) |
| PowerVR | untested |

If you try another phone, a report from **Report a bug** is very welcome.

## System requirements

The games differ a lot: *Dragon Age: Origins* and *Mass Effect* run on budget phones, *State of
Decay* needs a fast one.

- **Minimum (all games):** Android 11+, ARM64, 6 GB of RAM, the free storage above, and a graphics
  driver with Vulkan 1.2 (or Vulkan 1.1 with descriptor indexing). In practice: Adreno 640
  (Snapdragon 855) or newer, Mali-G57 / G77 or newer, Xclipse.
- **Not supported by the native renderer:** older Mali (G51, G52, G71, G72, G76) and Snapdragon 845
  or older on the stock driver. The game starts, but the screen stays one colour.
- **What to expect:**

| Game | Budget (Mali-G57, e.g. Dimensity 6100+, Helio G99) | Mid-range (Mali-G615, Dimensity 7300 / 8300) | Flagship (Snapdragon 8 Gen 2 and newer) |
|---|---|---|---|
| *Dragon Age: Origins* | playable | about 60 fps | 60 fps |
| *Mass Effect* | playable, with some stutters (30 fps for now) | — | smooth (30 fps for now) |
| *State of Decay* | under 10 fps | 18–55 fps | 40–57 fps (Snapdragon 8 Elite) |

*Mass Effect*'s first install needs the most memory and time (30–60 minutes).

## Getting the best performance

- **Turn on your phone's game booster** at its highest-performance profile: Samsung *Game Booster*,
  Xiaomi/POCO *Game Turbo*, Realme/OPPO/OnePlus *Game Space*, vivo/iQOO *Ultra Game Mode*.
- **Limit the frame rate to 30** for long sessions (the game's card in the launcher).
- **Close background apps** so the game gets the memory to itself.

## How it works

The games were made for the Xbox 360's PowerPC processor. When you install one, the launcher
**statically recompiles** its code: the phone translates the machine code from your copy into C++
(XenonRecomp) and compiles it for ARM64 (clang and lld, built into the app), in about 5–20 minutes.
The result runs with the game's runtime: [DAORecomp](https://github.com/96dot/Dragon-Age-Origins-Recomp)
by 96dot for *Dragon Age: Origins*, and SoDRecomp and ME1Recomp, derived from it, for *State of
Decay* and *Mass Effect*. They stand
in for the console's system. The graphics go through a native renderer: the game's draw calls are
turned into Vulkan directly, and its shaders, taken from your copy, are translated on the phone
(XenosRecomp and DirectXShaderCompiler). Xenia's Vulkan backend, which emulates the console's GPU,
remains as the other choice.

## Supporting development

This is an independent project. To help keep it going, contributions are welcome via
[Ko-Fi](https://ko-fi.com/udarmolota).

## Feedback

Please report issues via [GitHub Issues](https://github.com/udarmolota/XDroid360/issues). The quickest
way to report a problem is **Report a bug** in the side menu: choose the game, pick your email app,
add a short description of what happened, and send. The logs are attached for you.

## Credits & Third-Party Sources

- [DAORecomp](https://github.com/96dot/Dragon-Age-Origins-Recomp) by 96dot: the *Dragon Age: Origins*
  runtime, and the base of *State of Decay*'s and *Mass Effect*'s
- [XenonRecomp](https://github.com/hedge-dev/XenonRecomp) by hedge-dev: PowerPC → C++ translation
- [XenosRecomp](https://github.com/hedge-dev/XenosRecomp) by hedge-dev and Microsoft's
  [DirectXShaderCompiler](https://github.com/microsoft/DirectXShaderCompiler): the games' shaders
  for the native renderer
- [Xenia](https://github.com/xenia-project/xenia) / xenia-canary: the emulated Vulkan graphics backend
- [LLVM](https://llvm.org) clang and lld: compiling the games on the phone
- [Mesa Turnip](https://gitlab.freedesktop.org/mesa/mesa), StevenMXZ's
  [Gen8 build](https://github.com/StevenMXZ/Adreno-Tools-Drivers): the Adreno Vulkan driver
- [SDL 3](https://github.com/libsdl-org/SDL), [FFmpeg](https://github.com/has207/FFmpeg) (XMA audio),
  [liblinkernsbypass](https://github.com/bylaws/liblinkernsbypass)
- [Zomdroid](https://github.com/udarmolota/zomdroid), [RimDroid](https://github.com/udarmolota/RimDroid)
  and [ValDroid](https://github.com/udarmolota/ValDroid): the launchers XDroid360 grew out of

All components and their licences: [NOTICE.md](NOTICE.md). XDroid360's own code is MIT (`LICENSE`).
The native renderer in [`native-renderer/`](native-renderer) is GPL-3.0: it comes from
[nfsmw-android](https://github.com/codepdbh/nfsmw-android) by codepdbh (after nfsmw-nx by
StevensND, NFSMW-Recompiled by madelrandel-blip and NFSMW-Recompiled-Mobile by victorgbd). Both
games' libraries link it, so those libraries and the APK are GPL-3.0 as a whole.

### Source code

Everything in the APK is built from public sources: this repository at the release's tag (the
launcher, the native renderer, and the on-phone toolchain in [`toolchain/`](toolchain)), the
[DAORecomp fork](https://github.com/udarmolota/Dragon-Age-Origins-Recomp-Android),
[SoDRecomp](https://github.com/udarmolota/SoDRecomp) and [ME1Recomp](https://github.com/udarmolota/ME1Recomp). The recompiler the launcher runs,
`libxenonrecomp.so`, is GPL-2.0-or-later as a whole (its disassembler comes from GNU binutils);
its exact source is [XenonRecomp](https://github.com/hedge-dev/XenonRecomp/tree/ddd128bcca99fe8bfbb99bea583c972351fa6ace)
at `ddd128b`, patched by
[`scripts/patch_recompiler.py`](https://github.com/udarmolota/Dragon-Age-Origins-Recomp-Android/blob/b7bd9be312a2160b03592b5b0177047629c36d45/scripts/patch_recompiler.py)
at `b7bd9be`, built by [`toolchain/build_tools.sh`](toolchain/build_tools.sh).

*Dragon Age* and *Mass Effect* are trademarks of Electronic Arts; *State of Decay* is a trademark of
Undead Labs and Microsoft. XDroid360 is not affiliated with or endorsed by them.
