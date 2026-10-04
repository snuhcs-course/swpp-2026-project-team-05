"""Use the macOS keychain for local HTTPS verification."""

import sys


def configure_tls() -> None:
    if sys.platform == "darwin":
        import truststore

        truststore.inject_into_ssl()
