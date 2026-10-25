# Connect to the TV over ADB

## 1. Find the TV

Find the TV on my network using its hostname or the ARP table, or ask me for its IP address.
Connect over ADB on **port 5555**.

If the TV is in another home, use the Tailscale address I provide. Follow
[the remote connection guide](docs/REMOTE.md); don't scan my local network.

## 2. Connect and verify

From the repository root, replace `<tv ip>` with the TV's address and run:

```sh
TV_HOST='<tv ip>' tools/venv/bin/python tools/tv.py 'echo ADB_CONNECTED'
```

This uses the existing venv's `adb-shell` client and [tools/tv.py](tools/tv.py),
connecting directly to port 5555 without the stock adb binary or a relay.
It uses an existing ADB key when available.

> **TV confirmation:** Tell me when the **“Allow USB debugging?”** prompt will appear
> so I can accept it.

Expected output:

```text
ADB_CONNECTED
```

This confirms an authenticated shell. The command does not change TV settings or apps.

## 3. Resolve connection problems

### Sandbox blocks local-network access

If the Python client fails with:

```text
PermissionError: [Errno 1] Operation not permitted
```

Request permission through the execution tool to run the same command outside the
sandbox. **This route worked:** the sandboxed attempt failed, and the approved
attempt returned `ADB_CONNECTED`.

If execution outside the sandbox is unavailable and the TV answers from an ordinary
terminal, ask me to run this command there and leave it running:

```sh
python3 tools/relay.py <tv ip>
```

Then connect through the relay:

```sh
TV_HOST=127.0.0.1 TV_PORT=15555 tools/venv/bin/python tools/tv.py 'echo ADB_CONNECTED'
```

See [the relay guide](tools/relay.md).

### macOS reports “No route to host”

If the stock adb binary fails with this error even though the port is open, macOS
Local Network privacy is blocking adb. Don't debug it: use the Python `adb-shell`
client in a venv instead, as shown above.

### Port 5555 stays closed

The TV may only offer **Wireless debugging**, which is common on Google TV sets.

1. Look for `_adb-tls-connect._tcp` with `dns-sd -B` or `adb mdns services`.
2. Ask me for the code and port shown under **Developer options → Wireless debugging
   → Pair device with pairing code**.
3. Run `adb pair`, then `adb connect`, then `adb tcpip 5555`.

Android switches Wireless debugging off whenever Wi-Fi drops.

## File transfers and installs

| Symptom | Action |
|---|---|
| Python client's file push hangs on larger files | Transfer files as base64 chunks through the shell and verify the MD5. |
| `adb install` hangs on Android 14 | It is waiting for an on-screen confirmation. Push the APK and run `pm install -r` over the shell instead. |
