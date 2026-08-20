READ-ONLY analysis of the Auxio Android music player's navigation and Activity structure. Do NOT modify any file. Write your report to stdout as Markdown.

Repo root is the working directory. Kotlin source at app/src/main/java/org/oxycblt/auxio/. Key files to start from: MainActivity.kt, MainFragment.kt, Auxio.kt, AuxioService.kt, and the ui/ package. Navigation graphs will be XML under app/src/main/res/navigation/.

ACCURACY RULE: every class, file, id, and method you name must be one you actually opened and read. Cite path:LINE for every claim. If something is not there, write NOT FOUND and say where you looked. Never invent plausible-sounding names — this report will be used to write code.

Determine the following:

A. ACTIVITY STRUCTURE. Which Activity hosts the UI? Its exact name, file:line, and its entry in AndroidManifest.xml (cite the manifest line). What does its onCreate do, step by step? Is it a single-Activity architecture? What theme does it use, and does it call enableEdgeToEdge or equivalent?

B. NAVIGATION GRAPH. List every navigation XML file under res/navigation/ with its full path. For each: the start destination, and every destination with its android:id and the fragment class it points to. Present as a table.

C. FRAGMENT INVENTORY. Every Fragment class in the app, with file:line and a one-line description of the screen it renders. Note which are BottomSheet or dialog fragments as opposed to full screens.

D. THE NOW-PLAYING / BOTTOM SHEET MECHANISM. Auxio uses a bottom-sheet style now-playing panel. Identify exactly how it is implemented — custom CoordinatorLayout behavior, a library, or something else. Give the classes and layout XML files involved with paths. Describe how the collapsed and expanded states are managed.

E. BACK HANDLING. How is system back handled? Find every OnBackPressedCallback, onBackPressed override, or back-related dispatcher registration, with file:line. Describe the order of precedence between them.

F. LAYOUT INVENTORY. The layout XML files for the main Activity and the now-playing screen, with full paths. For the now-playing layout, list its root view group type and its direct children with their android:id values. We intend to insert a ComposeView into this layout later, so be precise about its structure.

G. THE CUT LINE. We are going to replace this entire navigation and Fragment layer with a single Compose-hosting Activity, while keeping playback and music indexing untouched. List every file that would need to be removed, replaced, or bypassed to do that. Separate them into: (1) pure UI/navigation, safe to cut, (2) files that mix UI with playback or indexing logic and therefore need care, (3) files that must be kept. Be specific about anything in category 2 — that is the risky set.

Output dense factual Markdown, sections A-G, tables where they fit, no preamble or advice. End with a section "Uncertain / Not Found".
