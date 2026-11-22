# FARCAST feature inventory (Section 2.6)

Every `<div class="dpanel">` in the supplied `index.html`, what it actually
calls, and where it lands natively. This is Stage 1 of Section 9.

| # | Website panel | Real APIs it calls | Android module (this pass) | Status |
|---|---|---|---|---|
| 1 | LIVE AVIATION (`#p-av`) | OpenSky via `api.allorigins.win` CORS proxy, fallback `api.adsb.lol/v2/ladd` | `LiveFeeds.kt` → `AviationService` | Built |
| 2 | CELESTRAK (`#p-or`) | `celestrak.org/NORAD/elements/gp.php` | `LiveFeeds.kt` → `CelestrakService` | Built |
| 3 | USGS QUAKES (`#p-qk`) | `earthquake.usgs.gov/.../summary/*.geojson` | `LiveFeeds.kt` → `UsgsQuakeService` | Built |
| 4 | ISS LIVE (`#p-iss`) | `api.wheretheiss.at`, crew via `open-notify.org` (proxied) | `LiveFeeds.kt` → `IssService` | Built |
| 5 | PUBLIC CAMS (`#p-cam`) | TfL JamCams, hardcoded curated-cam list, Windy Webcams API v3 | `PublicCamsService.kt` | Built |
| 6 | IP GEO + THREAT (`#p-ip`) | GeoJS, Shodan InternetDB, URLhaus, GreyNoise community, ThreatFox, AbuseIPDB | `IpThreatService.kt` | Built |
| 7 | DOMAIN / CT / DNS (`#p-dom`) | RDAP, Google DoH, crt.sh, urlscan.io, Wayback Machine, typosquat DNS probing | `DomainIntelService.kt` (now backs `DomainRdapSource`) | Built |
| 8 | PERSONA (`#p-ps`) | Quick-7 (GitHub/Reddit/GitLab/Codeforces/HN/Dev.to/Keybase), live Sherlock (400+ sites), live Maigret (500+ sites), email MX+Gravatar | `PersonaIntelService.kt` (now backs `PersonaUsernameSource`) | Built — DNS/MX part only for phone |
| 9 | OPSEC MIRROR (`#p-mir`) | Browser WebRTC leak check, browser fingerprint, DNS-resolver leak test | `OpsecMirrorService.kt` | Partial — see note below |
| 10 | CASE DB (`#p-db`) | sql.js (SQLite compiled to WASM) | `CaseDatabase.kt` — **native Room/SQLite**, not a WASM port | Built, upgraded |
| 11 | SQLMAP-STYLE TESTING (`#p-sqli`) | none — deliberately disabled in the source, with a refusal explaining why | `SqliTestingNotice.kt` | Preserved as-is, on purpose |
| 12 | LOCATION PICKER (`#p-pk`) | OSM tiles, NASA GIBS, Esri World Imagery | UI concern (Leaflet + Three.js globe) | **Deferred to Section 5 (UI)** |
| 13 | RE / EMU / MEDIA LAB (`#p-lab`) | Rizin WASM, WABT WASM, Capstone/Keystone/Unicorn WASM (RET), FFmpeg.wasm | — | **Deferred — see note below** |
| 14 | POST-QUANTUM CRYPTO (`#p-pq`) | pure-JS demo, explicitly labeled non-production in the source | `PqCryptoDemo.kt` | Built, same "demo" labeling kept |
| 15 | API KEYS (`#p-keys`) | `localStorage` | `SecureKeyVault.kt` — **EncryptedSharedPreferences**, not plaintext localStorage | Built, upgraded |
| 16 | LOG (`#p-lg`) | in-memory JS array | belongs with Case DB — every log line is already a `CaseFile`/`Evidence` row via DarkMixer | Covered by existing `CaseFile`/`Evidence` model |

## Notes on the two deferred items

**#13, RE/EMU/MEDIA LAB** — the website loads five different WASM cores
(Rizin, WABT, a Capstone+Keystone+Unicorn bundle, FFmpeg.wasm) from CDN URLs
at runtime. The Android equivalents exist (Capstone/Keystone/Unicorn all
ship official Android `.aar`/`.so` builds; Rizin's core is a C library
buildable for Android; FFmpeg has community Android builds) but picking
exact current artifact coordinates and verifying ARM32 availability for
each is its own research task — I'm not going to paste dependency versions
I haven't verified just to make this table look complete. Flagging it
honestly rather than shipping possibly-stale/wrong Gradle coordinates.

**#9, OPSEC MIRROR** — WebRTC-leak detection is a *browser* API
(`RTCPeerConnection` ICE candidate gathering); there's no Android
equivalent of "does my browser leak my local IP over WebRTC" because
there's no browser involved. What ported for real: the DNS-resolver leak
test (just HTTP calls to Cloudflare/Google/Quad9's DoH endpoints, platform-
agnostic) and a device/network fingerprint reframed for what Android
actually exposes (network type, locale, screen metrics, installed
sensors) rather than browser canvas/WebGL fingerprinting, which doesn't
apply here either.

**#12, LOCATION PICKER** — this is a map-and-overlay UI, not a service
with logic to port; it's Section 5 (Jetpack Compose UI), already on the
"still ahead" list from the previous README.
