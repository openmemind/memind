import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.install_codex_hooks import install_hooks, uninstall_hooks

ROOT = Path(__file__).resolve().parents[1]


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
            subprocess.run(base_command, check=True, capture_output=True, text=True, timeout=10)
            hooks = json.loads(hooks_path.read_text())
            hooks["hooks"].setdefault("Stop", []).append({"hooks": [{"type": "command", "command": "echo keep"}]})
            hooks_path.write_text(json.dumps(hooks))

            subprocess.run(base_command, check=True, capture_output=True, text=True, timeout=10)
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


if __name__ == "__main__":
    unittest.main()
