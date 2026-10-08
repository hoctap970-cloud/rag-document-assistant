# NOVA: document research workspace

## Brief and audit

Students need to ask questions about PDF and Word documents. The user requested a distinctive futuristic interface with plenty of neon light, using the three installed frontend skills. Preserve the NOVA wordmark, existing routes, form IDs, upload and chat behavior, citations, and personal workspace settings.

The unfinished design used an atom diagram, decorative numbered labels, four repeated cards, tiny metadata, and an unrelated italic headline. Replace those with artwork about documents, a clear two-line headline, compact question suggestions, and readable controls. Keep source reading quiet.

Applied skills:

- `design-taste-frontend`: audit and visual composition of the welcome screen. Marketing-page requirements do not apply to the working chat, upload form, or source reader.
- `frontend-design`: subject-specific direction, restrained typography, plain Vietnamese copy, and coherent tokens.
- `ui-ux-pro-max`: forms, keyboard focus, contrast, touch targets, responsive layout, and motion preferences. The verified search was `AI document assistant futuristic`, matching the AI product family. Adapt its functional typography and accessibility guidance to the explicit dark neon preference.

Design dials: variance 8 (bespoke document artwork, asymmetric welcome composition), motion 5 (one slow artwork movement, small transitions), density 5 (compact document management, spacious reading).

## Plan

```text
NOVA / Document intelligence                    Motion    Your space
+-------------------------+----------------------------------------+
| Your documents          | Ask your documents             Status  |
| Choose / drop file      |                                        |
| PDF deep reading        | Large headline     Glass-page artwork  |
| Analyze                 | Short explanation                      |
|                         | Suggested questions                    |
| Library + clear         |                                        |
| Document list           | Visible question label                 |
| Session note            | Question input                  Send   |
+-------------------------+----------------------------------------+
```

Critique: artwork may compete with reading, so confine it to the welcome state. Concentrate neon on artwork, primary controls, and focus. Use one font family, with no gradient or italic headline emphasis. Mobile uses document flow and an expandable library so users can collapse it when reading.

## Tokens and interaction

- Background `#0b1018`; panel `#121b27`; elevated panel `#1b2938`.
- Text `#e7eef6`; secondary text `#9fafc2`; accent `#71edff`.
- Self-hosted Manrope: 400 body, 600 controls, 800 headline. Body 16px, secondary copy 13-14px, small metadata 12px.
- Radii: large panels 16px, controls 8px; circles only for avatars and status dots.
- Desktop columns scroll independently; sidebar children never shrink into each other. Dialogs cap their height and scroll the reading area.
- Primary controls are at least 44px. Focus rings remain visible. Long excerpts and URLs wrap. Loading and errors retain text feedback.
- Decorative movement can be disabled by the checkbox and by `prefers-reduced-motion`. Loading still communicates state without animation.

## Local assets

- `static/images/document-light.webp`: 1200 x 800, optimized from the generated original. Decorative welcome artwork, not a representation of actual document content.
- `static/icons/*.svg`: selected outline icons from [Tabler Icons](https://github.com/tabler/tabler-icons), with MIT license in `static/icons/LICENSE.txt`. No runtime package or external CDN.
- Existing Manrope files and OFL license remain local.

### Image generation prompt

Use case: stylized-concept. Asset type: unique hero artwork for NOVA, a Vietnamese document question-answering research application. Subject: a sculptural fan of five thin translucent glass sheets suggesting document pages, with subtle finely etched text-like lines but absolutely no readable text or interface. The sheets curve into a single luminous ribbon of knowledge. Polished cinematic 3D product render, tangibly refractive glass edges, bright electric cyan edge light, a restrained violet reflection on far edges, dark graphite background matching #0b1018. Composition: landscape 3:2, sculptural sheets occupy the right two-thirds with the bottom-right brightest; left third is mostly smooth dark negative space. Deliberate asymmetry, dramatic perspective, photoreal materials, crisp edges, soft volumetric light, sophisticated futuristic mood. No atom or orbital diagram, no HUD, no circles, no grids, no badge, no label, no logo, no word, no watermark. This is artwork only, not a webpage screenshot.

## Verification (2026-10-08)

- Node UI regressions: 8 passed, including concurrent upload/chat, failed chat recovery, IME composition, and PDF deep-reading selection.
- Maven: 36 tests, 35 passed, 1 live Gemini test skipped; no failures or errors. Package build succeeded.
- Browser: inspected empty welcome at 1440x900, 1280x720, 1024x600, 768x1024, 390x844, and 320x740. No page horizontal overflow; no sidebar sections intersect; desktop composer remains inside the viewport. Mobile uses page scrolling and its library starts collapsed.
- Synthetic UI data: selected a DOCX through the real file chooser; confirmed analysis enabled and PDF deep reading disabled. Checked 15 long filenames with reading warnings, question suggestion and send state, chat reply, source dialog selecting chunk 8, Escape dismissal, personalization, missing-key notice, and saved motion preference.
- Manual browser checks use synthetic local data. They do not claim to evaluate live Gemini answer accuracy.
