# Lessons

## Writing style for docs, images and UI copy (2026-10-13)

Correction: no em dashes and no AI-sounding text in the README, images or other text.

- No em dashes (—). Use a colon, a comma, parentheses or a new sentence.
- Plain statements with "is" and "has". No slogans ("your data stays yours"), no marketing verbs
  (boasts, offers, seamless, powerful), no "not just X but Y".
- No emoji as headings or bullet markers, no bold labels at the start of every list item,
  sentence-case headings, few badges.
- Every claim must match the code. Check the behaviour before describing it (e.g. which
  offline changes are actually queued).
- Reference used: https://en.wikipedia.org/wiki/Wikipedia:Signs_of_AI_writing

## Generic class names can collide with global CSS

- A button with `class="collapse"` was laid out but invisible: a stylesheet outside `src`
  sets `.collapse { visibility: collapse }`. Use specific names (`past-toggle`), and when
  something takes space but does not show, check `getComputedStyle` before anything else.
