# pdpa-sg-clj — VS Code extension

Real-time Singapore PDPA PII + secret diagnostics, powered by
`bb scan --format sarif` from this repo. Findings render inline as
errors (critical/high), warnings (medium), and info (low).

## Develop

```bash
cd editors/pdpa-vscode
npm install
npm run compile     # type-check + emit to out/
```

Press `F5` in VS Code to launch an Extension Development Host.

## Publish (maintainers)

```bash
npm run package     # builds pdpa-sg-clj-<version>.vsix via vsce
vsce publish        # needs a Marketplace publisher PAT (publisher: nurazhardotcom)
```

Set `pdpa.toolRoot` if your `pdpa-sg-clj` checkout is not
`<workspace>/pdpa-sg-clj`. Requires `bb` + `rg` on `PATH`.
