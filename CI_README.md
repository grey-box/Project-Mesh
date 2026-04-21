The CI pipeline behaves as follows:

1. run Prettier on web-related filetypes, as well as XML
   a. Do not use prettier-plugin-kotlin, it is not maintained and errors regularly
2. On push, run super-linter. For all possible cases, run autofix (this covers Kotlin)
