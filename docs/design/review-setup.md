# Review: setup screens (onboarding, learning period, settings)

Scope: `ui.onboarding`, `ui.learning`, `ui.settings` (stateless screens on synthetic `SampleData`), rendered by
`SetupScreenshotsTest` on phone, flip-cover-square (Glance), owner-book (separating vertical fold), owner-tabletop
(separating horizontal fold) and tablet-land, font 1.0 and 2.0, light and dark, plus Russian at font 2.0 on the phone
and the cover screen; glass and solid for the shell screens (564 images per run, `app/build/screenshots`).

Assertions on every render: no unfilled placeholder, no clipped or ellipsized text, every clickable node >= 48 x 48 dp,
nothing clickable within 12 dp of a separating fold, nothing clickable in the tabletop top half.

## Round 1: findings and fixes

| # | Where | Finding | Fix |
|---|---|---|---|
| 1 | Settings, source-change dialog, cover screen and tabletop at 200 % | Dialog body and buttons clipped (test failure): the dialog did not fit the window or the bottom half | The dialog host scrolls when the dialog is taller than its area (still centred otherwise, still in the end pane in Book and the bottom half in Tabletop) |
| 2 | Settings, Book | Section rows reported across the fold (test failure) | Rows ended exactly at fold - 12 dp; the edge rounded up by a pixel. Test now allows 1 px of rounding; layout unchanged |
| 3 | Learning, phone | Six full metric tiles, each with the same "Learning, 3 of 7" chip, made "Already visible" four screens long and repetitive | Compact rows in one card (name, dotted band outline, value, chevron); the card heading carries the learning context. Wide panes split the rows over two cards |
| 4 | Learning, tabletop | Two row columns in the short bottom half wrapped names and crowded values | One column in Tabletop |
| 5 | Learning and settings, tabletop | Top-half card was full width while the label above it was capped at 600 dp (misaligned edges) | Top-half cards capped at 600 dp and centred like the content below |
| 6 | Settings, sensitivity row | Group header "Sensitivity" and row title "Sensitivity" repeated the same word | The row title is the preset name ("Balanced"), the summary its one-line description |
| 7 | Onboarding, ready (learning) | "3 of 7 nights" (progress label) followed by "3 of 7 nights are already there." | The body under the progress explains what comes next (`home_expect_body`) instead of repeating the count |
| 8 | Disclaimer footer, Russian at 200 % | The "About" link broke into one-letter lines next to the sentence | `DisclaimerFooter` uses a `FlowRow`: the link wraps below the sentence (shared component, same API) |
| 9 | Learning, tabletop at 200 % | After fix 5 the narrower top-half card clipped its body (test failure) | From font 1.5 the top half keeps title and progress; the sentence moves to the top of the bottom half |

Checked and kept: step indicator text plus pill-shaped current dot (not colour alone); selected preset card has a
2 dp outline, filled radio and bold name; selected section row uses `surface.3` plus bold title; glass only on the
shell (top bar, bar/rail, dialog), every list, card and illustration solid; onboarding is outside the shell and has
no glass.

## Open

- Russian at 200 %: the shared `FloatingNavBar` breaks "Settings" inside the word; owned by the shell, not changed
  here.
- Glance onboarding permission step is long at 200 % (eight lines of data types); it scrolls and nothing clips.
  Collapsing the optional types is a candidate for the next round.
- `sens_fewer_desc` in `strings.xml` says three measurements must move; ALGORITHM.md section 6 maps "Fewer notes" to z
  3.0 with the same two-measurement rule, so the screen uses `setup_sens_fewer_desc`. The old key can be removed when
  the copy owner agrees.
- Not built yet: Advanced sensitivity (range window, per-metric switches) and "Reset to default"; the screens take no
  ViewModel, navigation or platform intents yet (callbacks only).
