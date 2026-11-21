# CLAUDE.md

@AGENTS.md

## Específico de Claude Code

- Subagentes del equipo en `.claude/agents/`: `sincronizador` (Haiku), `planificador` (Opus), `orquestador` (Sonnet), `implementador` (Sonnet).
- Implementadores en paralelo: cada uno en su propio worktree (`isolation: worktree`) y su propia rama.
- `.claude/settings.json` desactiva la atribución: no agregues `Co-Authored-By` ni "Generated with" a mano.
- Config personal: `.claude/settings.local.json` y `CLAUDE.local.md` (ignorados por git).
