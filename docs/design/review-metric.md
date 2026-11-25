# Review: Metric detail

Date: 2026-11-25. Scope: `ui/metric` (Metric detail screen, `screens.md` 7.3, `layouts.md` 5.2, `states.md` 4, 5, 7.1
and 8) and the shared `TrendChart` (`ui/components/Chart.kt`). Method: `MetricScreenshotsTest` renders every
`MetricSample` on `phone` and the key states on `flip-cover-square`, `owner-book`, `owner-tabletop` and `tablet-land`,
in light and dark, at font 1.0 and 2.0. Glass and solid are both rendered for the default state and the explainer.
Grayscale renders and 7 / 30 / 90 period renders run on the phone. Each render checks for unfilled placeholders,
clipped or ellipsized text, clickable nodes under 48 x 48 dp, and (tabletop) clickable nodes above the fold. I read
the PNGs by eye after each round.

## Round 1: findings and fixes

| # | Where | Finding | Fix |
|---|---|---|---|
| 1 | TrendChart | The newest point at the right edge was clipped. Points sat at slot centres with no room for the marker (selected: 1.4x triangle plus halo), and the top value could touch the plot top. | The plot keeps a fixed `MARKER_EXTENT` (11 dp) inset on both sides, at the top and above the date axis, so every marker stays inside the canvas, including the enlarged selected one. |
| 2 | TrendChart | Y-axis ticks were uneven and unround (57 / 52 / 46). They were placed at fractions of a padded data range. | `niceScale`: steps of 1, 2, 2.5 or 5 x 10^k, at most 5 ticks. Sleep duration uses quarter, half or whole hours (labels "7.5 h", `chart_axis_hours`). The domain runs from the first tick to the last tick. Covered by `NiceScaleTest`. |
| 3 | TrendChart | The band was drawn as one rectangle per night with a 1 px overlap and separate edge lines, which could leave anti-aliased seams and doubled edges at steps. | Each contiguous run of nights with a range is now one filled path. Its top and bottom edges are stepped polylines with mitred joins. Runs break only at nights without a range. |
| 4 | TrendChart | The axis width (36 dp) and date-axis height (20 dp) were fixed, so tick labels could overflow at font 2.0. | Both are measured from the label text. Date labels are placed at the canvas bottom. |
| 5 | TrendChart | The tooltip was centred on the selected night, so it covered the newest point at the right edge (tabletop render). | The tooltip sits beside the guide line, on the side with more room, using the same slot geometry as the drawing. |
| 6 | Chart summary | A learning series said "learning, 0 of 7". | The summary counts the series' nights with a value. |
| 7 | Period selector | At font 2.0 in a narrow pane (book start pane), the last segment was squeezed to 40 dp wide and about 900 px tall. | New `fill` option: the segments share the width equally and their labels wrap ("90 / nights"). Single-column, book and tabletop layouts use it. |
| 8 | Tabletop | Prev/next chevrons next to the back chevron read as the same control. A "not recorded" state card with an action rendered above the fold. The period note clipped in the top half. | Back, the period selector and info share one row below the fold. "Previous night" and "Next night" are labelled tonal buttons. The top half shows a view-only empty frame, and the state card moves below the fold. Notes appear only below the fold. |
| 9 | Medium / tablet | When nothing was compared (learning), the header sat beside an empty column. | Without stats or a problem card, the header takes the column alone. |
| 10 | Stats vs header | The stats row printed the range with default precision ("45–58 bpm") while the header said "45.2–57.8 bpm". | `StatsRow(decimals)` lets the header's precision carry through. |
| 11 | Screenshot stitching | The floating bar and rail repeated on every slice, and a hairline seam showed under the top bar. | Slices are taken between the top bar (plus 2 dp) and the floating bar, and to the right of the rail. Both shell parts appear once. |
| 12 | Band-changed sample | The header said "Usual" while the last chart point was higher. | The sample takes the header state from the last point. |

## Open

- Double tap on a chart night (open Night detail) is not wired: `TrendChart` has no double-tap callback yet. `MetricDetailActions.onOpenNight` is in place for integration.
- `chart_summary_outside` counts against all nights in the period, gaps included ("1 of 30"), while the stats row counts compared nights ("28 of 29"). Both are correct but read slightly differently. This needs a copy decision, not a layout one.
- The side line uses the metric display name mid-sentence ("Only higher values of Sleeping heart rate count"). This is the same pattern as the existing `detail_side_ignored`. Changing the casing needs per-language name forms.
- Book at font 2.0: the chart moves below the visible part of the start pane, so the end pane's text summary is seen first. The start pane scrolls. A denser header variant for book at large font could keep the chart in view.
- CompactShort uses the Compact column (the selector is not moved into the top bar yet), and WideShort is implemented but has no rendered device in this matrix.
