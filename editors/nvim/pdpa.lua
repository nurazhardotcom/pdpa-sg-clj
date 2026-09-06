// pdpa-sg-clj Neovim integration — push scan findings to the quickfix list.
// Requires: `bb` + `rg` on PATH and pdpa-sg-clj checked out somewhere.
// Install: copy to ~/.config/nvim/lua/pdpa.lua, then in init.lua:
//   require("pdpa").setup({ root = vim.fn.expand("~/pdpa-sg-clj") })
//
// Usage: :PdpaScan [path]  (defaults to the current working directory)

local M = {}

local SEVERITY_QFTYPE = { CRITICAL = "E", HIGH = "E", MEDIUM = "W", LOW = "I" }

function M.setup(opts)
  opts = opts or {}
  local root = opts.root or vim.fn.expand("~/pdpa-sg-clj")

  vim.api.nvim_create_user_command("PdpaScan", function(cmd)
    local target = cmd.args ~= "" and cmd.args or vim.fn.getcwd()
    -- quickfix format: path:line: [SEV] label (run from the bb.edn root)
    local shellcmd = "cd "
      .. vim.fn.shellescape(root)
      .. " && bb scan "
      .. vim.fn.shellescape(target)
      .. " --format quickfix"
    local out = vim.fn.systemlist(shellcmd)
    vim.fn.setqflist({}, " ", {
      title = "pdpa-sg-clj scan",
      lines = vim.tbl_map(function(line)
        local file, lnum, sev, msg = line:match("^(.-):(%d+): %[(%u+)%] (.*)$")
        if not file then
          return { text = line }
        end
        return {
          filename = file,
          lnum = tonumber(lnum),
          col = 1,
          type = SEVERITY_QFTYPE[sev] or "I",
          text = "[" .. sev .. "] " .. msg,
        }
      end, out),
    })
    vim.cmd("copen")
  end, { nargs = "?", complete = "dir", desc = "pdpa-sg-clj PII/secret scan to quickfix" })
end

return M
