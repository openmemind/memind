#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.install_codex_hooks import install_hooks, uninstall_hooks

ROOT = Path(__file__).resolve().parents[1]


def _repo_root():
    return ROOT.parents[1]


def _installer_env(extra_pythonpath=None, python_bin_dir=None):
    env = dict(os.environ)
    paths = []
    if extra_pythonpath:
        paths.append(str(extra_pythonpath))
    paths.append(str(_repo_root() / "memind-clients" / "python" / "src"))
    existing = env.get("PYTHONPATH")
    if existing:
        paths.append(existing)
    env["PYTHONPATH"] = os.pathsep.join(paths)
    python_dir = str(python_bin_dir or Path(sys.executable).resolve().parent)
    env["PATH"] = f"{python_dir}{os.pathsep}{env.get('PATH', '')}"
    return env


def _write_python3_shim(root):
    python_bin = Path(root) / "python-bin"
    python_bin.mkdir()
    fake_python = python_bin / "python3"
    fake_python.write_text(f"#!/usr/bin/env sh\nexec {sys.executable!r} \"$@\"\n")
    fake_python.chmod(0o755)
    return python_bin


def _write_memind_package(root, names):
    fake_package_root = Path(root) / "fake-python"
    fake_memind = fake_package_root / "memind"
    fake_memind.mkdir(parents=True)
    fake_memind.joinpath("__init__.py").write_text("\n".join(f"{name} = object" for name in names) + "\n")
    return fake_package_root


def _valid_memind_package(root):
    return _write_memind_package(
        root,
        [
            "AsyncMemindClient",
            "MemindClient",
            "ConversationContent",
            "Message",
            "Strategy",
        ],
    )


class InstallerTest(unittest.TestCase):
    def test_install_merges_and_reinstall_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            hooks_path = Path(tmp) / "hooks.json"
            config_path = Path(tmp) / "config.toml"
            hooks_path.write_text(json.dumps({"hooks": {"Stop": [{"hooks": [{"type": "command", "command": "echo existing"}]}]}}))
            install_hooks(ROOT, hooks_path, codex_config_path=config_path)
            install_hooks(ROOT, hooks_path, codex_config_path=config_path)
            hooks = json.loads(hooks_path.read_text())["hooks"]
            stop_commands = [hook["command"] for group in hooks["Stop"] for hook in group["hooks"]]
            self.assertIn("echo existing", stop_commands)
            memind_commands = [command for command in stop_commands if "memind-integrations/codex" in command]
            self.assertEqual(len(memind_commands), 1)

    def test_uninstall_removes_only_memind_entries(self):
        with tempfile.TemporaryDirectory() as tmp:
            hooks_path = Path(tmp) / "hooks.json"
            config_path = Path(tmp) / "config.toml"
            install_hooks(ROOT, hooks_path, codex_config_path=config_path)
            hooks = json.loads(hooks_path.read_text())
            hooks["hooks"].setdefault("Stop", []).append({"hooks": [{"type": "command", "command": "echo keep"}]})
            hooks_path.write_text(json.dumps(hooks))
            uninstall_hooks(hooks_path)
            remaining = json.loads(hooks_path.read_text())["hooks"]
            commands = [hook["command"] for group in remaining["Stop"] for hook in group["hooks"]]
            self.assertEqual(commands, ["echo keep"])

    def test_install_enables_feature_flag_when_config_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            hooks_path = Path(tmp) / "hooks.json"
            config_path = Path(tmp) / "config.toml"
            output = install_hooks(ROOT, hooks_path, codex_config_path=config_path)
            config_text = config_path.read_text()
        self.assertIn("Enabled Codex hooks feature flag", output)
        self.assertIn("[features]", config_text)
        self.assertIn("codex_hooks = true", config_text)

    def test_install_preserves_existing_config_when_enabling_feature_flag(self):
        with tempfile.TemporaryDirectory() as tmp:
            hooks_path = Path(tmp) / "hooks.json"
            config_path = Path(tmp) / "config.toml"
            config_path.write_text('model = "gpt-5.5"\n\n[features]\nfoo = true\n\n[projects."/tmp/example"]\ntrust_level = "trusted"\n')
            output = install_hooks(ROOT, hooks_path, codex_config_path=config_path)
            config_text = config_path.read_text()
        self.assertIn("Enabled Codex hooks feature flag", output)
        self.assertIn('model = "gpt-5.5"', config_text)
        self.assertIn("[features]\ncodex_hooks = true\nfoo = true", config_text)
        self.assertIn('[projects."/tmp/example"]', config_text)

    def test_no_feature_flag_change_when_enabled_under_features(self):
        with tempfile.TemporaryDirectory() as tmp:
            hooks_path = Path(tmp) / "hooks.json"
            config_path = Path(tmp) / "config.toml"
            config_path.write_text("[features]\n  codex_hooks   =   true\n")
            output = install_hooks(ROOT, hooks_path, codex_config_path=config_path)
            config_text = config_path.read_text()
        self.assertNotIn("Enabled Codex hooks feature flag", output)
        self.assertEqual(config_text, "[features]\n  codex_hooks   =   true\n")

    def test_shell_installer_installs_to_overridden_paths_without_home_writes(self):
        with tempfile.TemporaryDirectory() as tmp:
            python_bin = _write_python3_shim(tmp)
            fake_package_root = _valid_memind_package(tmp)
            install_root = Path(tmp) / "memind" / "codex"
            hooks_path = Path(tmp) / "codex" / "hooks.json"
            config_path = Path(tmp) / "codex" / "config.toml"
            result = subprocess.run(
                [
                    "bash",
                    str(ROOT / "install.sh"),
                    "--source-root",
                    str(ROOT),
                    "--install-root",
                    str(install_root),
                    "--hooks-path",
                    str(hooks_path),
                    "--config-path",
                    str(config_path),
                ],
                capture_output=True,
                check=True,
                env=_installer_env(fake_package_root, python_bin_dir=python_bin),
                text=True,
                timeout=10,
            )
            hooks = json.loads(hooks_path.read_text())
            config_text = config_path.read_text()
            self.assertIn("Installation complete", result.stdout)
            self.assertTrue((install_root / "install.sh").exists())
            self.assertTrue((install_root / "scripts" / "retrieve.py").exists())
            self.assertIn("UserPromptSubmit", hooks["hooks"])
            self.assertIn("codex_hooks = true", config_text)

    def test_shell_installer_reinstall_and_uninstall_are_owned_entry_safe(self):
        with tempfile.TemporaryDirectory() as tmp:
            python_bin = _write_python3_shim(tmp)
            fake_package_root = _valid_memind_package(tmp)
            install_root = Path(tmp) / "memind" / "codex"
            hooks_path = Path(tmp) / "codex" / "hooks.json"
            config_path = Path(tmp) / "codex" / "config.toml"
            base_command = [
                "bash",
                str(ROOT / "install.sh"),
                "--source-root",
                str(ROOT),
                "--install-root",
                str(install_root),
                "--hooks-path",
                str(hooks_path),
                "--config-path",
                str(config_path),
            ]
            subprocess.run(base_command, check=True, capture_output=True, text=True, timeout=10, env=_installer_env(fake_package_root, python_bin_dir=python_bin))
            hooks = json.loads(hooks_path.read_text())
            hooks["hooks"].setdefault("Stop", []).append({"hooks": [{"type": "command", "command": "echo keep"}]})
            hooks_path.write_text(json.dumps(hooks))

            subprocess.run(base_command, check=True, capture_output=True, text=True, timeout=10, env=_installer_env(fake_package_root, python_bin_dir=python_bin))
            reinstalled = json.loads(hooks_path.read_text())["hooks"]
            stop_commands = [hook["command"] for group in reinstalled["Stop"] for hook in group["hooks"]]
            memind_stop_commands = [command for command in stop_commands if "ingest.py" in command]
            self.assertEqual(len(memind_stop_commands), 1)
            self.assertIn("echo keep", stop_commands)

            subprocess.run(
                [
                    "bash",
                    str(install_root / "install.sh"),
                    "--uninstall",
                    "--install-root",
                    str(install_root),
                    "--hooks-path",
                    str(hooks_path),
                    "--config-path",
                    str(config_path),
                ],
                check=True,
                capture_output=True,
                env=_installer_env(fake_package_root, python_bin_dir=python_bin),
                text=True,
                timeout=10,
            )
            remaining = json.loads(hooks_path.read_text())["hooks"]
            remaining_stop_commands = [hook["command"] for group in remaining["Stop"] for hook in group["hooks"]]
            self.assertEqual(remaining_stop_commands, ["echo keep"])

    def test_shell_installer_dry_run_does_not_write_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            install_root = Path(tmp) / "memind" / "codex"
            hooks_path = Path(tmp) / "codex" / "hooks.json"
            config_path = Path(tmp) / "codex" / "config.toml"
            result = subprocess.run(
                [
                    "bash",
                    str(ROOT / "install.sh"),
                    "--dry-run",
                    "--source-root",
                    str(ROOT),
                    "--install-root",
                    str(install_root),
                    "--hooks-path",
                    str(hooks_path),
                    "--config-path",
                    str(config_path),
                ],
                capture_output=True,
                check=True,
                text=True,
                timeout=10,
            )
            self.assertIn("Dry run", result.stdout)
            self.assertFalse(install_root.exists())
            self.assertFalse(hooks_path.exists())
            self.assertFalse(config_path.exists())

    def test_shell_installer_fails_when_python_is_too_old(self):
        with tempfile.TemporaryDirectory() as tmp:
            fake_bin = Path(tmp) / "bin"
            fake_bin.mkdir()
            fake_python = fake_bin / "python3"
            fake_python.write_text(
                "#!/usr/bin/env sh\n"
                "echo 'error: Python 3.10+ is required for Memind Codex hooks' >&2\n"
                "exit 1\n"
            )
            fake_python.chmod(0o755)
            install_root = Path(tmp) / "memind" / "codex"
            hooks_path = Path(tmp) / "codex" / "hooks.json"
            config_path = Path(tmp) / "codex" / "config.toml"
            env = _installer_env()
            env["PATH"] = f"{fake_bin}{os.pathsep}{env.get('PATH', '')}"
            result = subprocess.run(
                [
                    "bash",
                    str(ROOT / "install.sh"),
                    "--source-root",
                    str(ROOT),
                    "--install-root",
                    str(install_root),
                    "--hooks-path",
                    str(hooks_path),
                    "--config-path",
                    str(config_path),
                ],
                capture_output=True,
                text=True,
                timeout=10,
                env=env,
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Python 3.10+", result.stderr)
        self.assertFalse(hooks_path.exists())

    def test_shell_installer_fails_when_memind_package_lacks_required_api(self):
        with tempfile.TemporaryDirectory() as tmp:
            python_bin = _write_python3_shim(tmp)
            fake_package_root = _write_memind_package(tmp, ["MemindClient"])
            install_root = Path(tmp) / "memind" / "codex"
            hooks_path = Path(tmp) / "codex" / "hooks.json"
            config_path = Path(tmp) / "codex" / "config.toml"
            result = subprocess.run(
                [
                    "bash",
                    str(ROOT / "install.sh"),
                    "--source-root",
                    str(ROOT),
                    "--install-root",
                    str(install_root),
                    "--hooks-path",
                    str(hooks_path),
                    "--config-path",
                    str(config_path),
                ],
                capture_output=True,
                text=True,
                timeout=10,
                env=_installer_env(fake_package_root, python_bin_dir=python_bin),
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Memind Python client", result.stderr)
        self.assertIn("AsyncMemindClient", result.stderr)
        self.assertFalse(hooks_path.exists())


if __name__ == "__main__":
    unittest.main()
