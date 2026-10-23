# India release calendar (personal fork)

Open the calendar icon beside the sun button on Home. Select **+ Add** in any
column, search TMDB by movie/show title, choose the correct result (type/year
are shown), review the India release information and select **Save**.

The title and a supported India subscription app are filled automatically.
Supported services are Netflix, Prime Video, JioHotstar, SonyLIV, ZEE5 and Sun NXT.
An installed matching app is preferred. Availability is from JustWatch via TMDB
and describes the current subscription listing, not a promise about a future
release or your particular plan. Unsupported or missing listings remain
**India subscription app unconfirmed**; rental/purchase listings are excluded.

Only TMDB's country **IN**, release type **Digital** can populate a movie's
release date. If several dates exist, the next upcoming date is used, otherwise
the most recent past date. Dates are displayed on their actual weekday with
the full date on each card. Entries do not repeat weekly. A digital date is
country-specific and not necessarily the date the title joins the selected app.

TMDB's TV episode air dates do not confirm India streaming availability. They
are never substituted for India release dates. TMDB also does not reliably
confirm exact streaming clock times; **Time unannounced** stays explicit and
midnight/date-only timestamps are not converted into guessed IST launch times.
No foreign broadcast timetable is used.

Titles without an India date are saved under **Unannounced dates** at the top.
Select a saved title for **Open app**, **Refresh release info**, or **Remove**.
Refresh checks TMDB when requested; no background polling occurs. Saving the
same TMDB title/type again updates it rather than making a duplicate. Removal
asks for confirmation. **Open app** opens the app, not the exact title or playback.

Search needs Home's existing enabled private TMDB configuration and internet
access. Queries and selected title IDs go to TMDB; the token stays in private
storage and HTTPS headers. Search results are limited to films and TV series,
with adult content excluded. Requests are bounded and cancelled UI results
cannot replace a newer search or reopen panels after leaving the calendar.
Failures preserve saved entries. No new permission or dependency is added.

The calendar saves locally and survives app updates/restarts. Clearing Home's
app data removes it. Earlier manual viewing slots are retained in the undated
list, rather than relabelled as verified releases. Left/Right changes columns;
Up/Down moves within one column. Back or **‹ Home** returns to Home.

Metadata: TMDB. Availability: JustWatch. Full TMDB notice and logo remain in
Home Settings (hold OK) → **Movie artwork credits**.

API sources: [search](https://developer.themoviedb.org/reference/search-multi),
[movie release types](https://developer.themoviedb.org/reference/movie-release-dates),
[TV details](https://developer.themoviedb.org/reference/tv-series-details),
[India provider availability](https://developer.themoviedb.org/reference/tv-series-watch-providers).
