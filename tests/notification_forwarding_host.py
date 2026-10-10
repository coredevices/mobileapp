#!/usr/bin/env python3
"""Compile the production forwarding handler and codec with mock Apple sessions."""

from pathlib import Path
import re
import sys
import subprocess
import tempfile

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
SOURCES = [
    "iosApp/PairedIntegration/PebbleForwarding/NotificationForwardingHandler.swift",
    "iosApp/PairedIntegration/PebbleForwarding/PairedNotificationToken.swift",
    "iosApp/PairedIntegration/PebbleForwarding/PairedFIFO.swift",
    "iosApp/PairedIntegration/PebbleProtocol/AccessoryNotificationCodec.swift",
    "iosApp/PairedIntegration/PebbleProtocol/IntegerBytes.swift",
    "iosApp/PairedIntegration/PebbleProtocol/UTF8Truncation.swift",
]


def host_source(path: Path) -> str:
    source = path.read_text()
    source = source.replace("#if os(iOS)\n", "").replace("#endif\n", "")
    source = re.sub(r"^(?:public )?import (?:AccessoryNotifications|AccessoryTransportExtension|PebbleProtocol|OSLog)\n", "", source, flags=re.M)
    source = source.replace("package ", "")
    source = source.replace("public ", "")
    return source


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="pebble-forwarding-host-") as temp:
        directory = Path(temp)
        (directory / "main.swift").write_text(
            "import Foundation\nimport CryptoKit\nimport Synchronization\n"
            + (Path(__file__).with_name("notification_forwarding_stubs.swift")).read_text()
            + "\n".join(host_source(ROOT / path) for path in SOURCES)
            + (Path(__file__).with_name("notification_forwarding_cases.swift")).read_text()
        )
        binary = directory / "forwarding-host"
        subprocess.run(["xcrun", "swiftc", "-swift-version", "5", "-o", str(binary), str(directory / "main.swift")], check=True)
        subprocess.run([str(binary)], check=True, timeout=20)


if __name__ == "__main__":
    main()
