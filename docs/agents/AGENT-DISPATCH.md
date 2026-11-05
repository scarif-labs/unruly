# Agent Dispatch — Verified Capabilities and Recipes

Conclusions here were established by **running the tools**, not by reading their docs. Re-verify if the machine changes.

Verified: 2026-08-05

---

## Can Claude spawn these agents? — YES, both

| Agent | Binary | Version | Auth | Headless verified |
|-------|--------|---------|------|-------------------|
| codex | `~/.local/bin/codex` | `codex-cli 0.146.0` | ✅ `~/.codex/auth.json` | ✅ returned `CODEX_OK` |
| cline | `~/.nvm/versions/node/v22.22.3/bin/cline` | `3.0.49` | ✅ provider `cline` | ✅ returned `CLINE_OK` |

Mechanism: they are **CLIs invoked through the `Bash` tool**, not entries in Claude Code's
`Agent` tool. The `Agent` tool only offers Anthropic-model subagents
(`claude`, `Explore`, `general-purpose`, `Plan`, …). So dispatch = `Bash(run_in_background: true)`.

Practical consequence: an external agent's output is a **file on disk**, not a structured
result object. Every dispatch must redirect to a log under `docs/agents/logs/` and be
reviewed by Claude before its work is accepted.

---

## codex — `gpt-5.6-luna`, reasoning `high`

Already the machine default in `~/.codex/config.toml`:

```toml
model = "gpt-5.6-luna"
model_reasoning_effort = "high"
```

**Read-only / analysis** (Phase 1 mapping work):

```bash
codex exec --skip-git-repo-check "<prompt>"
```

**Writing code** — the default sandbox is `read-only`; it must be widened or the agent
silently cannot edit:

```bash
codex exec --sandbox workspace-write --cd /Users/0x1379/Work/unruly/repo "<prompt>"
```

### Papercuts
- **`--skip-git-repo-check` is required** while the target dir is not a git repo. `/Users/0x1379/Work/unruly` is not one; `repo/` (the Auxio clone) is.
- **Default sandbox is `read-only`.** A code-writing prompt without `--sandbox workspace-write` produces a confident description of edits it never made. This is the single most expensive failure mode — always pass it.
- Approval mode reports `never` in headless exec, so it will not stop to ask.

---

## cline — `deepseek/deepseek-v4-flash`

```bash
cline --json --auto-approve true -c /Users/0x1379/Work/unruly/repo --thinking high "<prompt>"
```

### Papercuts

- **🔥 `-m` MUTATES PERSISTENT STATE. This one bit us.**
  `cline -m <model>` does not scope the model to that one run — it **writes the value into
  `~/.cline/data/settings/providers.json` and it becomes the new default for every
  subsequent run.**

  Sequence of events: probing whether `deepseek/deepseek-v4` existed
  (`cline -m deepseek/deepseek-v4 "say OK"`) failed with `model not found` *and silently
  persisted the invalid id*. A Phase 1 agent dispatched later with **no `-m` flag at all**
  then died instantly with `model not found`, because the poisoned default was still in
  effect. The failure surfaced far from its cause, which is what made it expensive.

  Repair — patch only the `model` key, never rewrite the file wholesale (it holds live
  WorkOS auth tokens):

  ```bash
  python3 -c "
  import json,shutil
  p='/Users/0x1379/.cline/data/settings/providers.json'
  shutil.copy(p,p+'.bak')
  d=json.load(open(p))
  d['providers']['cline']['settings']['model']='deepseek/deepseek-v4-flash'
  json.dump(d,open(p,'w'),indent=2)"
  ```

  **Rules going forward:** never probe model availability with `-m` on the live config;
  always pass `-m deepseek/deepseek-v4-flash` explicitly on every dispatch rather than
  trusting the default; use `--data-dir` for an isolated config when experimenting.

- **`deepseek/deepseek-v4` does not exist** on this account — only the **Flash** variant
  (`deepseek/deepseek-v4-flash`) is provisioned.

- **Transient `session not found` failures.** One dispatch died on iteration 1 with
  `error: session not found: <id>`, exit code 1, having written nothing. Not a code or
  prompt problem — a straight re-dispatch of the identical prompt succeeded. **Always check
  whether any files were actually created before assuming the task was attempted**, and
  re-dispatch rather than debugging the prompt.
  - Specs: 1,048,576-token context, 393,216 max output, caps `tools · reasoning · structured_output · temperature · prompt-cache`, $0.14/$0.28 per Mtok, released 2026-04-24.
- **Prompts must be a single quoted argument.** Unquoted multi-word prompts are parsed as a subcommand and rejected (`Unknown command or unquoted prompt`).
- `--json` emits **NDJSON**, one event per line. The last line is `run_result`, carrying `finishReason`, `usage`, and final `text` — parse that line, ignore the rest.
- `--worktree` auto-creates a detached git worktree under `~/.cline/worktrees/`. Useful for parallel skin work in Phases 4–5, but only once `repo/` is a git repo with commits.
- `--timeout` defaults to `0` (no timeout). Long-running dispatches need an explicit value or they can hang indefinitely.

---

## 🔥 Dispatch mechanics — `nohup &` gives you NO completion notification

**The mistake:** agents were launched as

```bash
nohup codex exec ... > log 2>&1 &      # WRONG
```

`nohup … &` detaches the process, so the `Bash` tool call returns **immediately** and the
harness marks that call complete. The agent then runs for minutes with **nothing tracking
it**. All three Phase 1 agents finished and sat idle for ~10 minutes before anyone noticed;
the user had to ask for an update. Silence looked identical to "still working".

Only `Bash(run_in_background: true)` is harness-tracked and fires a completion
notification.

**Correct patterns:**

```bash
# Preferred — harness-tracked, notifies on completion
Bash(run_in_background: true, command: "codex exec --cd <dir> \"$(cat prompt.md)\" > log 2>&1")
```

For agents already detached, arm a waiter that exits when the process dies — one
notification, no polling:

```bash
Bash(run_in_background: true, command:
  "while pgrep -qf 'codex exec'; do sleep 10; done; echo 'agents done'")
```

**Rule: never dispatch an agent without a tracked wake-up.** Either launch it with
`run_in_background: true`, or arm a waiter immediately after. Polling by hand between
unrelated turns is not a substitute — it only works if something else happens to prompt it.

## Division of labour

| Work type | Agent | Why |
|-----------|-------|-----|
| Codebase mapping, architecture, contract design (Ph 1, 3) | **codex / luna-high** | High reasoning effort; correctness matters more than cost here |
| Bulk mechanical implementation, boilerplate, repetitive skin screens (Ph 4, 5) | **cline / deepseek-v4-flash** | 1M context ingests `ARCHITECTURE-NOTES.md` + full contract cheaply |
| Toolchain, Gradle/AGP/NDK diagnosis, verification, integration | **Claude (direct)** | Buildspec explicitly warns LLMs diagnose these badly and suggest confidently wrong fixes |

Per the buildspec's own guidance: paste `ARCHITECTURE-NOTES.md` into every prompt touching
Auxio internals, paste the skin contract into every skin prompt, pin Media3/Compose/Kotlin
versions in prompts, and scope each dispatch to **one sub-phase**.
