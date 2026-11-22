# FARCAST UI design notes

## Subject
A field-intelligence console: opening a case is opening a dossier that
accumulates evidence from live telemetry and passive recon, scored by an
autonomous pipeline. Audience is a researcher comfortable with data
density — not a consumer social app, not a SaaS dashboard.

## Tokens
- Background `#0E1116` · Panel `#161B22` · Panel-raised `#1D232C`
- Amber (primary/actionable) `#E8A33D`
- Cyan (live telemetry ONLY — never decorative) `#4FB8C4`
- Brick (flagged/blocked) `#C2473D`
- Text `#E4E7EB` · Text-muted `#8A93A0` · Hairline `#2A313C`
- Type: JetBrains Mono (data — coordinates, hashes, case IDs, scores),
  IBM Plex Sans (chrome, body, navigation). Both open-source.
- Layout: left icon rail (branches) + bordered manifest/log main content,
  hairline dividers, no rounded-card grid. Numbered sequence used exactly
  once, for DarkMixer's real pipeline stages.

## Principles
1. Color is functional, mapped 1:1 to `LeadCategory`/`LeadStatus` — never
   decorative. If a color doesn't answer "what does this mean," cut it.
2. The case manifest is the home screen. No marketing-style landing page
   with feature cards — the job is opening a case, so that's screen one.
3. Density is a choice, not an oversight. Generous whitespace would be
   wrong for this audience; legibility (contrast, <80-char text columns)
   still isn't optional.
4. Live vs. static is always visually distinguishable at a glance (cyan
   pulse/indicator for telemetry currently streaming vs. static recon data).

## Self-critique log (update every pass)
- Pass 1 (this one): rejected the near-black+single-neon-accent default —
  two accents, each with one specific functional meaning, background has a
  blue undertone rather than true black. Rejected rounded SaaS cards in
  favor of bordered manifest rows, which also reads more like an actual
  case file / report than a dashboard of widgets.
- Open question for pass 2: the branch rail's icon set needs real icons,
  not text placeholders — revisit once an icon source is picked (Lucide,
  used elsewhere in this stack per the Visualizer's own library list, is
  the obvious default rather than introducing a second icon set).
