# Documentation

Every document of the repository, grouped by reader.

## For people who use the app

| Document | What it covers |
|---|---|
| [../README.md](../README.md) | What NightBaseline does, screenshots, install, tested sources, privacy summary |
| [../README.ru.md](../README.ru.md) | The same in Russian |
| [user-guide.md](user-guide.md) | First run, every screen and message, learning period, settings, notifications, foldables, FAQ, troubleshooting |
| [privacy.md](privacy.md) | Privacy policy: what is read and why, what is stored, deletion, contact |
| [../ALGORITHM.md](../ALGORITHM.md) | How a night becomes "within" or "moved outside your usual range": every rule, parameter and the validation |
| [../CHANGELOG.md](../CHANGELOG.md) | Changes per release |
| [../SECURITY.md](../SECURITY.md) | Reporting a vulnerability, signing key policy, verifying a download |

## For contributors

| Document | What it covers |
|---|---|
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | Bug reports, proposals, pull requests, commit style, what not to commit |
| [development.md](development.md) | Setup with mise, build, tests, modules, screenshots, dataset tools, phone access, CI, releases, signing secrets |
| [DATASET.md](DATASET.md) | The developer's Health Connect export pipeline and its formats (data stays in `.data/`) |
| [STATUS.md](STATUS.md) | Current project state and next steps |
| [../CLAUDE.md](../CLAUDE.md) | Project rules for automated agents working in this repository |

## Decisions

| Document | Decision |
|---|---|
| [adr/0001-architecture-and-reuse.md](adr/0001-architecture-and-reuse.md) | Module layering, pluggable metrics, what is reused from PulseStages |
| [adr/0002-project-language-and-history.md](adr/0002-project-language-and-history.md) | English-only repository |
| [adr/0003-ui-library-and-blur.md](adr/0003-ui-library-and-blur.md) | Own design layer on Compose, Haze for blur |

## Design

| Document | What it covers |
|---|---|
| [design/README.md](design/README.md) | Index and reading order of the design documents |
| [design/principles.md](design/principles.md) | Design principles |
| [design/screens.md](design/screens.md) | Screen map, navigation, components, every screen |
| [design/states.md](design/states.md) | Every state, its copy and the notification spec |
| [design/layouts.md](design/layouts.md) | Layouts per window size, posture and input |
| [design/tokens.md](design/tokens.md) | Colour, type, shape, material and motion tokens; contrast pairs |
| [design/reference-analysis.md](design/reference-analysis.md) | Measurements from system apps (references only) |
| [design/review.md](design/review.md) | Design review log; per-screen reviews: [home](design/review-home.md), [metric](design/review-metric.md), [history](design/review-history.md), [setup](design/review-setup.md) |

## Research

Aggregates only; no personal values.

| Document | What it covers |
|---|---|
| [research/metrics-matrix.md](research/metrics-matrix.md) | What the device data supports per metric: sampling, coverage, spreads, publication delay |
| [research/analogs.md](research/analogs.md) | How Apple, Samsung, Garmin, Withings, Oura and Fitbit build nightly baselines |
| [research/health-connect-docs.md](research/health-connect-docs.md) | Health Connect facts that matter for NightBaseline |
| [research/large-screens.md](research/large-screens.md) | Large screens, foldables and flip cover screens |
| [research/design-coloros17.md](research/design-coloros17.md) | ColorOS 17 visual style and Compose UI libraries |
| [research/pulsestages-reuse.md](research/pulsestages-reuse.md) | What can be reused from PulseStages |
| [research/ohealth-cloud.md](research/ohealth-cloud.md) | OHealth's cloud storage and official data copies |
