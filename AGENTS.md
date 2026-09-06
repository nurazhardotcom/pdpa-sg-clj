# AGENTS.md — operator notes for AI agents working on pdpa-sg-clj

Owner: Nur Azhar <dev@nurazhar.com> · https://nurazhar.com
Identity rule: all commits as `Nur Azhar <dev@nurazhar.com>`.
Get identity from evidence (`git log`, `gh api user`) — never invent emails.

## Everyday commands

```bash
bb test                          # full suite (needs `rg` on PATH)
bb audit .                       # self-audit (expect pre-existing fixtures only)
bb scan <path> --format sarif --out audit.sarif
bb export-rules --format gitleaks --out gitleaks.toml
```

## Environment warning (this box)

Container has ~2.7GB RAM and **no swap**. The scanner holds `rg --json`
output in memory; `scan` excludes `.git/node_modules/target/out/.cpcache`
for this reason — do not remove `skip-globs`. Never run `bb audit` on trees
containing vendored dependencies without excludes; verify with small scopes first.

## ⏳ PENDING — VS Code Marketplace publish (OWNER does this, not agents)

The extension scaffold at `editors/pdpa-vscode/` is complete, type-checks
clean (`npm run compile`), and is publicly visible in this repo. Publishing
is deliberately deferred. When the owner asks for it:

1. **Confirm the publisher ID first.** Scaffold says `nurazhardotcom`
   ( = GitHub login). It MUST equal a Marketplace publisher the owner owns.
   Do not change it on a guess — ask.
2. **You need a Marketplace PAT** (Azure DevOps → Personal Access Tokens →
   `Marketplace > Manage` scope). Agents cannot mint this; the owner pastes it.
3. Then:
   ```bash
   cd editors/pdpa-vscode
   npm install
   npm run package     # vsce → pdpa-sg-clj-<version>.vsix
   vsce publish        # prompts for / uses the PAT
   ```
4. Requires `bb` + `rg` on PATH at runtime (`pdpa.toolRoot` setting otherwise).
