# NOVA Prism

## Design read and audit

A Vietnamese document research app, redesigned as a futuristic, expressive workspace with visible, satisfying effects throughout. The user rejected the quiet pastel Studio version. Its repeated soft surfaces, low visual energy and barely visible motion did not meet the request. Preserve NOVA and every real upload/chat/document/source behavior.

The new direction uses an open midnight canvas, a compact navigation dock, luminous document tools and an expansive conversation stage. This is a bespoke glass-and-light aesthetic, implemented with native CSS, canvas and JavaScript. Manrope and the existing Tabler icons remain self-hosted. No framework migration or animation dependency is needed.

Applied skills: frontend-design for subject-specific identity; design-taste-frontend for the welcome audit/composition, with product UI handled separately; ui-ux-pro-max for controls, accessibility, themes, feedback and responsive behavior. The broad design-system result matched academic/portfolio styles and did not fit this brief. The single narrower style query `glassmorphism dark` returned the verified Glassmorphism style, suitable for app navigation and overlays. Adapt its layered light and solid-surface accessibility guidance.

DESIGN_VARIANCE 9: open stage, narrow dock and asymmetric question lenses. MOTION_INTENSITY 9: coordinated entry, ambient light, pointer parallax, tilt, press ripples, loading scans and reading drawer transitions. VISUAL_DENSITY 4: generous dialogue, compact document controls.

## Plan

```text
NOVA / Hỏi đáp tài liệu              Theme / Motion / Personalization
+------+--------------------------------------+--------------------+
| Ask  | Conversation                 Status  | Your documents     |
| Docs |                                      | Luminous uploader  |
| Add  | Editorial title    Glass book prism   | PDF read option    |
|      |                 pointer parallax     | Analyze / scan     |
|      | Wide question   | Narrow question    |                    |
|      | Narrow question | Wide question      | Actual file list   |
|      |                                      |                    |
|      | Floating writing surface      Send   | Session note       |
+------+--------------------------------------+--------------------+
```

Critique: merely changing the pastel palette back to dark would repeat the rejected design. The new layout therefore removes the enclosing chat card, moves navigation into a functional dock, makes the welcome artwork responsive to input, and gives upload, suggestions, writing, real library files and citations different material treatments. Motion responds to real actions; no invented documents, fake progress percentage or decorative status data.

Mobile presents the conversation welcome first and exposes a direct upload jump; document tools follow below. The dock becomes a compact horizontal navigation. Reading remains legible and naturally scrollable.

## Tokens and effects

- Midnight: #091321
- Solid reading surface: #111e31
- Primary text: #eef5ff
- Secondary text: #a6b7cd
- Cyan: #83e4ee
- Periwinkle: #b4b7ff
- Apricot: #edc4aa, a small material reflection rather than a competing action color.

Manrope 400/600/800. Desktop welcome headings 40-64px; body 16px; primary controls 14px; most metadata 12px. A separate light theme maps the same semantic tokens. Text sits on sufficiently opaque surfaces. Calculated contrast on solid reading surfaces: secondary text 8.20:1 dark / 5.84:1 light; primary action text 11.09:1 dark / 7.19:1 light. Decorative gradients stay outside body copy.

Effects: canvas light trails with a frame budget; one background aurora system; pointer parallax in the book; perspective on question lenses; delegated pointer highlights; native button press ripples; upload halo and actual busy scan; input focus beam; source and document entry; reading drawer; toast transition. Motion preferences, reduced motion and hidden tabs stop decoration. Pointer effects apply only to fine pointers. No scroll hijacking or cursor replacement.

## Artwork

Built-in imagegen, stylized-concept followed by background-extraction. Final project asset: `backend/src/main/resources/static/images/knowledge-prism-cutout.webp`, 1200x800, 120,776 bytes with actual alpha transparency. The same sculpture is used in both themes with theme-specific color treatment. It is self-hosted and dimensioned. Generated PNG masters remain outside the repository; Pillow only resizes and encodes the final WebP.

Final prompt:

Use case: stylized-concept. Create a bespoke cinematic 3D artwork for NOVA, a document intelligence workspace. One sculptural open book made of ultra-thin iridescent optical glass: two sweeping translucent sheets twist gracefully upward and inward into an almost closed infinity arch, like knowledge unfolding from paper into light. This is a single elegantly curved object, NOT a fan or stack of rectangular screens. Rich midnight navy background #091321. Fine luminous cyan glass edges, pearlescent inner surfaces, controlled periwinkle reflections, a tiny warm apricot reflection at the base. Beautiful physically based refraction, soft volumetric light spilling from inside the arch onto its base, refined and sculptural, high-end 3D product art. Landscape 3:2 composition, entire sculpture visible with breathing room, centered, a low camera angle with generous empty dark background fading to #091321 at every outer edge. No UI, no text, no letters, no logos, no orbit lines, no HUD, no grids, no watermark. Artwork only.

Background extraction prompt:

Use case: background-extraction. Remove the navy background and reflective floor from this image, leaving only the complete iridescent optical-glass open-book sculpture as a transparent cutout. Preserve exactly the sculpture's curved two-sheet infinity arch, shape, proportions, luminous cyan edges, periwinkle and apricot refractions and original product lighting. Keep its entire outline visible, including both wing-like base sheets and central arch. Actual transparent alpha background; no backdrop color, no floor, no rectangular dark haze, no text, no added object. Do not redesign the sculpture.

## Verification

- Node: 13 frontend checks pass, including four new tests of animation lifecycle and theme restoration.
- Java: 35 tests pass; the opt-in live Gemini evaluation is skipped. No backend behavior or dependency changes.
- Final Maven application packaging succeeds after the frontend resources are updated.
- Browser: checked 1440x900, 1280x720, 1024x600, 768x1024, 390x844, 375x812, 320x740 and 844x390. Final 320px layout has no horizontally clipped controls or overflowing prompt text. Empty laptop welcome and document desk fit; short desktop windows use page scrolling.
- Both themes, keyboard focus, prompt-to-composer, answer citations, highlighted source scrolling, full-document opening, personalization and persisted motion pause checked in the browser. Canvas and CSS artwork stop when decoration is switched off; lifecycle tests verify hidden-tab cancellation and a single resumed rendering loop.
- Browser content used a local synthetic API fixture to isolate layout and interaction checks; it does not establish live Gemini answer accuracy. No fixture data or server is shipped.
- Fonts, icons, artwork and scripts are served by Spring; no external runtime assets, animation dependency or separate frontend server.
