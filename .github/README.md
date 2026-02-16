# Mesh CI

---

## Pipeline Flow

The CI pipeline behaves as follows:

1. run Prettier on web-related filetypes, as well as XML
   a. Do not use prettier-plugin-kotlin, it is not maintained and errors regularly
2. On push, run super-linter. For all possible cases, run autofix (this covers Kotlin)

- Needs updating

---

## General TODO

### Ongoing

- For all workflows, add current build summary as commit/pr comments
- Alter system to use GitHub Pages instead of github HTML preview. This will fix CSS on previews
  - Add script to change iFrame title and onscreen title + pass/fail indicator
- Add orchestrator workflow for all reporting tasks
  - determine how to allow multiple reusable workflows to share build cache
  - add link to super-linter actions output
  - onsider whether to delete reports files after use
  - add setting on manual run whether to commit anything/deploy to pages
- replace build-artifacts setup gradle with coverage setup gradle
- Prevent any non-main commits from showing in the Pages preview, but build in a system to view the webpage from any commit iteration and branch.

### Backlog

- Integrate with Dokka
- Test auto UML diagramming
- Break workflows out into multiple intelligently-grouped jobs for improved execution visibility
- Add graceful error handling if specific preview reports cannot be generated
- Add graceful error handling to note that coverage reporting can only be generated on PRs and pushes, not workflow dispatches
- Research if it's possible to ask to run workflows on any push