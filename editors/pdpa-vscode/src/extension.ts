import * as cp from "child_process";
import * as path from "path";
import * as vscode from "vscode";

interface SarifResult {
  ruleId?: string;
  level?: string;
  message?: { text?: string };
  locations?: Array<{
    physicalLocation?: {
      artifactLocation?: { uri?: string };
      region?: { startLine?: number };
    };
  }>;
}

interface SarifLog {
  runs?: Array<{
    results?: SarifResult[];
  }>;
}

const SEVERITY: Record<string, vscode.DiagnosticSeverity> = {
  error: vscode.DiagnosticSeverity.Error,
  warning: vscode.DiagnosticSeverity.Warning,
  note: vscode.DiagnosticSeverity.Information,
  none: vscode.DiagnosticSeverity.Hint,
};

function findToolRoot(): string | undefined {
  const configured = vscode.workspace
    .getConfiguration("pdpa")
    .get<string>("toolRoot");
  if (configured) {
    return configured;
  }
  const folders = vscode.workspace.workspaceFolders ?? [];
  for (const folder of folders) {
    const candidate = path.join(folder.uri.fsPath, "pdpa-sg-clj");
    try {
      require("fs").statSync(path.join(candidate, "bb.edn"));
      return candidate;
    } catch {
      /* not here — keep looking */
    }
  }
  // Fall back to the first workspace folder (bb on PATH + BB_EDN path use).
  return folders[0]?.uri.fsPath;
}

function runScan(
  toolRoot: string,
  target: string
): Promise<SarifLog> {
  return new Promise((resolve, reject) => {
    const child = cp.spawn(
      "bb",
      ["scan", target, "--format", "sarif"],
      { cwd: toolRoot, shell: false }
    );
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (d) => (stdout += d));
    child.stderr.on("data", (d) => (stderr += d));
    child.on("error", reject);
    child.on("close", (code) => {
      if (!stdout.trim()) {
        reject(new Error(`pdpa scan produced no output (exit ${code}): ${stderr}`));
        return;
      }
      try {
        resolve(JSON.parse(stdout) as SarifLog);
      } catch (err) {
        reject(new Error(`Could not parse pdpa SARIF output: ${err}`));
      }
    });
  });
}

export function activate(context: vscode.ExtensionContext): void {
  const diagnostics =
    vscode.languages.createDiagnosticCollection("pdpa-sg-clj");
  context.subscriptions.push(diagnostics);

  const status = vscode.window.createStatusBarItem(
    vscode.StatusBarAlignment.Right,
    100
  );
  status.command = "pdpa.scanWorkspace";
  context.subscriptions.push(status);

  async function scanWorkspace(): Promise<void> {
    const toolRoot = findToolRoot();
    const folders = vscode.workspace.workspaceFolders;
    if (!toolRoot || !folders) {
      void vscode.window.showWarningMessage(
        "PDPA: no workspace folder (or pdpa-sg-clj checkout) found."
      );
      return;
    }
    diagnostics.clear();
    let total = 0;
    for (const folder of folders) {
      let log: SarifLog;
      try {
        log = await runScan(toolRoot, folder.uri.fsPath);
      } catch (err) {
        void vscode.window.showErrorMessage(
          `PDPA scan failed: ${err instanceof Error ? err.message : err}`
        );
        return;
      }
      const byFile = new Map<string, vscode.Diagnostic[]>();
      for (const result of log.runs?.[0]?.results ?? []) {
        const loc = result.locations?.[0]?.physicalLocation;
        const uri = loc?.artifactLocation?.uri;
        const line = (loc?.region?.startLine ?? 1) - 1;
        if (!uri) {
          continue;
        }
        const filePath = path.isAbsolute(uri)
          ? uri
          : path.join(folder.uri.fsPath, uri);
        const range = new vscode.Range(line, 0, line, Number.MAX_SAFE_INTEGER);
        const message = `${result.ruleId ?? "pdpa"}: ${
          result.message?.text ?? ""
        }`;
        const diag = new vscode.Diagnostic(
          range,
          message,
          SEVERITY[result.level ?? "note"] ??
            vscode.DiagnosticSeverity.Information
        );
        diag.source = "pdpa-sg-clj";
        const list = byFile.get(filePath) ?? [];
        list.push(diag);
        byFile.set(filePath, list);
        total += 1;
      }
      for (const [filePath, diags] of byFile) {
        diagnostics.set(vscode.Uri.file(filePath), diags);
      }
    }
    status.text = total === 0 ? "$(shield) PDPA: clean" : `$(alert) PDPA: ${total}`;
    status.tooltip = "Singapore PDPA PII/secret findings in this workspace";
    status.show();
  }

  context.subscriptions.push(
    vscode.commands.registerCommand("pdpa.scanWorkspace", () => void scanWorkspace())
  );

  if (
    vscode.workspace
      .getConfiguration("pdpa")
      .get<boolean>("scanOnSave", true)
  ) {
    context.subscriptions.push(
      vscode.workspace.onDidSaveTextDocument(() => void scanWorkspace())
    );
  }

  void scanWorkspace();
}

export function deactivate(): void {
  /* nothing to clean up */
}
