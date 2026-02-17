# Mesh CI

---

## NOTES

- To run the orchestrator to test on branches outside of main, run ```gh workflow run reporting_orchestrator.yml  --ref feature/ras-cicd```, replacing "feature/ras-cicd" whichever branch you wish to run the workflow against.
- Super-Linter summary can only be added on pushes and PRs, not manual runs. Super-Linter is still running, you just need to dig into the workflow details to view useful results.

---

## Pipeline Flow

The CI pipeline behaves as follows:

1. run Prettier on web-related filetypes, as well as XML
   a. Do not use prettier-plugin-kotlin, it is not maintained and errors regularly
2. On push, run super-linter. For all possible cases, run autofix (this covers Kotlin)

- This Section Needs updating

---

## General TODO

### Ongoing

- For all workflows, add current build summary as commit/pr comments
- Add script to preview to change iFrame title and onscreen title + pass/fail indicator
- For orchestrator
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
- Alter pages commit process to commit build_reports to gh-pages and not main, so it stays off main and avoids double-commits and extra fetches

### Integration
- add dokka
- add uml to previewer