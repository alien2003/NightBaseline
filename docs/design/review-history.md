# History screen review

Date: 2026-11-25. Scope: `ui/history` (History screen, calendar, controls, night preview, samples) against
`screens.md` 7.4, `states.md` 5 and 7.2, `layouts.md` 5.3, 6, 10 and 11. Method: the rendered PNGs of
`HistoryScreenshotsTest` (build/screenshots/history) were read state by state on every window. Each window is
captured at rest and then a window at a time to the end of the feed, and the frames are stitched side by side. Every
frame is also checked by assertions: no clipped or ellipsized text, no unfilled placeholder, every clickable node at
least 48 x 48 dp, and no clickable node within 12 dp of a separating fold.

## Coverage

- States: feed (`S-ALL`, `S-SOME`, `S-DEV`, `E-NODATA`, `E-SHORT`, `E-AMBIGUOUS`, `E-NOTEVAL` rows), calendar
  (current month, previous month), filter "Moved", learning (`L1`..`L6`), `H-FILTER-EMPTY`, `H-EMPTY` (also `L0`,
  `E-HC-NONE`), `H-LOADING`, `E-PERM` and `E-HC-UPDATE` banners (`E-SOURCE-GONE` keeps the rows with no banner, the
  same as the feed).
- Windows: phone (Compact), flip-cover-square (Glance), owner-book (Book, separating fold at 346 dp), tablet-land
  (Large, two panes), owner-inner (Medium), owner-tabletop (Tabletop, horizontal fold at 346 dp). Each window at font
  1.0 and 2.0, in light and dark. The feed and the calendar are also rendered in glass and in solid.

## Round 1: findings and fixes

| # | Where | Finding | Fix |
|---|---|---|---|
| 1 | `NightRow` (shared) | The 2..6 per-metric glyphs wrapped as 5 + 1 in the 72 dp column, leaving a lone dot on the second line in every row. | `FlowRow(maxItemsInEachRow = 3)`: the glyphs now sit in even rows of 3 (3 + 3 for six metrics). |
| 2 | Samples | Derived "two long sleeps" and "too few readings" nights kept the moved metrics of the night they were copied from, so the row read "Two long sleeps. Sleeping heart rate, …" and showed triangles. | Placed states get matching metrics (no data: none; too few: no value; two sleeps: not compared). |
| 3 | Filter chips | The unselected "All" chip was a 40 dp wide target (caught by the 48 dp assertion). | The chip's target box has a 48 dp minimum width as well as height. |
| 4 | Book | Feed rows end exactly on the hinge clearance line, and pixel snapping put them 0.5 px inside it. | Geometry kept (the list ends at fold - 12 dp). The fold assertion allows 1 px for pixel snapping on that line. |
| 5 | Book (346 dp half) | A 7-column month grid cannot keep 48 dp cells in a 346 dp half (7 x 48 + gutters > 334 dp). | When the pane is narrower than 7 x 48 dp, the grid is transposed: weekdays are rows and weeks are columns (at most 6). Cells keep 48 dp, and the arrow keys follow the layout. The narrow book half uses the compact gutter (16 dp). |
| 6 | Calendar | Days after today were drawn as empty cell cards. They looked like nights without data. | Future days show only a tertiary number, with no surface. They are not targets. |
| 7 | Calendar, wide panes | On tabletop and wide single panes the grid stretched to 600 dp (cells around 130 dp), which made the grid loose and hard to scan. | Grid capped at 7 x 64 dp (`layouts.md` 5.3: 48-64 dp cells) and centred. |
| 8 | Legend | "No data and Too few readings" was joined into one entry with odd capitalisation. | Two entries that share the glyph. |
| 9 | Tabletop | At 200 % font the full summary card (with body) did not fit the 334 dp top half; it scrolled and cut the body mid-line. | The top half uses a view-only compact summary (glyph, night label, headline). Body and metrics stay one tap away, in Night detail. |
| 10 | Filter-empty | The card used the dotted ring, which is the learning glyph, so "no moved nights" looked like a learning state. | The dotted ring appears only on `H-EMPTY`. The filter-empty card has text and "Show all nights" only. |
| 11 | Compact at 200 % | Title "History" and the List/Calendar toggle do not fit on one bar row: the title would have to break inside the word. | The toggle sits in the top bar only when the bar is at least 360 dp at the current font scale. Otherwise it is the first content row. |

Checked and kept: status is never carried by colour alone (each row and cell has a glyph shape and a text label; the
grayscale reading follows from the shapes); data sits on solid surfaces and only the shell is glass; sticky month
headers are solid; the floating bar never covers the last row (bottom clearance); in tabletop the top half holds no
targets.

## New string keys (to add to `states.md` 7.2 when the shared docs are next edited)

| key | en | ru |
|---|---|---|
| `history_open_night` | Open night | yes |
| `history_filter_empty_gaps` | No learning nights or gaps in this period. | yes |

Both are in `res/values*/strings_history.xml` (checked by `HistoryStringsTest`).

## Open

- Non-separating crease (owner-inner flat): `LayoutSpec` drops non-separating folds, so the filter chips can straddle
  the crease at x = 346 dp in Medium (`layouts.md` 6.3). This needs the crease in `LayoutSpec` or a `Modifier.avoidFold()`.
- Calendar glyphs stay at 16 dp at 200 % font, while the day numbers grow. They are readable, but small next to the
  numbers.
- `Alt+Up` / `Alt+Down` (previous / next item with the detail following) and restoring the scroll anchor across layout
  changes (`layouts.md` 8.2) belong to the route, which is integrated later.
- The shared `MonthCalendar` (components) has no keyboard navigation and no narrow form. `HistoryCalendar` reuses
  `CalendarCell` and could replace it.
