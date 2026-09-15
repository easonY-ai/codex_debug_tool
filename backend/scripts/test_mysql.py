#!/usr/bin/env python3
"""Run a command against an owned, disposable local MySQL instance (8.4+).

No existing MySQL configuration, credentials or data directory is used.
Default command: ./mvnw -B -ntp -Pmysql-integration verify
Override command after --; working directory is backend.
"""
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time


def main():
    binaries = {name: shutil.which(name) for name in ("mysqld", "mysql")}
    if not all(binaries.values()):
        raise SystemExit("Install MySQL 8.4+ and put mysqld and mysql on PATH before integration testing.")
    command = sys.argv[1:]
    if command[:1] == ["--"]:
        command = command[1:]
    command = command or ["./mvnw", "-B", "-ntp", "-Pmysql-integration", "verify"]
    with tempfile.TemporaryDirectory(prefix="trace-lens-mysql-") as directory:
        root = Path(directory)
        data = root / "data"
        # macOS Unix-domain socket paths have a small length limit.
        unix_socket = root / "m.sock"
        log = root / "mysql.log"
        environment = os.environ.copy()
        # Do not pass actual application credentials into database administration commands.
        for key in list(environment):
            if key.startswith("TRACE_LENS_DB_") or key.startswith("MYSQL"):
                del environment[key]
        subprocess.run([binaries["mysqld"], "--no-defaults", "--initialize-insecure",
                        f"--datadir={data}", f"--log-error={log}"], env=environment, check=True)
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        with log.open("ab") as output:
            server = subprocess.Popen([binaries["mysqld"], "--no-defaults", f"--datadir={data}",
                f"--socket={unix_socket}", f"--port={port}", "--bind-address=127.0.0.1",
                "--mysqlx=OFF", "--skip-log-bin", "--max-allowed-packet=268435456",
                f"--pid-file={root / 'mysql.pid'}", f"--log-error={log}"],
                env=environment, stdout=output, stderr=subprocess.STDOUT)
            client = [binaries["mysql"], "--no-defaults", "--no-login-paths", "--protocol=SOCKET",
                      f"--socket={unix_socket}", "--user=root", "--batch"]
            try:
                deadline = time.monotonic() + 60
                while time.monotonic() < deadline:
                    if server.poll() is not None:
                        raise RuntimeError("Temporary MySQL exited during startup: " + log.read_text())
                    ready = subprocess.run(client, input="SELECT 1;", text=True, env=environment,
                                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    if ready.returncode == 0:
                        break
                    time.sleep(0.2)
                else:
                    raise RuntimeError("Temporary MySQL did not become ready within 60 seconds")
                name = "codex_analyze_test_" + secrets.token_hex(8)
                password = secrets.token_hex(32)
                sql = (f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;"
                       f"CREATE USER 'trace_lens_fixture'@'127.0.0.1' IDENTIFIED BY '{password}';"
                       f"GRANT ALL PRIVILEGES ON `{name}`.* TO 'trace_lens_fixture'@'127.0.0.1';")
                subprocess.run(client, input=sql, text=True, env=environment, check=True,
                               stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
                environment.update(MYSQL_HOST="127.0.0.1", MYSQL_PORT=str(port),
                                   MYSQL_USERNAME="trace_lens_fixture", MYSQL_PASSWORD=password,
                                   TRACE_LENS_MYSQL_TEST="1")
                print("Running against isolated temporary MySQL; existing databases are untouched.", flush=True)
                return subprocess.call(command, cwd=Path(__file__).resolve().parents[1], env=environment)
            finally:
                server.terminate()
                try:
                    server.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    server.kill()
                    server.wait()


if __name__ == "__main__":
    sys.exit(main())
