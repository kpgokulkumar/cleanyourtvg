# Optional streaming backdrop (personal fork)

When an eligible dock tile stays focused for 400 ms, Home starts an atmospheric selection of up to six recent films and TV series
listed for that provider's subscription service in India. Three films and three
series alternate, advancing every 14 seconds with a fading backdrop, a compact
provider/year label and a position counter. Rotation stops when Home leaves the
foreground or focus leaves the dock provider. Metadata/images come
from TMDB; provider availability is supplied by JustWatch. Netflix, Amazon Prime
Video, JioHotstar, Apple TV, Sony Liv, Zee5 and Sun Nxt are matched by provider name from
TMDB's India provider list, rather than assuming IDs. Unknown apps and
local players keep the normal theme. YouTube can instead use a local
`youtube-wallpaper.jpg` in Home's external files directory. It is shown only
while the YouTube dock tile is focused, without title or synopsis. Use a JPEG
under 5 MB; the image fills the screen with a centered crop. Personal photos
should stay outside Git. This uses the same optional artwork controller, so
enabled private configuration is required. Only dock focus triggers requests. Apple TV matches the Apple TV subscription
service, excluding Apple TV Store rentals/purchases and third-party channels.

Films are sorted by release date and series by their first air date, excluding future releases,
adult titles and titles without a backdrop, with at least 20 TMDB votes. This
is **not** a date-added feed or a guarantee that your plan can play a title.
Provider metadata can lag. A series with a new season may have an older first air date.

## Change the YouTube wallpaper

In the lounge layout the picture behind Home is `youtube-wallpaper.jpg` while the YouTube dock tile is
focused (the theme's `wallpaper.jpg` sits underneath it). To change it:

1. Make a JPEG of about 1920×1080 and under 300 KB (5 MB is the hard limit). Apply any EXIF rotation to the
   pixels and drop the tag: a phone photo that looks upright on a Mac can be stored sideways, and the
   launcher shows what is stored. With Pillow, `ImageOps.exif_transpose(im).convert("RGB").resize((1920, 1080))`
   then `save(path, quality=62)` does both.
2. Keep the old picture, then push the new one (`TV_HOST` is the TV's IP):

   ```sh
   export TV_HOST=<tv ip>
   D=/sdcard/Android/data/com.gathin.tvhome/files
   tools/venv/bin/python tools/b64push.py new.jpg /data/local/tmp/yt.jpg
   tools/venv/bin/python tools/tv.py "cp $D/youtube-wallpaper.jpg $D/youtube-wallpaper.jpg.bak; cat /data/local/tmp/yt.jpg > $D/youtube-wallpaper.jpg; rm /data/local/tmp/yt.jpg"
   ```

   Write over the existing file with `cat >`, as above: a new file made by the shell can be unreadable to Home
   on newer Android. Tested on Android 9 (Xiaomi MiTV-AXSO2).
3. Home loads the picture when focus lands on the YouTube tile, so press Right then Left on the dock (or
   Home) to see it. No restart is needed.

To go back, copy `youtube-wallpaper.jpg.bak` over `youtube-wallpaper.jpg` the same way. Deleting the file
leaves the tile with the normal theme. Personal photos stay out of Git.

## Enable

Save your TMDB **API Read Access Token** in the ignored local file
`work/tmdb-token.txt`. Never put it in a theme or Android resource.

```sh
python3 tools/tmdb-config.py work/tmdb-token.txt work/tmdb-import.json
adb -s <tv-ip>:5555 push work/tmdb-import.json /sdcard/Android/data/com.gathin.tvhome/files/tmdb-import.json
```

Restart Home or reinstall using the normal installer (leave the stock launcher
state alone with `STOCK_LAUNCHER=none`). The configuration is imported into
Home's private app storage and removed from external storage. Delete the local
staging JSON after use. Existing Android 9 external storage/ADB access can read
staging credentials until import finishes. There is no token in the APK.

Without enabled private configuration, no worker or network feature starts.
The fork declares INTERNET; there is no background service, periodic polling,
new dependency, telemetry or account integration. TMDB sees your public IP and
provider queries. Your viewing history and streaming credentials are not sent.

## Cache and offline behavior

Provider listings and up to six backdrops per app are cached on disk for 24 hours.
Artwork uses TMDB's original-size file, with no dimming or gradient overlays.
Original files replace the earlier 780-pixel previews as each title is shown.
Decode sampling keeps at least the TV display resolution when the source permits;
images smaller than the screen retain all source pixels. The selected original
is the quality limit: the launcher does not invent extra image detail. Downloads
are bounded to 32 MB and image dimensions to 16,384 pixels per side.
Expired entries refresh while that provider is focused. A failed refresh can show cached data up to
seven days old; otherwise the day/night theme
remains. Response sizes, image dimensions, request timeouts and HTTPS hosts are
bounded. Switching focus invalidates pending UI results so a late response
cannot replace a different provider's background. Cache is disposable and is
cleared with Android's Clear cache. Rotation uses cached metadata and fetches
each image when first shown; it does no work while Home is in the background.

## Disable and credits

```sh
python3 tools/tmdb-config.py --disable work/tmdb-import.json
```

Push and restart in the same way. Long-press the Settings pill and select
**Movie artwork credits** for TMDB and JustWatch attribution, privacy and cache
information. The official TMDB logo is converted from
https://files.readme.io/29c6fee-blue_short.svg without changing geometry/colors.
TMDB branding remains owned by TMDB, outside the project's MIT artwork license.

Sources: [Discover movies](https://developer.themoviedb.org/reference/discover-movie),
[Discover TV](https://developer.themoviedb.org/reference/discover-tv),
[provider list](https://developer.themoviedb.org/reference/watch-providers-movie-list),
[JustWatch attribution](https://developer.themoviedb.org/reference/movie-watch-providers),
[TMDB attribution](https://developer.themoviedb.org/docs/faq).
