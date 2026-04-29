#!/usr/bin/env bash

set -euo pipefail

RAW_BASE="${MEMIND_CODEX_INSTALL_BASE_URL:-https://raw.githubusercontent.com/openmemind/memind/main/memind-integrations/codex}"
INSTALL_ROOT="${HOME}/.memind/codex"
HOOKS_PATH="${HOME}/.codex/hooks.json"
CONFIG_PATH="${HOME}/.codex/config.toml"
SOURCE_ROOT=""
UNINSTALL=false
DRY_RUN=false

usage() {
  cat <<'EOF'
Usage: bash install.sh [options]

Options:
  --source-root PATH   Install from a local memind-integrations/codex directory.
  --install-root PATH  Override install directory. Default: ~/.memind/codex
  --hooks-path PATH    Override Codex hooks path. Default: ~/.codex/hooks.json
  --config-path PATH   Override Codex config path. Default: ~/.codex/config.toml
  --dry-run            Show planned file changes without writing anything.
  --uninstall          Remove Memind Codex hook entries.
  --help               Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source-root)
      SOURCE_ROOT="$2"
      shift 2
      ;;
    --install-root)
      INSTALL_ROOT="$2"
      shift 2
      ;;
    --hooks-path)
      HOOKS_PATH="$2"
      shift 2
      ;;
    --config-path)
      CONFIG_PATH="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --uninstall)
      UNINSTALL=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if ! command -v python3 >/dev/null 2>&1; then
  echo "error: python3 is required to install Memind Codex hooks" >&2
  exit 1
fi

if [[ "${DRY_RUN}" == true ]]; then
  echo "Dry run: no files will be written."
  if [[ "${UNINSTALL}" == true ]]; then
    echo "Would remove Memind hook entries from: ${HOOKS_PATH}"
  else
    echo "Would install Memind Codex files to: ${INSTALL_ROOT}"
    echo "Would merge Memind hook entries into: ${HOOKS_PATH}"
    echo "Would ensure [features] codex_hooks = true in: ${CONFIG_PATH}"
  fi
  exit 0
fi

script_dir() {
  local source_path="${BASH_SOURCE[0]}"
  if [[ -n "${source_path}" && -f "${source_path}" ]]; then
    cd "$(dirname "${source_path}")" && pwd
  fi
}

resolve_source_root() {
  if [[ -n "${SOURCE_ROOT}" ]]; then
    return
  fi
  local candidate
  candidate="$(script_dir || true)"
  if [[ -n "${candidate}" && -f "${candidate}/scripts/install_codex_hooks.py" && -f "${candidate}/hooks/hooks.json" ]]; then
    SOURCE_ROOT="${candidate}"
  fi
}

copy_local_install() {
  local source_root="$1"
  mkdir -p "${INSTALL_ROOT}"
  rm -rf "${INSTALL_ROOT}/scripts" "${INSTALL_ROOT}/hooks" "${INSTALL_ROOT}/.codex-plugin"
  mkdir -p "${INSTALL_ROOT}/.codex-plugin"
  cp -R "${source_root}/scripts" "${INSTALL_ROOT}/scripts"
  cp -R "${source_root}/hooks" "${INSTALL_ROOT}/hooks"
  cp "${source_root}/.codex-plugin/plugin.json" "${INSTALL_ROOT}/.codex-plugin/plugin.json"
  cp "${source_root}/settings.json" "${INSTALL_ROOT}/settings.json"
  cp "${source_root}/README.md" "${INSTALL_ROOT}/README.md"
  cp "${source_root}/install.sh" "${INSTALL_ROOT}/install.sh"
}

download_file() {
  local url="$1"
  local dest="$2"
  mkdir -p "$(dirname "${dest}")"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "${url}" -o "${dest}"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "${url}" -O "${dest}"
  else
    echo "error: curl or wget is required for remote installation" >&2
    exit 1
  fi
}

download_remote_install() {
  local files=(
    "README.md"
    "install.sh"
    "settings.json"
    ".codex-plugin/plugin.json"
    "hooks/hooks.json"
    "scripts/install_codex_hooks.py"
    "scripts/ingest.py"
    "scripts/retrieve.py"
    "scripts/session_start.py"
    "scripts/lib/__init__.py"
    "scripts/lib/client.py"
    "scripts/lib/config.py"
    "scripts/lib/content.py"
    "scripts/lib/identity.py"
    "scripts/lib/logging_utils.py"
    "scripts/lib/retry.py"
    "scripts/lib/state.py"
  )
  mkdir -p "${INSTALL_ROOT}"
  rm -rf "${INSTALL_ROOT}/scripts" "${INSTALL_ROOT}/hooks" "${INSTALL_ROOT}/.codex-plugin"
  for file in "${files[@]}"; do
    download_file "${RAW_BASE}/${file}" "${INSTALL_ROOT}/${file}"
  done
}

install_files() {
  resolve_source_root
  if [[ -n "${SOURCE_ROOT}" ]]; then
    if [[ ! -f "${SOURCE_ROOT}/scripts/install_codex_hooks.py" || ! -f "${SOURCE_ROOT}/hooks/hooks.json" ]]; then
      echo "error: --source-root must point to memind-integrations/codex" >&2
      exit 1
    fi
    copy_local_install "${SOURCE_ROOT}"
  else
    download_remote_install
  fi
}

run_hook_installer() {
  python3 "${INSTALL_ROOT}/scripts/install_codex_hooks.py" \
    --hooks-path "${HOOKS_PATH}" \
    --config-path "${CONFIG_PATH}" \
    "$@"
}

if [[ "${UNINSTALL}" == true ]]; then
  if [[ ! -f "${INSTALL_ROOT}/scripts/install_codex_hooks.py" ]]; then
    install_files
  fi
  run_hook_installer --uninstall
  echo "Uninstall complete. Memind state and user config under ~/.memind are preserved."
  exit 0
fi

install_files
run_hook_installer

echo
echo "Installation complete."
echo "Installed scripts: ${INSTALL_ROOT}/scripts"
echo "Codex hooks: ${HOOKS_PATH}"
echo "Codex config: ${CONFIG_PATH}"
echo "Start a new Codex session to activate Memind memory."
