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
- Address linting flags

### Backlog

- seems Very complex, but find a way to generate the site for any commit in any branch, not just most recent in main/cicd testing/most recent pr
- Add script to preview to change iFrame title and onscreen title + pass/fail indicator
- For orchestrator
  - add setting on manual run whether to commit anything/deploy to pages
- Address all remaining errors and warnings in pipe
- Add trigger for Build APKs that specifically builds them with no TTL (default 1 day rn) when run from a PR merge
- Increase test coverage
