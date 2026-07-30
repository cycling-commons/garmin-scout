# Scout — store asset generation prompts

AI-image/video-tool prompts used while producing the store hero banner, app
logo/icon, and promo video, kept here so they can be re-run or tweaked later
without reconstructing the reasoning from chat history.

---

## Hero banner (1440x720px)

For tools that accept multiple image inputs (Midjourney image-prompting,
GPT-image-1/DALL·E with references, Firefly, etc.). Attach in this order:

- **Image 1** = Cycling Commons logo
- **Image 2** = Scout app screenshot (raw UI)
- **Image 3** = the framed Garmin Edge device mockup

```
Create a clean, modern app-store hero banner, exactly 1440x720px (2:1 landscape).

SUBJECT: Use image [3] (the framed Garmin Edge device, showing the Scout tile
grid — DANGER/CLOSURE/SURFACE/RESUPPLY/SCENERY/OTHER — and the "3 cars" radar
readout) as the hero product shot. Place it left-of-center, occupying roughly
60% of the frame height, angled in soft 3D perspective (10-15° tilt) as if
mounted on a bike stem, with gentle studio lighting and a soft drop shadow.
Keep the on-screen UI text and layout from image [3] crisp and undistorted —
do not redraw or reinterpret the screen content, just render the device
naturally in the scene.

If image [2] (raw app screenshot) offers a sharper or more legible version of
the on-screen UI than image [3], use it to refine the device's screen content
while keeping image [3]'s device framing/angle.

BACKGROUND: a softly blurred (shallow depth of field, bokeh) outdoor touring
scene — an open gravel or rural road at golden-hour light. Muted, warm, natural
tones (dusty greens, ambers, road browns). No visible riders or faces needed —
keep it atmospheric, not busy, so the device stays the clear focal point.

LOGO: place image [1] (the Cycling Commons logo) small, in the bottom-right
corner, roughly 10% of the canvas width, with clear padding around it — a
subtle brand mark, not a competing focal point. Add a soft translucent light
backing behind it only if needed for contrast against the background.

CONSTRAINTS: no added text, taglines, or badges — the store renders its own
title/description elsewhere. Sharp focus on the device, soft-focus background.
Deliver as a single flat image, exactly 1440x720px, optimized to stay under
2048KB as PNG or JPG.

MOOD: adventurous, practical, trustworthy — built for real riders, not a
flashy or gamer aesthetic.
```

**Known issue**: AI image tools reliably mangle small reference text when
redrawing a device screen (e.g. produced "DANDER"/"SUBFACE" instead of
"DANGER"/"SURFACE" on one pass). Always proofread the on-screen tile labels
in the output before using it — if wrong, composite the real screenshot in
manually (Affinity Photo) rather than relying on the AI's redraw.

---

## App icon / logo (SVG)

Evolves the existing flat pin (`resources/drawables/launcher_icon.png`)
rather than starting from scratch.

```
Design a flat, vector logo/icon for "Scout," a Garmin cycling app that lets
riders tap their bike computer screen to mark hazards, closures, road surface
changes, and rest stops on the fly, mid-ride.

FORMAT: SVG, square 1:1 canvas, generous safe-margin padding (at least 12% of
canvas width on all sides — this doubles as a Connect IQ device launcher icon,
shown as small as ~40x40px, so it must stay legible and uncropped at tiny
sizes). Transparent background.

CONCEPT: evolve a map pin/location marker — the app's current mark — into
something that also reads as "tap to record." Options to explore:
  (a) a map pin with a small concentric "tap ripple" radiating from its base
      or tip, implying a touch/tap action at that point
  (b) a map pin where the pin's head is subtly formed from or overlaid with a
      single fingertip/tap-circle shape
  (c) a map pin merged with a simple radar-sweep arc (echoing the app's
      optional vehicle-radar feature) — one clean arc, not a literal radar
      dish

STYLE: flat, geometric, bold single silhouette — no gradients, no
photorealism, no fine detail or text. Must read clearly as a small
monochrome silhouette (test: does it still work at 1 color, tiny size?).

COLOR: primary color a warm red-orange (#D1421F, matching the app's existing
"Danger" tile and current pin icon) on transparent background. If a second
color is used for the tap/ripple accent, keep it a single flat neutral
(white or dark grey) — no more than 2 colors total.

MOOD: purposeful and quick, not playful — this is a safety/utility tool used
while riding, not a game icon.

DELIVERABLE: clean SVG markup, optimized (no unnecessary nested groups or
excess anchor points), so it scales cleanly from a tiny device icon up to a
large hero/marketing size without redrawing.
```

Note: Connect IQ's manifest (`launcherIcon="@Drawables.LauncherIcon"`) needs a
rasterized PNG, not SVG — export/rasterize before dropping a result into
`resources/drawables/`.

---

## Hero banner text (if adding a wordmark/tagline overlay)

- **"Scout" wordmark font**: match the Cycling Commons lettering (bold,
  rounded, friendly geometric sans) so it reads as the same family — try
  **Poppins ExtraBold** or **Quicksand Bold** (Google Fonts). Set in caps,
  scaled similarly to how "GARMIN" sits on the device bezel.
- **Tagline**: reuse the established one from
  [store-listing.md](store-listing.md) for consistency across the hero,
  store subtitle, and social share text:
  > Tag road conditions from the saddle — straight into your ride's FIT file.

  Shorter alternatives if space is tight:
  - "Tag the ride. Build the map." (ties directly to the CC Atlas)
  - "One tap. Every road remembered."
  - "See it. Tap it. Ride on."

---

## Promo video (ends on the hero image)

Written for tools like Runway, Pika, Luma, or Sora-style video generators.
Attach the final hero banner image as a visual/continuity reference — the
video's closing shot dissolves into it as a static end card.

```
Create a short (15-20 second) promotional video for "Scout," a Garmin cycling
app, in the same visual world as the attached reference image (a cyclist
pausing at a stone fountain in a sunlit forest, golden-hour light, shallow
depth of field, warm natural tones).

SEQUENCE:
1. (0-4s) Wide shot: a cyclist rides along a dappled forest gravel path,
   golden-hour backlight through the trees, unhurried pace.
2. (4-7s) The cyclist slows and stops near a stone drinking fountain — the
   same fountain and setting as the reference image — dismounting to refill
   a water bottle.
3. (7-11s) Cut to a close-up over-the-shoulder shot: a gloved hand reaches
   to a Garmin Edge bike computer mounted on the handlebars, and taps a
   tile on its screen (the Scout app's coloured tile grid — DANGER,
   CLOSURE, SURFACE, RESUPPLY, SCENERY, OTHER). The tap flashes briefly on
   the tile to confirm it landed.
4. (11-14s) Quick insert: the device's bottom strip showing a live vehicle
   count ticking up ("3 cars, 25 mph") as a car passes on a nearby road —
   implying the radar feature working in the background.
5. (14-17s) The cyclist remounts and rides on, receding down the sunlit
   path.
6. (17-20s) Smooth cross-dissolve from the receding rider into a hard hold
   on the final hero image (attached) — the same scene, same framing, now
   static, with the Scout pin-and-wordmark logo, "Tag the ride. Build the
   map." tagline, and Cycling Commons logo visible. Hold for 2-3 seconds
   as the closing card.

STYLE: naturalistic, handheld-but-steady camera movement, warm golden-hour
grading throughout, shallow depth of field matching the reference image —
no CGI gloss, no fast cuts, no on-screen text until the final hero-image
card (the image itself already carries the branding).

AUDIO (if supported): ambient forest/birdsong, soft bike freewheel clicks,
a single subtle tap/confirmation tone synced to the screen-tap moment — no
music track needed, or a light, unobtrusive acoustic bed if one is added.

ASPECT RATIO: 16:9 landscape, matching the hero image's framing.
```

### 5-second cut (bumper/teaser length)

Too short for the multi-beat sequence above — at this length, AI video
generators do best with one continuous shot plus a hard cut to the end card,
not several quick cuts. Same reference image as above.

```
Create a 5-second promotional bumper video for "Scout," a Garmin cycling app,
in the same visual world as the attached reference image (golden-hour forest
light, shallow depth of field, warm natural tones).

SEQUENCE:
1. (0-3s) One continuous close-up shot: a gloved hand taps a tile on a
   Garmin Edge bike computer's screen (the Scout app's coloured tile grid).
   The tile flashes briefly to confirm the tap landed. Background softly
   blurred — dappled sunlit forest, out of focus.
2. (3-5s) Hard cut to a 2-second hold on the final hero image (attached) —
   the cyclist-at-the-fountain scene with the Scout pin-and-wordmark logo,
   "Tag the ride. Build the map." tagline, and Cycling Commons logo visible,
   as the closing card.

STYLE: naturalistic, steady close-up camera, warm golden-hour grading, no
extra cuts beyond the one hard cut to the end card, no on-screen text until
the hero-image card itself.

AUDIO (if supported): a single crisp tap/confirmation tone synced to the
screen-tap, ambient forest sound underneath — no music needed.

ASPECT RATIO: 16:9 landscape, matching the hero image's framing.
```
