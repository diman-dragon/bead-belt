#!/usr/bin/env python3
"""Делает скриншоты для Google Play из настоящей игры (нужны: pip install playwright && playwright install chromium).
Запуск:  python3 tools/screenshots.py [ru|en]
Результат: store/screenshots/<lang>/*.png — 1080x2160 (соотношение 2:1, допустимо для телефонов).
"""
import os, sys, threading, functools, http.server, socketserver
from playwright.sync_api import sync_playwright

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = sys.argv[1] if len(sys.argv) > 1 else "en"
OUT = os.path.join(ROOT, "store", "screenshots", LANG)
os.makedirs(OUT, exist_ok=True)

class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args, **kwargs):
        pass


handler = functools.partial(Quiet, directory=os.path.join(ROOT, "www"))
socketserver.TCPServer.allow_reuse_address = True
srv = socketserver.TCPServer(("127.0.0.1", 0), handler)
port = srv.server_address[1]
threading.Thread(target=srv.serve_forever, daemon=True).start()

with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 360, "height": 760}, device_scale_factor=3, has_touch=True,
                        locale="ru-RU" if LANG == "ru" else "en-US")
    page = ctx.new_page()
    page.goto(f"http://127.0.0.1:{port}/index.html")
    page.wait_for_timeout(500)
    page.click("#btnPlay")
    page.wait_for_timeout(1500)
    geo = page.evaluate("() => { const r=document.getElementById('cv').getBoundingClientRect(); return {l:r.left,t:r.top,w:r.width,h:r.height}; }")
    k = min(geo["w"] / 360, geo["h"] / 600)
    ox, oy = (geo["w"] - 360 * k) / 2, (geo["h"] - 600 * k) / 2

    def tap(ci):
        cx = [62, 180, 298][ci]
        page.mouse.click(geo["l"] + ox + cx * k, geo["t"] + oy + (54 + 43) * k)

    page.screenshot(path=os.path.join(OUT, "1-start.png"))
    tap(0)
    page.wait_for_timeout(900)
    tap(1)
    page.wait_for_timeout(1500)
    page.screenshot(path=os.path.join(OUT, "2-pouring.png"))
    page.wait_for_timeout(3500)
    page.screenshot(path=os.path.join(OUT, "3-belt-busy.png"))
    b.close()
srv.shutdown()
print("✓ скриншоты:", OUT)
