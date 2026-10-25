# Currency Rates

A small Android TV module card that opens USD → INR and EUR → INR reference rates,
plus the price of 8 grams of 22K gold in Chennai.
The Home card opens the full rates screen. An optional Home-only overlay shows both
rates directly beneath the brightness and Settings icons. Use the remote's OK button
on **Refresh rates** to update. Back returns to Home.

Currency rates come from [Currencylayer](https://currencylayer.com/), using its HTTPS
`/live` endpoint. A single USD-base response supplies USD/INR directly; EUR/INR is
USD/INR divided by USD/EUR. The provider's quote timestamp is displayed in IST,
including the time. A successful request does not imply that the quote has changed.
Freshness and request allowance depend on the owner's Currencylayer subscription.

The latest successful response is cached locally. If an update fails, saved values stay
visible with their original timestamp and an update-unavailable message.

## Chennai gold

Gold uses the public [Golden Chennai page](https://rates.goldenchennai.com/gold-rate/chennai-gold-rate-today/).
The app reads the dated Chennai table's explicitly labelled 22K per-gram column and
multiplies it by 8 using decimal arithmetic. The overlay shows purity, city, weight,
source and the gold quote's own date, separately from the currency date. This is an
indicative metal value; a jeweller's final bill can include taxes and making charges.

No API key is needed. The page is fetched over HTTPS, with a 512 KB response limit.
If the source changes its columns, loses its date, blocks requests or fails, the app
keeps the last validated gold quote and date. It never substitutes 24K, global spot
prices or another city's rate. Gold and currency caches update independently.

## Home overlay

Open **Currency Rates → Home overlay settings**, then enable **Currency Rates Home
Overlay** in Android Accessibility and accept Android's confirmation. Press Home to
show the overlay. Disable that service in the same settings to remove it.

The panel is non-interactive and never takes remote focus. It is positioned 56 dp
from the right and 116 dp from the top for the tested TV. A compact translucent charcoal panel, rounded corners, white
typography and two separated currency columns match the Home controls. Text and
vertical spacing use a 75% scale for a shorter panel; width and anchor stay fixed. The overlay hides when
another app or a launcher dialog becomes active, and when the screen turns off.
Android binds the service after it is enabled; no boot receiver is required.

The service listens only for window-state changes and compares the package and
activity names. It has no screen-content retrieval, key filtering, gesture or
screenshot capability. It waits for a Home event after connecting. Force-stopping
the add-on stops its accessibility service until Android binds it again.

Currency refreshes use the configured interval while Home is visible (1440 minutes
for Free, 60 for Basic, 10 for Professional, or 1 for Business). Gold refreshes after six
hours; failed requests retry after 15 minutes while visible. Leaving Home cancels
the timer; an already-started request may finish and save its result. The full-screen
card's **Refresh rates** button requests both sources and updates their shared caches.

## Permissions

- `INTERNET`: HTTPS requests to `api.currencylayer.com` and `rates.goldenchennai.com` when opened, manually refreshed, or due for refresh while the Home overlay is visible.
  Currencylayer receives your configured API key with its HTTPS request. Golden Chennai
  receives only its page request. No location or other account information is sent.
- `BIND_ACCESSIBILITY_SERVICE`: protects the optional Android-bound accessibility
  service. The owner enables it in TV settings to observe foreground window changes
  and draw a `TYPE_ACCESSIBILITY_OVERLAY`. No separate draw-over-apps permission is
  needed. This service remains bound while enabled; its timer runs only on Home.
- `WRITE_SECURE_SETTINGS` protects the exported, write-only configuration provider;
  the app does not request this permission. Only ADB shell or another already-trusted
  system caller can provision the key. The provider cannot return credentials.
- No foreground service, usage-access permission, key interception, ads or telemetry.
- No changes to the launcher or its permissions.

## Build and install

Requires JDK 17 and Android SDK 34, like the other add-ons.

```sh
cd addons/currency-rates
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

Install the locally built APK using [the add-on instructions](../README.md#how-an-add-on-works).
On Home, choose **Show card** when prompted. A release uses the local debug signing key,
like the existing add-ons; retain that key for updates.

## Currencylayer configuration

Keep your own JSON configuration outside tracked source, for example in ignored `work/`:

```json
{"accessKey":"YOUR_CURRENCYLAYER_KEY","refreshMinutes":1440}
```

The key is provisioned at runtime, never built into the APK. Transfer that private file
to `/data/local/tmp/currencylayer.json` using the existing transfer tools, then write it
into the app's private storage:

```sh
TV_HOST='<tv ip>' tools/venv/bin/python tools/tv.py 'content write --uri content://com.cleanyourtv.rates.currencyconfig/config < /data/local/tmp/currencylayer.json'
TV_HOST='<tv ip>' tools/venv/bin/python tools/tv.py 'rm -f /data/local/tmp/currencylayer.json'
```

Open the card and use Refresh afterwards. Configuration is write-only through this
provider, app backup is disabled, redirects are refused for credentialed API requests,
and network exceptions and request URLs are not logged. Store local configuration
with file permissions `600`; never commit it or include it in screenshots.

## Tested on

MiTV-AXSO2, Android 9, firmware PI.4802 release-keys. Verified installation,
Home card discovery and display, HTTPS rate loading, and the dated rate screen.
Verified the enabled overlay beneath the Home controls, hiding when the rates app
opens, returning on Home, and D-pad navigation passing through. Verified the dated Chennai 22K quote loads on the TV and that 8 grams is
calculated from the per-gram rate. Verified authenticated Currencylayer loading and
provider quote timestamps in IST on the TV. Local screenshots are kept in the ignored `shots/` folder.
