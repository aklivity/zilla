# Zilla — Claude Code Guide

@AGENTS.md

## Routing & session hygiene

- For risky multi-file refactors, architecture questions, or anything where planning matters more than typing: suggest I enable plan mode (or `/model opusplan`) before implementing.
- Delegate codebase searches and file discovery to the explore subagent — don't read files into the main session unless you'll edit them.
- For repetitive mechanical work across many files (renames, format conversions, boilerplate), spawn a subagent rather than doing it in the main session.
- For genuinely simple tasks (single-file edit, lookup, syntax question), proceed directly — don't over-route.
- When I switch to an unrelated task in the same session, suggest `/clear` first.
