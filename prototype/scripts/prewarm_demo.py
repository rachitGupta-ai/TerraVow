"""Pre-warm the TerraVow response cache so the demo sample flow is instant.

Run this against the live site right before recording (the cache is in-memory on the
Cloud Run instance, so it clears if the instance recycles after a long idle).

    python scripts/prewarm_demo.py https://terravow-xxxx.a.run.app
    python scripts/prewarm_demo.py https://terravow-xxxx.a.run.app --explain-all

Warms: Analyze(before.jpg), Verify(after_same -> VERIFIED), Verify(after_different ->
rejected), and Explain. By default Explain is warmed at a few key slider days
(0, planned-window, break-even, 90); pass --explain-all to warm every day 0-90 so
Explain is instant wherever the slider is left (slower: ~90 live calls).

Requires only the Python standard library. It replays the exact bytes the UI sends,
so it only caches real Gemini output - nothing is faked.
"""
import sys, json, time, uuid, urllib.request

BASE = next((a for a in sys.argv[1:] if a.startswith("http")), "http://127.0.0.1:8080").rstrip("/")
EXPLAIN_ALL = "--explain-all" in sys.argv
MID = "B-2"


def _get(path, timeout=60):
    with urllib.request.urlopen(BASE + path, timeout=timeout) as r:
        return r.read()


def _post(path, fields=None, files=None, jsonbody=None, timeout=120):
    if jsonbody is not None:
        body, headers = json.dumps(jsonbody).encode(), {"Content-Type": "application/json"}
    elif files:
        b = "----p" + uuid.uuid4().hex
        body = b""
        for k, v in (fields or {}).items():
            body += f"--{b}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode()
        for k, (fn, data, ct) in files.items():
            body += (f"--{b}\r\nContent-Disposition: form-data; name=\"{k}\"; filename=\"{fn}\"\r\n"
                     f"Content-Type: {ct}\r\n\r\n").encode() + data + b"\r\n"
        body += f"--{b}--\r\n".encode()
        headers = {"Content-Type": f"multipart/form-data; boundary={b}"}
    else:
        body, headers = b"", {}
    req = urllib.request.Request(BASE + path, data=body, method="POST", headers=headers)
    t = time.perf_counter()
    with urllib.request.urlopen(req, timeout=timeout) as r:
        d = json.loads(r.read() or b"{}")
    return d, time.perf_counter() - t


def main():
    man = json.loads(_get("/static/samples/manifest.json"))
    img = {k: _get("/static/samples/" + man[k]["file"]) for k in man}
    notes = {k: man[k].get("notes", "") for k in man}

    def reset():
        _post("/api/demo/reset", timeout=30)

    def analyze():
        return _post("/api/analyze", {"machine_id": MID, "notes": notes["before"]},
                     {"image": ("before.jpg", img["before"], "image/jpeg")})

    def verify(key):
        return _post("/api/verify_improvement", {"machine_id": MID, "notes": notes[key]},
                     {"image": (man[key]["file"], img[key], "image/jpeg")})

    print(f"API: {BASE}")
    reset(); _, ta = analyze()
    m = json.loads(_get("/api/machine/" + MID))
    planned = min(int(m["planned_window_day"]), 90)
    breakeven = min(int(m.get("break_even_day") or planned), 90)
    _, te = _post("/api/explain", jsonbody={"machine_id": MID, "defer_days": planned})
    ds, tvs = verify("after_same")
    reset(); analyze(); dd, tvd = verify("after_different")
    print(f"  analyze={ta:.2f}s  explain(day {planned})={te:.2f}s  "
          f"verify_same={tvs:.2f}s ({ds['status']})  verify_diff={tvd:.2f}s ({dd['status']})")

    days = range(0, 91) if EXPLAIN_ALL else sorted({0, planned, breakeven, 90})
    print(f"warming explain for {len(list(days))} slider day(s){' (all)' if EXPLAIN_ALL else ''} ...")
    reset(); analyze()
    slow = 0
    for day in days:
        _, dt = _post("/api/explain", jsonbody={"machine_id": MID, "defer_days": day})
        slow += dt > 1.0
        time.sleep(0.4)  # gentle: avoid overloading the instance
    reset()
    print(f"done. {slow} explain day(s) were cold and are now cached.")


if __name__ == "__main__":
    main()
