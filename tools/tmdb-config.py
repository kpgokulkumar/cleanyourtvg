#!/usr/bin/env python3
"""Prepare a private optional TMDB configuration; never put tokens in config.xml or themes.

    python3 tools/tmdb-config.py work/tmdb-token.txt work/tmdb-import.json
    python3 tools/tmdb-config.py --disable work/tmdb-import.json

Push the output to /sdcard/Android/data/com.gathin.tvhome/files/tmdb-import.json,
then restart/reinstall Home. Home imports it into its private files and deletes
the staging file. Treat staging files and ADB access as credentials.
"""
import json
import os
from pathlib import Path
import sys

def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    disabled = sys.argv[1] == '--disable'
    token = '' if disabled else Path(sys.argv[1]).read_text().strip()
    if not disabled and (not token or '\n' in token or '\r' in token):
        raise SystemExit('Expected one API Read Access Token, with no line breaks.')
    target = Path(sys.argv[2])
    target.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, 'w') as out:
        json.dump({'enabled': not disabled, 'token': token}, out)
        out.write('\n')
    print('Private configuration prepared; do not commit it.')

if __name__ == '__main__':
    main()
