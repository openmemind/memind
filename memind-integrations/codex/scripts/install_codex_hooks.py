#!/usr/bin/env python3

import argparse
import json
import re
from pathlib import Path

OWNER_MARKER = "memind-integrations/codex"
OWNER_MARKERS = (OWNER_MARKER, "memind/codex")
PLACEHOLDER = "${CODEX_PLUGIN_ROOT}"
CODEX_HOOKS_RE = re.compile(r"^codex_hooks\s*=\s*true(?:\s*(?:#.*)?)?$")


def _default_hooks_path():
    return Path.home() / ".codex" / "hooks.json"


def _default_config_path():
    return Path.home() / ".codex" / "config.toml"


def _read_hooks(path):
    path = Path(path)
    if not path.exists():
        return {"hooks": {}}
    try:
        data = json.loads(path.read_text())
    except json.JSONDecodeError:
        raise ValueError(f"invalid JSON in {path}")
    data.setdefault("hooks", {})
    return data


def _write_json_atomic(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
    temp.replace(path)


def _write_text_atomic(path, text):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_text(text)
    temp.replace(path)


def _replace_placeholder(value, plugin_root):
    if isinstance(value, str):
        return value.replace(PLACEHOLDER, str(plugin_root))
    if isinstance(value, list):
        return [_replace_placeholder(item, plugin_root) for item in value]
    if isinstance(value, dict):
        return {key: _replace_placeholder(item, plugin_root) for key, item in value.items()}
    return value


def _is_memind_group(group):
    for hook in group.get("hooks", []):
        command = str(hook.get("command", ""))
        if any(marker in command for marker in OWNER_MARKERS):
            return True
    return False


def _remove_memind_entries(data):
    hooks = data.setdefault("hooks", {})
    for event in list(hooks):
        groups = []
        for group in hooks.get(event, []):
            if _is_memind_group(group):
                remaining_hooks = [
                    hook
                    for hook in group.get("hooks", [])
                    if not any(marker in str(hook.get("command", "")) for marker in OWNER_MARKERS)
                ]
                if remaining_hooks:
                    cleaned = dict(group)
                    cleaned["hooks"] = remaining_hooks
                    groups.append(cleaned)
            else:
                groups.append(group)
        if groups:
            hooks[event] = groups
        else:
            hooks.pop(event, None)
    return data


def _load_template(plugin_root):
    template_path = Path(plugin_root) / "hooks" / "hooks.json"
    return _replace_placeholder(json.loads(template_path.read_text()), Path(plugin_root).resolve())


def _feature_flag_message(config_path):
    config_path = Path(config_path)
    if config_path.exists() and _codex_hooks_enabled(config_path.read_text()):
        return ""
    return f"Reminder: enable Codex hooks by adding `[features]` with `codex_hooks = true` to {config_path}."


def _codex_hooks_enabled(config_text):
    in_features = False
    for raw_line in config_text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            in_features = line == "[features]"
            continue
        if in_features and CODEX_HOOKS_RE.match(line):
            return True
    return False


def _enable_codex_hooks(config_path):
    config_path = Path(config_path)
    config_text = config_path.read_text() if config_path.exists() else ""
    if _codex_hooks_enabled(config_text):
        return False

    lines = config_text.splitlines(keepends=True)
    for index, raw_line in enumerate(lines):
        if raw_line.strip() == "[features]":
            lines.insert(index + 1, "codex_hooks = true\n")
            _write_text_atomic(config_path, "".join(lines))
            return True

    if not config_text:
        _write_text_atomic(config_path, "[features]\ncodex_hooks = true\n")
        return True
    suffix = "" if config_text.endswith("\n") else "\n"
    _write_text_atomic(config_path, f"{config_text}{suffix}\n[features]\ncodex_hooks = true\n")
    return True


def install_hooks(plugin_root, hooks_path=None, codex_config_path=None, enable_feature_flag=True):
    hooks_path = Path(hooks_path or _default_hooks_path())
    codex_config_path = Path(codex_config_path or _default_config_path())
    existing = _remove_memind_entries(_read_hooks(hooks_path))
    template = _load_template(plugin_root)
    for event, groups in template.get("hooks", {}).items():
        existing["hooks"].setdefault(event, []).extend(groups)
    _write_json_atomic(hooks_path, existing)
    message = f"Installed Memind Codex hooks into {hooks_path}."
    if enable_feature_flag and _enable_codex_hooks(codex_config_path):
        return f"{message}\nEnabled Codex hooks feature flag in {codex_config_path}."
    if not enable_feature_flag:
        reminder = _feature_flag_message(codex_config_path)
        return f"{message}\n{reminder}" if reminder else message
    return message


def uninstall_hooks(hooks_path=None):
    hooks_path = Path(hooks_path or _default_hooks_path())
    data = _remove_memind_entries(_read_hooks(hooks_path))
    _write_json_atomic(hooks_path, data)
    return f"Removed Memind Codex hooks from {hooks_path}."


def main():
    parser = argparse.ArgumentParser(description="Install Memind hooks for Codex CLI.")
    parser.add_argument("--uninstall", action="store_true", help="Remove Memind Codex hooks.")
    parser.add_argument("--hooks-path", default=None, help="Override Codex hooks.json path.")
    parser.add_argument("--config-path", default=None, help="Override Codex config.toml path.")
    parser.add_argument(
        "--no-enable-feature-flag",
        action="store_true",
        help="Do not modify Codex config.toml; print a reminder instead.",
    )
    args = parser.parse_args()

    plugin_root = Path(__file__).resolve().parents[1]
    if args.uninstall:
        print(uninstall_hooks(args.hooks_path))
    else:
        print(install_hooks(plugin_root, args.hooks_path, args.config_path, not args.no_enable_feature_flag))


if __name__ == "__main__":
    main()
