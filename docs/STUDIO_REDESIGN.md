# NOVA Studio: whole-workspace redesign

Historical design note. Superseded by [NOVA Prism](PRISM_DESIGN.md), which follows the user's subsequent request for a more expressive neon interface and richer interactions.

## Design read

A document research application for Vietnamese students, with a calm, artistic, contemporary visual language. The latest brief asks for a memorable whole page, creative structure, satisfying effects and a gentle first impression. Soft light and mineral colors supersede the previous high-contrast neon direction. Preserve NOVA, actual documents, API routes, upload, questions, citations and personalization.

## Audit

The previous design has two heavy dark rectangles, a dominant illustration, repeated small prompt buttons, and a tall left sidebar that requires scrolling even when empty. The dialogue, library and source viewer need equal visual care. Reorganize the working space rather than repainting its hero.

## Plan and critique

```text
NOVA                    Ask / Library                Motion / Profile
+--------------------------------------------------+--------------------+
| Ask documents                         Connection | Your documents     |
|                                                  | Compact uploader   |
| Big typographic welcome       Paper sculpture    | PDF read mode      |
|                                                  | Analyze            |
| Tall main prompt | Wide summary prompt           |                    |
|                  | Concepts | Conclusion         | Library + counts   |
|                                                  | Open / remove file |
| Question label                                   |                    |
| Spacious input                           Send    | Session note       |
+--------------------------------------------------+--------------------+
```

The art alone cannot carry the request. The whole layout uses related paper surfaces, folded corners, varied suggestion proportions, quiet mineral colors and deliberate typography. The composer becomes its own writing surface. Library files open directly into a reading drawer. Avoid a marketing landing page, invented document previews or controls without behavior. Mobile shows the upload area first, then dialogue; the library can collapse.

Applied skills: design-taste-frontend for the welcome composition and redesign audit; frontend-design for the visual identity, copy and hierarchy; ui-ux-pro-max for application structure, feedback, touch and accessibility. The design-system searches returned a Brutalism style unsuitable for this calm application, so those style recommendations are not persisted; use a bespoke palette and the skill's verified accessibility and interaction rules as fallback. Existing HTML/CSS/JS and self-hosted Manrope remain the implementation foundation.

DESIGN_VARIANCE 8: asymmetric suggestions and right-side document desk. MOTION_INTENSITY 6: one gentle paper motion, coordinated entry, pointer-responsive material light and direct interaction feedback. VISUAL_DENSITY 4: comfortable reading, compact tools.

## Tokens

- Mist background: #eef4f3
- Paper surface: #fbfcfb
- Deep water text: #183b43
- Slate secondary text: #526b74
- Teal action: #276b69
- Lavender wash: #e7e5f3

Manrope 400/600/800; headings 40-60px, body 16px, controls 14px, metadata 12px. Rounded work surfaces 24px, input 18px, controls 12px. Assistant replies use readable line lengths. Source highlights have actual text and a visible location label.

Motion: a single load reveal; gentle artwork movement; light follows fine pointers within interactive surfaces; buttons respond to press; the source reader appears as a drawer. Reduced motion and the saved motion toggle disable decoration, including pointer movement. No flashing, scroll hijacking, cursor replacement, animation library or CDN. Guidance: [web.dev motion accessibility](https://web.dev/learn/accessibility/motion).

## Artwork

Built-in imagegen was used. Workspace output: backend/src/main/resources/static/images/paper-studio.webp. The original generated PNG remains in the Codex generated_images directory. The older neon asset remains available in Git history and the project; the redesigned page references only the new asset.

Final prompt:

Use case: stylized-concept. Create a bespoke sculptural artwork for NOVA Studio, a calm document research web app. A single continuous ribbon of fine translucent paper and frosted glass folds into a flowing open-book fan, with five layered page-like planes, a tangibly beautiful sculptural object rather than a UI. Soft mineral palette: pale ice blue, sea-glass teal, pearlescent ivory, restrained powder lavender. Matte pearl paper faces and thin luminous glass edges, delicate natural refraction, a small soft cyan light glows from inside the folds. Sophisticated cinematic 3D product photography, diffuse daylight and a faint iridescent reflection, inviting and artistic. Landscape 3:2 composition; whole object clearly visible with breathing room, positioned in the center-right; smooth light mist background matching #eef4f3, soft contact shadow. No words, letters, logos, interface, grid, orbit, holographic HUD, dark background or watermark. Artwork only, not a webpage screenshot.

## Verification, 9 October 2026

- Node frontend tests: 9 passed, including direct full-document opening from the library.
- Maven tests: 35 passed; the opt-in live Gemini evaluation was skipped. No failures or errors.
- Maven package completed successfully. No backend code or dependencies changed.
- Browser checks: 1440x900, 1280x720, 1024x600, 768x1024, 390x844, 320x740 and 844x390. No horizontal overflow, overlapping sidebar blocks or clipped empty-state suggestions. At short desktop heights the welcome scrolls with the page; conversation and populated library scroll within their working regions.
- Checked real frontend interactions against a local synthetic API fixture: question suggestions and send; direct document opening; citation highlighting and scroll; mobile source reading; Escape and focus return; navigation and expandable library; saved motion preference; personalization dialog; missing AI configuration; 15 long file names. The fixture does not evaluate Gemini answer accuracy.
- Primary contrast ratios: ink/background 10.81:1; secondary/background 5.08:1; CTA 6.19:1; prompt metadata/lavender 4.55:1. Focus rings and 44px primary touch controls are retained. Motion off pauses the artwork and disables surface light; reduced-motion CSS also removes transitions.
- Asset is a self-hosted 1200x800 WebP, 53,790 bytes, with declared dimensions. Only the above-the-fold art and two critical font weights receive preload priority. No added runtime library or external font/image request.
- Pre-flight reviewed against the three skills. Marketing-only checks such as testimonial logos, marquees, eight-section diversity and GSAP pinning do not apply to this document application. Preserve the existing brand mark and upload-first keyboard workflow; the navigation and skip link provide direct access to the question area. The current design deliberately uses a consistent light theme for the requested gentle palette.
