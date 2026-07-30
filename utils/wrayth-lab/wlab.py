#!/usr/bin/env python3
"""wlab: run Wrayth.exe under wine against a fake game server, feed it protocol
data, and screenshot the result.

The point is to see how the real Simutronics client renders a given piece of the
protocol, so Warlock can match it. Nothing here talks to a real game server.

Typical session:

    utils/wrayth-lab/wlab.py up                   # fake server + Wrayth in a wine desktop
    utils/wrayth-lab/wlab.py send '<pushBold/>bold text<popBold/>'
    utils/wrayth-lab/wlab.py shot bold.png        # -> /tmp/wrayth-lab/shots/bold.png
    utils/wrayth-lab/wlab.py recv                 # what Wrayth sent back to us
    utils/wrayth-lab/wlab.py stop

State (logs, screenshots, the generated .sal) lives in /tmp/wrayth-lab; override
with --dir or $WLAB_DIR. Standard library only, no dependencies.
"""

import argparse
import ctypes
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_DIR = Path(os.environ.get("WLAB_DIR", "/tmp/wrayth-lab"))
DEFAULT_GAME_PORT = 7000
DESKTOP_NAME = "WraythLab"
DEFAULT_SIZE = "1280x900"
WIRE_ENCODING = "iso-8859-1"  # what the game protocol and WraythClient use

EXE_CANDIDATES = [
    "drive_c/Program Files (x86)/SIMU/Wrayth/Wrayth.exe",
    "drive_c/Program Files/SIMU/Wrayth/Wrayth.exe",
]


# --------------------------------------------------------------------------- #
# helpers
# --------------------------------------------------------------------------- #


def die(msg):
    print("wlab: " + msg, file=sys.stderr)
    raise SystemExit(1)


def wineprefix():
    return Path(os.environ.get("WINEPREFIX", str(Path.home() / ".wine")))


def find_exe(explicit):
    if explicit:
        p = Path(explicit).expanduser()
        if not p.is_file():
            die("no such exe: %s" % p)
        return p
    env = os.environ.get("WRAYTH_EXE")
    if env:
        return find_exe(env)
    for rel in EXE_CANDIDATES:
        p = wineprefix() / rel
        if p.is_file():
            return p
    die("could not find Wrayth.exe under %s; pass --exe" % wineprefix())


def win_path(unix_path):
    """Unix path -> windows path, using winepath when available."""
    try:
        out = subprocess.run(
            ["winepath", "-w", str(unix_path)],
            capture_output=True,
            text=True,
            timeout=60,
            env=dict(os.environ, WINEPREFIX=str(wineprefix()), WINEDEBUG="-all"),
        )
        line = out.stdout.strip()
        if out.returncode == 0 and line:
            return line
    except (OSError, subprocess.SubprocessError):
        pass
    # Fall back to the Z: drive wine maps to /.
    return "Z:" + str(Path(unix_path).resolve()).replace("/", "\\")


def state_path(d):
    return d / "state.json"


def read_state(d):
    try:
        return json.loads(state_path(d).read_text())
    except (OSError, ValueError):
        return {}


def write_state(d, **kw):
    st = read_state(d)
    st.update(kw)
    state_path(d).write_text(json.dumps(st, indent=2) + "\n")
    return st


def unescape(text):
    """Interpret backslash escapes (\\n, \\t, \\xNN) without touching anything else."""
    return text.encode("utf-8").decode("unicode_escape")


# --------------------------------------------------------------------------- #
# the fake game server
# --------------------------------------------------------------------------- #


class Server:
    """Accepts Wrayth's game connection and relays whatever we tell it to send.

    Two listeners: the game port (what the .sal points at) and a control port
    that the other wlab subcommands drive with one JSON object per connection.
    """

    def __init__(self, args):
        self.args = args
        self.dir = args.dir
        self.eol = b"\n" if args.lf else b"\r\n"
        self.lock = threading.Lock()
        self.client = None
        self.conn_count = 0
        self.handshake = []
        self.recv_lines = []
        self.sent_lines = 0
        self.log_file = open(self.dir / "server.log", "a", buffering=1)
        self.client_file = open(self.dir / "client.log", "a", buffering=1)

    def log(self, msg):
        line = "%s %s" % (time.strftime("%H:%M:%S"), msg)
        print(line, flush=True)
        self.log_file.write(line + "\n")

    # -- game side --------------------------------------------------------- #

    def serve_forever(self):
        game = socket.socket()
        game.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        game.bind(("127.0.0.1", self.args.game_port))
        game.listen(4)
        control = socket.socket()
        control.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        control.bind(("127.0.0.1", self.args.control_port))
        control.listen(8)
        self.log(
            "listening: game=127.0.0.1:%d control=127.0.0.1:%d eol=%s"
            % (self.args.game_port, self.args.control_port, "LF" if self.args.lf else "CRLF")
        )
        threading.Thread(target=self._accept_loop, args=(control, self._serve_control), daemon=True).start()
        self._accept_loop(game, self._serve_game)

    def _accept_loop(self, listener, handler):
        while True:
            try:
                sock, addr = listener.accept()
            except OSError:
                return
            threading.Thread(target=handler, args=(sock, addr), daemon=True).start()

    def _serve_game(self, sock, addr):
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        with self.lock:
            self.conn_count += 1
            n = self.conn_count
            self.client = sock
            self.handshake = []
        self.log("client #%d connected from %s:%d" % (n, addr[0], addr[1]))
        if self.args.bootstrap:
            threading.Thread(target=self._send_bootstrap, daemon=True).start()
        buf = b""
        try:
            while True:
                chunk = sock.recv(4096)
                if not chunk:
                    break
                buf += chunk
                while b"\n" in buf:
                    raw, buf = buf.split(b"\n", 1)
                    self._on_client_line(raw.rstrip(b"\r").decode(WIRE_ENCODING, "replace"), n)
        except OSError as exc:
            self.log("client #%d read error: %s" % (n, exc))
        finally:
            if buf:
                self._on_client_line(buf.decode(WIRE_ENCODING, "replace"), n)
            self.log("client #%d disconnected" % n)
            with self.lock:
                if self.client is sock:
                    self.client = None
            try:
                sock.close()
            except OSError:
                pass

    def _on_client_line(self, line, conn):
        with self.lock:
            if len(self.handshake) < 2:
                self.handshake.append(line)
                kind = "key" if len(self.handshake) == 1 else "fe-info"
            else:
                kind = "input"
            self.recv_lines.append(line)
            del self.recv_lines[:-500]
        self.client_file.write(line + "\n")
        self.log("<- #%d %s: %s" % (conn, kind, line))

    def _send_bootstrap(self):
        path = Path(self.args.bootstrap)
        if not path.is_file():
            self.log("bootstrap file missing: %s" % path)
            return
        time.sleep(self.args.bootstrap_delay / 1000.0)
        lines = path.read_text(encoding=WIRE_ENCODING).splitlines()
        lines = [ln for ln in lines if not ln.startswith("##")]
        sent = self.send_lines(lines, delay_ms=0)
        self.log("sent bootstrap %s (%d lines)" % (path.name, sent))

    def send_lines(self, lines, delay_ms=0):
        with self.lock:
            sock = self.client
        if sock is None:
            return 0
        count = 0
        for line in lines:
            payload = line.encode(WIRE_ENCODING, "replace") + self.eol
            try:
                sock.sendall(payload)
            except OSError as exc:
                self.log("send failed: %s" % exc)
                break
            count += 1
            self.log("-> %s" % line)
            if delay_ms:
                time.sleep(delay_ms / 1000.0)
        with self.lock:
            self.sent_lines += count
        return count

    def send_raw(self, text):
        with self.lock:
            sock = self.client
        if sock is None:
            return 0
        data = text.encode(WIRE_ENCODING, "replace")
        try:
            sock.sendall(data)
        except OSError as exc:
            self.log("send failed: %s" % exc)
            return 0
        self.log("-> (raw %d bytes) %s" % (len(data), text.replace("\n", "\\n")))
        return len(data)

    # -- control side ------------------------------------------------------ #

    def _serve_control(self, sock, _addr):
        try:
            f = sock.makefile("rwb")
            request = f.readline()
            if not request:
                return
            try:
                req = json.loads(request.decode("utf-8"))
            except ValueError as exc:
                reply = {"ok": False, "error": "bad json: %s" % exc}
            else:
                reply = self._handle_control(req)
            f.write((json.dumps(reply) + "\n").encode("utf-8"))
            f.flush()
        except OSError:
            pass
        finally:
            try:
                sock.close()
            except OSError:
                pass

    def _handle_control(self, req):
        cmd = req.get("cmd")
        with self.lock:
            connected = self.client is not None
        if cmd == "ping":
            return {"ok": True, "connected": connected}
        if cmd == "status":
            with self.lock:
                return {
                    "ok": True,
                    "connected": connected,
                    "connections": self.conn_count,
                    "handshake": list(self.handshake),
                    "lines_sent": self.sent_lines,
                    "lines_received": len(self.recv_lines),
                    "game_port": self.args.game_port,
                    "control_port": self.args.control_port,
                }
        if cmd == "recv":
            n = int(req.get("count", 40))
            with self.lock:
                return {"ok": True, "lines": self.recv_lines[-n:] if n > 0 else list(self.recv_lines)}
        if cmd == "send":
            if not connected:
                return {"ok": False, "error": "no client connected"}
            if req.get("raw"):
                return {"ok": True, "bytes": self.send_raw(req.get("data", ""))}
            lines = req.get("lines")
            if lines is None:
                lines = req.get("data", "").split("\n")
            return {"ok": True, "lines": self.send_lines(lines, int(req.get("delay_ms", 0)))}
        if cmd == "boot":
            if not connected:
                return {"ok": False, "error": "no client connected"}
            path = Path(req.get("path") or self.args.bootstrap)
            if not path.is_file():
                return {"ok": False, "error": "no such file: %s" % path}
            lines = [ln for ln in path.read_text(encoding=WIRE_ENCODING).splitlines() if not ln.startswith("##")]
            return {"ok": True, "lines": self.send_lines(lines, int(req.get("delay_ms", 0)))}
        if cmd == "drop":
            with self.lock:
                sock = self.client
                self.client = None
            if sock is not None:
                try:
                    sock.close()
                except OSError:
                    pass
            return {"ok": True, "dropped": sock is not None}
        if cmd == "shutdown":
            self.log("shutdown requested")
            threading.Timer(0.2, lambda: os._exit(0)).start()
            return {"ok": True}
        return {"ok": False, "error": "unknown cmd: %r" % cmd}


# --------------------------------------------------------------------------- #
# control client
# --------------------------------------------------------------------------- #


def control(d, req, timeout=15):
    st = read_state(d)
    port = st.get("control_port")
    if port is None:
        die("no server state in %s; run 'wlab serve' or 'wlab up' first" % d)
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=timeout) as sock:
            sock.sendall((json.dumps(req) + "\n").encode("utf-8"))
            f = sock.makefile("rb")
            line = f.readline()
    except OSError as exc:
        die("cannot reach server on control port %d (%s); is it running?" % (port, exc))
    if not line:
        die("server closed the control connection without replying")
    return json.loads(line.decode("utf-8"))


def server_alive(d):
    st = read_state(d)
    if not st.get("control_port"):
        return False
    try:
        with socket.create_connection(("127.0.0.1", st["control_port"]), timeout=2) as sock:
            sock.sendall(b'{"cmd":"ping"}\n')
            return bool(sock.makefile("rb").readline())
    except OSError:
        return False


# --------------------------------------------------------------------------- #
# X11: window lookup, screenshots, input injection
# --------------------------------------------------------------------------- #

WIN_RE = re.compile(
    r'^\s*(0x[0-9a-f]+)\s+(?:"([^"]*)"|\(has no name\)):\s+'
    r'\("([^"]*)"\s+"[^"]*"\)\s+(\d+)x(\d+)\+(-?\d+)\+(-?\d+)\s+\+(-?\d+)\+(-?\d+)'
)


def list_windows():
    try:
        out = subprocess.run(["xwininfo", "-root", "-tree"], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError) as exc:
        die("xwininfo failed: %s (install x11-utils)" % exc)
    if out.returncode != 0:
        die("xwininfo failed: %s" % out.stderr.strip())
    windows = []
    for line in out.stdout.splitlines():
        m = WIN_RE.match(line)
        if not m:
            continue
        wid, title, cls, w, h, _x, _y, ax, ay = m.groups()
        windows.append(
            {
                "id": wid,
                "title": title or "",
                "class": cls,
                "w": int(w),
                "h": int(h),
                "x": int(ax),
                "y": int(ay),
                "area": int(w) * int(h),
            }
        )
    return windows


def _is_target(w):
    """True for the wine virtual desktop or any real Wrayth window."""
    if DESKTOP_NAME.lower() in w["title"].lower():
        return True
    return "wrayth" in w["class"].lower() and w["area"] > 10000


def pick_window(match=None):
    """Default target is the wine virtual desktop; otherwise the biggest Wrayth window."""
    windows = list_windows()
    if match:
        low = match.lower()
        cands = [w for w in windows if low in w["title"].lower() or low in w["class"].lower() or w["id"] == match]
    else:
        cands = [w for w in windows if DESKTOP_NAME.lower() in w["title"].lower()]
        if not cands:
            cands = [w for w in windows if "wrayth" in w["class"].lower() or "wrayth" in w["title"].lower()]
    cands = [w for w in cands if w["area"] > 100]
    if not cands:
        die("no matching window found (is Wrayth running? try 'wlab windows')")
    return max(cands, key=lambda w: w["area"])


def capture(win, out_path, max_width=1400, crop=None):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    tool = shutil.which("import")
    if tool:
        cmd = [tool, "-window", win["id"], str(out_path)]
    elif shutil.which("xwd") and shutil.which("convert"):
        cmd = None
    else:
        die("need ImageMagick 'import' (package imagemagick) to take screenshots")
    if cmd:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        if res.returncode != 0 or not out_path.is_file():
            die("import failed: %s" % (res.stderr.strip() or "no output"))
    else:
        xwd = subprocess.run(["xwd", "-id", win["id"]], capture_output=True, timeout=120)
        if xwd.returncode != 0:
            die("xwd failed: %s" % xwd.stderr.decode(errors="replace").strip())
        subprocess.run(["convert", "xwd:-", str(out_path)], input=xwd.stdout, check=True, timeout=120)
    if crop:
        if not shutil.which("convert"):
            die("--crop needs ImageMagick 'convert'")
        subprocess.run(["convert", str(out_path), "-crop", crop, "+repage", str(out_path)], check=True, timeout=120)
    width = int(crop.split("x")[0]) if crop else win["w"]
    if max_width and width > max_width and shutil.which("convert"):
        subprocess.run(["convert", str(out_path), "-resize", "%dx" % max_width, str(out_path)], timeout=120)
    return out_path


class X11:
    """Just enough libX11/libXtst via ctypes to focus a window and fake input."""

    def __init__(self):
        try:
            self.x = ctypes.CDLL("libX11.so.6")
            self.tst = ctypes.CDLL("libXtst.so.6")
        except OSError as exc:
            die("cannot load X libraries: %s" % exc)
        self.x.XOpenDisplay.restype = ctypes.c_void_p
        self.x.XOpenDisplay.argtypes = [ctypes.c_char_p]
        self.dpy = self.x.XOpenDisplay(None)
        if not self.dpy:
            die("cannot open X display %s" % os.environ.get("DISPLAY", "(unset)"))
        self.x.XStringToKeysym.restype = ctypes.c_ulong
        self.x.XStringToKeysym.argtypes = [ctypes.c_char_p]
        self.x.XKeysymToKeycode.restype = ctypes.c_ubyte
        self.x.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
        self.x.XkbKeycodeToKeysym.restype = ctypes.c_ulong
        self.x.XkbKeycodeToKeysym.argtypes = [ctypes.c_void_p, ctypes.c_ubyte, ctypes.c_int, ctypes.c_int]
        self.x.XFlush.argtypes = [ctypes.c_void_p]
        self.x.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
        self.x.XRaiseWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
        self.x.XMoveResizeWindow.argtypes = [
            ctypes.c_void_p,
            ctypes.c_ulong,
            ctypes.c_int,
            ctypes.c_int,
            ctypes.c_uint,
            ctypes.c_uint,
        ]
        self.x.XResizeWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_uint, ctypes.c_uint]
        self.x.XSetInputFocus.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
        self.tst.XTestFakeKeyEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
        self.tst.XTestFakeButtonEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
        self.tst.XTestFakeMotionEvent.argtypes = [
            ctypes.c_void_p,
            ctypes.c_int,
            ctypes.c_int,
            ctypes.c_int,
            ctypes.c_ulong,
        ]
        self.shift = self._keycode_of_name("Shift_L")

    def _keycode_of_name(self, name):
        return self.x.XKeysymToKeycode(self.dpy, self.x.XStringToKeysym(name.encode()))

    def focus(self, win_id):
        wid = int(win_id, 16) if isinstance(win_id, str) else win_id
        self.x.XRaiseWindow(self.dpy, wid)
        self.x.XSetInputFocus(self.dpy, wid, 2, 0)  # RevertToParent, CurrentTime
        self.x.XSync(self.dpy, 0)
        time.sleep(0.15)

    def resize(self, win_id, w, h, x=None, y=None):
        wid = int(win_id, 16) if isinstance(win_id, str) else win_id
        if x is None:
            self.x.XResizeWindow(self.dpy, wid, w, h)
        else:
            self.x.XMoveResizeWindow(self.dpy, wid, x, y, w, h)
        self.x.XSync(self.dpy, 0)

    def _tap(self, keycode, mods=()):
        for m in mods:
            self.tst.XTestFakeKeyEvent(self.dpy, m, 1, 0)
        self.tst.XTestFakeKeyEvent(self.dpy, keycode, 1, 0)
        self.tst.XTestFakeKeyEvent(self.dpy, keycode, 0, 0)
        for m in reversed(mods):
            self.tst.XTestFakeKeyEvent(self.dpy, m, 0, 0)
        self.x.XFlush(self.dpy)

    def type_text(self, text, delay=0.02):
        for ch in text:
            keysym = ord(ch)
            kc = self.x.XKeysymToKeycode(self.dpy, keysym)
            if kc == 0:
                continue
            plain = self.x.XkbKeycodeToKeysym(self.dpy, kc, 0, 0)
            self._tap(kc, (self.shift,) if plain != keysym else ())
            time.sleep(delay)

    def press(self, spec):
        """spec like 'Return', 'ctrl+c', 'alt+shift+F1'."""
        mod_names = {"ctrl": "Control_L", "control": "Control_L", "alt": "Alt_L", "shift": "Shift_L", "super": "Super_L"}
        parts = spec.split("+")
        mods = []
        for p in parts[:-1]:
            name = mod_names.get(p.lower())
            if not name:
                die("unknown modifier: %s" % p)
            mods.append(self._keycode_of_name(name))
        key = parts[-1]
        keysym = self.x.XStringToKeysym(key.encode())
        if keysym == 0 and len(key) == 1:
            keysym = ord(key)
        if keysym == 0:
            die("unknown key: %s" % key)
        kc = self.x.XKeysymToKeycode(self.dpy, keysym)
        if kc == 0:
            die("no keycode for key: %s" % key)
        self._tap(kc, tuple(mods))

    def click(self, x, y, button=1):
        self.tst.XTestFakeMotionEvent(self.dpy, -1, int(x), int(y), 0)
        self.x.XFlush(self.dpy)
        time.sleep(0.05)
        self.tst.XTestFakeButtonEvent(self.dpy, button, 1, 0)
        self.tst.XTestFakeButtonEvent(self.dpy, button, 0, 0)
        self.x.XFlush(self.dpy)


# --------------------------------------------------------------------------- #
# subcommands
# --------------------------------------------------------------------------- #


def cmd_serve(args):
    args.dir.mkdir(parents=True, exist_ok=True)
    if args.control_port is None:
        args.control_port = args.game_port + 1
    if args.bootstrap is None:
        args.bootstrap = str(HERE / "bootstrap.txt")
    elif args.bootstrap.lower() in ("none", "off", ""):
        args.bootstrap = None
    write_state(
        args.dir,
        game_port=args.game_port,
        control_port=args.control_port,
        server_pid=os.getpid(),
    )
    Server(args).serve_forever()


def start_server_background(args):
    if server_alive(args.dir):
        print("server already running (control port %d)" % read_state(args.dir)["control_port"])
        return
    args.dir.mkdir(parents=True, exist_ok=True)
    log = open(args.dir / "serve.out", "a", buffering=1)
    cmd = [sys.executable, str(Path(__file__).resolve()), "--dir", str(args.dir), "serve",
           "--game-port", str(args.game_port)]
    if args.control_port is not None:
        cmd += ["--control-port", str(args.control_port)]
    if args.bootstrap is not None:
        cmd += ["--bootstrap", args.bootstrap]
    if args.lf:
        cmd.append("--lf")
    subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    for _ in range(50):
        time.sleep(0.1)
        if server_alive(args.dir):
            print("server started (game port %d, log %s)" % (args.game_port, args.dir / "server.log"))
            return
    die("server did not come up; see %s" % (args.dir / "serve.out"))


def cmd_launch(args):
    """Start Wrayth pointed at our fake server.

    Wrayth ignores a .sal path on its command line; the website flow hands the
    .sal to SGE's Launcher.exe, which translates it into the switches used here:
    /G<gamecode>/H<host>/P<port>/K<key>, concatenated with no spaces.
    """
    exe = find_exe(args.exe)
    st = read_state(args.dir)
    port = args.game_port or st.get("game_port") or DEFAULT_GAME_PORT
    args.dir.mkdir(parents=True, exist_ok=True)
    switches = "/G%s/H%s/P%d/K%s" % (args.game_code, args.host, port, args.key)
    env = dict(os.environ, WINEPREFIX=str(wineprefix()), WINEDEBUG=os.environ.get("WINEDEBUG", "-all"))
    cmd = ["wine"]
    if args.desktop:
        # A wine virtual desktop keeps every Wrayth window inside one capture
        # target. Some compositors force it to full screen regardless of the
        # size given here, so it is opt-in.
        cmd += ["explorer", "/desktop=%s,%s" % (DESKTOP_NAME, args.desktop)]
    cmd += [win_path(exe), switches]
    log = open(args.dir / "wine.log", "a", buffering=1)
    log.write("\n=== %s launching: %s\n" % (time.strftime("%H:%M:%S"), " ".join(cmd)))
    proc = subprocess.Popen(
        cmd, cwd=str(exe.parent), env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True
    )
    write_state(args.dir, wine_pid=proc.pid, exe=str(exe), switches=switches, desktop=args.desktop or "")
    print("launched %s %s" % (exe.name, switches))
    deadline = time.time() + args.wait
    win = None
    while time.time() < deadline:
        time.sleep(0.5)
        wins = [w for w in list_windows() if _is_target(w)]
        if wins:
            win = max(wins, key=lambda w: w["area"])
            break
    if win is None:
        print("warning: no Wrayth window after %ds; see %s" % (args.wait, args.dir / "wine.log"))
        return
    if args.size and args.size != "none" and not args.desktop:
        try:
            w, h = (int(v) for v in args.size.lower().split("x"))
        except ValueError:
            die("--size wants WxH, got %r" % args.size)
        # Wrayth restores its saved maximized geometry a moment after the window
        # appears, so one resize right away tends to lose the race. Retry until
        # the size sticks.
        x11 = X11()
        for attempt in range(4):
            time.sleep(1.5)
            x11.resize(win["id"], w, h)
            time.sleep(1.0)
            win = pick_window(win["id"])
            if (win["w"], win["h"]) == (w, h):
                break
        else:
            print("warning: window settled at %dx%d, not %dx%d" % (win["w"], win["h"], w, h))
    print("window ready: %s %s %dx%d" % (win["id"], win["title"] or win["class"], win["w"], win["h"]))


def cmd_up(args):
    start_server_background(args)
    cmd_launch(args)
    if args.shot:
        time.sleep(2)
        args.out, args.window, args.max_width, args.full, args.crop = None, None, 1400, False, None
        cmd_shot(args)


def cmd_send(args):
    text = " ".join(args.text) if args.text else sys.stdin.read()
    if args.escape:
        text = unescape(text)
    if args.raw:
        rep = control(args.dir, {"cmd": "send", "data": text, "raw": True})
    else:
        rep = control(args.dir, {"cmd": "send", "data": text.rstrip("\n"), "delay_ms": args.delay})
    if not rep.get("ok"):
        die(rep.get("error", "send failed"))
    print("sent %s" % ("%d bytes" % rep["bytes"] if args.raw else "%d line(s)" % rep["lines"]))


def cmd_sendfile(args):
    path = Path(args.file)
    if not path.is_file():
        die("no such file: %s" % path)
    lines = [ln for ln in path.read_text(encoding=WIRE_ENCODING).splitlines() if not ln.startswith("##")]
    rep = control(args.dir, {"cmd": "send", "lines": lines, "delay_ms": args.delay}, timeout=600)
    if not rep.get("ok"):
        die(rep.get("error", "send failed"))
    print("sent %d line(s) from %s" % (rep["lines"], path))


def cmd_boot(args):
    req = {"cmd": "boot", "delay_ms": args.delay}
    if args.file:
        req["path"] = str(Path(args.file).resolve())
    rep = control(args.dir, req, timeout=600)
    if not rep.get("ok"):
        die(rep.get("error", "boot failed"))
    print("sent %d bootstrap line(s)" % rep["lines"])


def cmd_recv(args):
    rep = control(args.dir, {"cmd": "recv", "count": args.count})
    for line in rep.get("lines", []):
        print(line)


def cmd_status(args):
    if not server_alive(args.dir):
        print("server: not running (state dir %s)" % args.dir)
    else:
        rep = control(args.dir, {"cmd": "status"})
        print("server: up  game=%d control=%d" % (rep["game_port"], rep["control_port"]))
        print("client: %s  connections=%d sent=%d received=%d"
              % ("connected" if rep["connected"] else "disconnected", rep["connections"],
                 rep["lines_sent"], rep["lines_received"]))
        for i, line in enumerate(rep["handshake"]):
            print("handshake[%d]: %s" % (i, line))
    wins = [w for w in list_windows() if "wrayth" in w["class"].lower() or DESKTOP_NAME.lower() in w["title"].lower()]
    for w in sorted(wins, key=lambda w: -w["area"]):
        print("window: %s %-28s %dx%d+%d+%d" % (w["id"], '"%s"' % w["title"], w["w"], w["h"], w["x"], w["y"]))


def cmd_windows(args):
    for w in list_windows():
        print("%s %-34s %-14s %dx%d+%d+%d" % (w["id"], '"%s"' % w["title"], w["class"], w["w"], w["h"], w["x"], w["y"]))


def cmd_shot(args):
    win = pick_window(args.window)
    name = args.out or ("shot-%s.png" % time.strftime("%H%M%S"))
    out = Path(name)
    if not out.is_absolute():
        out = args.dir / "shots" / out
    if out.suffix == "":
        out = out.with_suffix(".png")
    capture(win, out, 0 if args.full else args.max_width, args.crop)
    print("%s (%s %dx%d%s)" % (out, win["title"] or win["class"], win["w"], win["h"],
                               " crop " + args.crop if args.crop else ""))


def cmd_resize(args):
    win = pick_window(args.window)
    try:
        w, h = (int(v) for v in args.size.lower().split("x"))
    except ValueError:
        die("size wants WxH, got %r" % args.size)
    X11().resize(win["id"], w, h, args.pos_x, args.pos_y)
    time.sleep(1.0)
    now = pick_window(win["id"])
    print("%s is now %dx%d+%d+%d" % (now["id"], now["w"], now["h"], now["x"], now["y"]))


def cmd_type(args):
    win = pick_window(args.window)
    x = X11()
    x.focus(win["id"])
    x.type_text(" ".join(args.text))
    if args.enter:
        x.press("Return")
    print("typed %d char(s) into %s" % (len(" ".join(args.text)), win["title"] or win["class"]))


def cmd_key(args):
    win = pick_window(args.window)
    x = X11()
    x.focus(win["id"])
    for spec in args.keys:
        x.press(spec)
        time.sleep(0.05)
    print("pressed %s" % ", ".join(args.keys))


def cmd_click(args):
    win = pick_window(args.window)
    x = X11()
    x.focus(win["id"])
    gx, gy = (args.x, args.y) if args.absolute else (win["x"] + args.x, win["y"] + args.y)
    x.click(gx, gy, args.button)
    print("clicked button %d at %d,%d" % (args.button, gx, gy))


def cmd_stop(args):
    if server_alive(args.dir) and not args.wine_only:
        control(args.dir, {"cmd": "shutdown"})
        print("server stopped")
    if not args.server_only:
        env = dict(os.environ, WINEPREFIX=str(wineprefix()), WINEDEBUG="-all")
        res = subprocess.run(["wineserver", "-k"], env=env, capture_output=True, text=True)
        print("wine killed" if res.returncode == 0 else "wineserver -k: %s" % res.stderr.strip())


# --------------------------------------------------------------------------- #


def main(argv=None):
    p = argparse.ArgumentParser(prog="wlab", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--dir", type=Path, default=DEFAULT_DIR, help="state dir (default %(default)s)")
    sub = p.add_subparsers(dest="cmd", required=True)

    def server_opts(sp):
        sp.add_argument("--game-port", type=int, default=DEFAULT_GAME_PORT)
        sp.add_argument("--control-port", type=int, default=None, help="default: game port + 1")
        sp.add_argument("--bootstrap", default=None, help="file sent on connect, or 'none'")
        sp.add_argument("--bootstrap-delay", type=int, default=300, help="ms to wait after connect")
        sp.add_argument("--lf", action="store_true", help="terminate lines with LF instead of CRLF")

    def launch_opts(sp):
        sp.add_argument("--exe", default=None, help="path to Wrayth.exe")
        sp.add_argument("--size", default=DEFAULT_SIZE, help="resize the window to WxH after launch, or 'none'")
        sp.add_argument("--desktop", default=None, metavar="WxH",
                        help="run inside a wine virtual desktop of this size (some compositors ignore the size)")
        sp.add_argument("--key", default="wlab", help="/K key Wrayth sends as its first line")
        sp.add_argument("--game-code", default="DR", help="/G game code (DR, GS4, ...)")
        sp.add_argument("--host", default="127.0.0.1", help="/H host Wrayth connects to")
        sp.add_argument("--wait", type=int, default=30, help="seconds to wait for the window")

    sp = sub.add_parser("serve", help="run the fake game server in the foreground")
    server_opts(sp)
    sp.set_defaults(func=cmd_serve)

    sp = sub.add_parser("launch", help="write a .sal and start Wrayth under wine")
    launch_opts(sp)
    sp.add_argument("--game-port", type=int, default=None)
    sp.set_defaults(func=cmd_launch)

    sp = sub.add_parser("up", help="start the server in the background, then launch Wrayth")
    server_opts(sp)
    launch_opts(sp)
    sp.add_argument("--shot", action="store_true", help="screenshot once the window appears")
    sp.set_defaults(func=cmd_up)

    sp = sub.add_parser("send", help="send protocol text to Wrayth (one line per argument line)")
    sp.add_argument("text", nargs="*", help="text to send; reads stdin when omitted")
    sp.add_argument("-e", "--escape", action="store_true", help=r"interpret \n, \t, \xNN")
    sp.add_argument("--raw", action="store_true", help="send bytes verbatim, no line terminator added")
    sp.add_argument("--delay", type=int, default=0, help="ms between lines")
    sp.set_defaults(func=cmd_send)

    sp = sub.add_parser("sendfile", help="send a file, one protocol line per text line")
    sp.add_argument("file")
    sp.add_argument("--delay", type=int, default=0, help="ms between lines")
    sp.set_defaults(func=cmd_sendfile)

    sp = sub.add_parser("boot", help="resend the bootstrap sequence")
    sp.add_argument("file", nargs="?", default=None)
    sp.add_argument("--delay", type=int, default=0)
    sp.set_defaults(func=cmd_boot)

    sp = sub.add_parser("recv", help="show what Wrayth has sent us")
    sp.add_argument("-n", "--count", type=int, default=40)
    sp.set_defaults(func=cmd_recv)

    sp = sub.add_parser("status", help="server, client and window state")
    sp.set_defaults(func=cmd_status)

    sp = sub.add_parser("windows", help="list X windows (for --window)")
    sp.set_defaults(func=cmd_windows)

    sp = sub.add_parser("shot", help="screenshot the Wrayth window")
    sp.add_argument("out", nargs="?", default=None, help="output png (relative -> <dir>/shots)")
    sp.add_argument("--window", default=None, help="window id or title/class substring")
    sp.add_argument("--max-width", type=int, default=1400, help="downscale wider images")
    sp.add_argument("--full", action="store_true", help="no downscaling")
    sp.add_argument("--crop", default=None, metavar="WxH+X+Y", help="crop to this region first")
    sp.set_defaults(func=cmd_shot)

    sp = sub.add_parser("resize", help="resize the window, e.g. resize 1280x900")
    sp.add_argument("size")
    sp.add_argument("--pos-x", type=int, default=None, help="also move here")
    sp.add_argument("--pos-y", type=int, default=None)
    sp.add_argument("--window", default=None)
    sp.set_defaults(func=cmd_resize)

    sp = sub.add_parser("type", help="type text into the window (XTEST)")
    sp.add_argument("text", nargs="+")
    sp.add_argument("--window", default=None)
    sp.add_argument("--enter", action="store_true", help="press Return afterwards")
    sp.set_defaults(func=cmd_type)

    sp = sub.add_parser("key", help="press keys, e.g. Return ctrl+c alt+d")
    sp.add_argument("keys", nargs="+")
    sp.add_argument("--window", default=None)
    sp.set_defaults(func=cmd_key)

    sp = sub.add_parser("click", help="click at window-relative coordinates")
    sp.add_argument("x", type=int)
    sp.add_argument("y", type=int)
    sp.add_argument("--button", type=int, default=1)
    sp.add_argument("--absolute", action="store_true", help="coordinates are screen-relative")
    sp.add_argument("--window", default=None)
    sp.set_defaults(func=cmd_click)

    sp = sub.add_parser("stop", help="shut down the server and kill wine")
    sp.add_argument("--server-only", action="store_true")
    sp.add_argument("--wine-only", action="store_true")
    sp.set_defaults(func=cmd_stop)

    args = p.parse_args(argv)
    args.dir = args.dir.expanduser()
    args.func(args)


if __name__ == "__main__":
    main()
