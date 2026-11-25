# Review: Home and Night detail (stage 4)

Screens: `ui.home.HomeScreen` (Home, "Last night") and `ui.night.NightDetailScreen` (Night detail). Both are
stateless (`HomeState` / `NightDetailState` plus action holders); synthetic states come from `HomeSamples` and
`NightDetailSamples`. Rendered by `HomeScreenshotsTest` (build/screenshots, `home-*` and `night-*`): key states x
light/dark x font 1.0/2.0 x phone, flip-cover-square, owner-book, tablet-land, owner-tabletop, glass plus the solid
fallback for `S-DEV`; every state of `states.md` on phone and flip-cover-square; grayscale on the phone. Each render
asserts no unfilled placeholder, no clipped text, 48 dp targets, no duplicated control and no control within a
separating fold +- 12 dp.

## Round 1: findings and fixes

| # | Finding (from the rendered PNGs or the assertions) | Fix |
|---|---|---|
| 1 | The night label appeared twice on Home (top bar subtitle and summary card) and on Night detail (title and card); under "Last result" the heading and the card repeated it too. | Home shows the subtitle only above learning and state cards; `NightSummaryCard(showLabel = false)` on Night detail and below "Last result" (the label stays in the TalkBack text). |
| 2 | Stitched screenshots of two-pane layouts repeated the detail pane and the rail. | The test stitches only the horizontal extent of the scrolled pane and fills the rest with the window background. |
| 3 | Tabletop: the summary card ran wider than the strip under it, and the "What this means" action sat alone at the start edge while the tiles below spanned two columns. | Top-half blocks share the 600 dp text width; below the fold the banner, actions, "Last result" and tiles share the grid width. Root cause: `fillMaxWidth().widthIn(max)` never caps; the screens now pass `widthIn` first. |
| 4 | Tiles of one grid row had different heights (ragged rhythm on Medium, tablet, tabletop). | Tiles of a row take the tallest height. `IntrinsicSize.Min` was tried and clipped wrapped text (caught by the clipping assertion); a two-pass `SubcomposeLayout` leaked a second copy of every tile into the semantics tree (caught by the new duplicate-control assertion). The final version reports heights after layout and applies the tallest as a minimum. |
| 5 | Sample copy said "at least 0% is needed" and "Only 9 readings; at least 3 are needed". | The samples set the thresholds those sentences quote (20 readings, 60 %). |
| 6 | The summary card's text action was indented by the button's inner padding. | It is pulled back by that padding so its label lines up with the body text. |
| 7 | The glance screens ignored the hoisted scroll state, so they could not be stitched or restored. | `GlanceHome` takes the scroll state; the test fails if a screen does not use it. |

Checked and kept: the stable metric order; grayscale renders still tell within, higher, lower, learning and missing
apart (glyph plus label); 200 % text wraps (top-bar titles take two lines on Night detail) with no clipping; there is
no red and no exclamation mark.

## Open items

- `HistoryStrip` always titles itself "Last 7 nights", even while learning shows only the 1 to 6 counted nights. It
  needs a "Last {n} nights" plural in the shared component.
- In Large (tablet-land, 1280 dp) the labelled rail leaves too little width for the 320 dp "Recent nights" pane next
  to a 360 dp detail pane, so the supporting pane is not shown there. It appears only from about 1400 dp. `layouts.md`
  5.1 needs to change, or the list pane needs to narrow.
- Shared components put `widthIn(max)` after the caller's `fillMaxWidth()` (`NbCard`, `NightSummaryCard`,
  `InlineBanner`, `MetricRangeTile`), so their 600 dp cap has no effect unless the caller caps first.
- The clipping assertion cannot see text inside `clearAndSetSemantics` nodes (tiles, summary). Those texts have no
  height limit or line limit, so they wrap; the screenshots are the check for now.
- `StringResourcesTest` reads only `strings.xml`. The new `strings_home.xml` keys match by hand (en and ru), but
  nothing checks them automatically yet.
- In Tabletop, the top half scrolls when 200 % text does not fit above the fold. It holds no controls, but
  `layouts.md` 6.4 says only the bottom half scrolls.
