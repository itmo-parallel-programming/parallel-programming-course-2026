#!/usr/bin/env python3
"""Строит SVG-график «млн оп/сек от числа потоков» из throughput.csv без внешних зависимостей."""
import csv
import math
import sys
from collections import defaultdict

TITLES = {
    "single-thread": "Этап 0: однопоточный baseline",
    "empty-lock": "Этап 1: пустой лок",
    "synchronized": "Этап 1: единый лок",
    "striped-lock": "Этап 2: 16 шардов",
    "thread-local": "Этап 3: thread-local",
    "double-buffer": "Этап 4: двойная буферизация",
}
COLORS = ["#7f7f7f", "#d62728", "#ff7f0e", "#9467bd", "#2ca02c", "#1f77b4"]

W, H = 860, 620
LEFT, RIGHT, TOP, BOTTOM = 70, 30, 50, 150
# Лог-шкала: иначе кривые локов (единицы-десятки) сливаются с нулём рядом с thread-local (~1000).
TICKS = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000]


def main(src, dst):
    series = defaultdict(list)
    with open(src) as f:
        for row in csv.DictReader(f):
            series[row["collector"]].append((int(row["threads"]), float(row["mops"])))

    threads = sorted({t for pts in series.values() for t, _ in pts})
    all_m = [m for pts in series.values() for _, m in pts]
    ticks = [t for t in TICKS if min(all_m) / 2 <= t <= max(all_m) * 2]
    lo, hi = math.log10(ticks[0]), math.log10(ticks[-1])

    pw, ph = W - LEFT - RIGHT, H - TOP - BOTTOM
    # Ось X категориальная: точки 1, 2, 4, 8, ... на равных расстояниях.
    xs = {t: LEFT + (i + 0.5) * pw / len(threads) for i, t in enumerate(threads)}
    def y(v):
        return TOP + ph - (math.log10(v) - lo) / (hi - lo) * ph

    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
        'font-family="Helvetica, Arial, sans-serif" font-size="13">',
        f'<rect width="{W}" height="{H}" fill="white"/>',
        f'<text x="{LEFT}" y="28" font-size="16" font-weight="bold">Пропускная способность record(), млн оп/сек (лог. шкала)</text>',
    ]
    for v in ticks:
        yy = y(v)
        out.append(f'<line x1="{LEFT}" x2="{LEFT + pw}" y1="{yy:.1f}" y2="{yy:.1f}" stroke="#e5e5e5"/>')
        out.append(f'<text x="{LEFT - 8}" y="{yy + 4:.1f}" text-anchor="end" fill="#555">{v:g}</text>')
    for t, xx in xs.items():
        out.append(f'<text x="{xx:.1f}" y="{TOP + ph + 22}" text-anchor="middle" fill="#555">{t}</text>')
    out.append(f'<text x="{LEFT + pw / 2}" y="{TOP + ph + 46}" text-anchor="middle">Число потоков T</text>')
    out.append(f'<line x1="{LEFT}" x2="{LEFT + pw}" y1="{TOP + ph}" y2="{TOP + ph}" stroke="#333"/>')

    for i, name in enumerate(n for n in TITLES if n in series):
        color = COLORS[i % len(COLORS)]
        pts = sorted(series[name])
        path = " ".join(f"{xs[t]:.1f},{y(m):.1f}" for t, m in pts)
        if len(pts) > 1:
            out.append(f'<polyline points="{path}" fill="none" stroke="{color}" stroke-width="2.5"/>')
        for t, m in pts:
            out.append(f'<circle cx="{xs[t]:.1f}" cy="{y(m):.1f}" r="4" fill="{color}"><title>{m:.1f}</title></circle>')
        # Легенда под графиком в две колонки.
        ly = TOP + ph + 85 + (i // 2) * 24
        lx = LEFT + (i % 2) * 360
        out.append(f'<line x1="{lx}" x2="{lx + 24}" y1="{ly}" y2="{ly}" stroke="{color}" stroke-width="3"/>')
        out.append(f'<text x="{lx + 32}" y="{ly + 4}">{TITLES[name]}</text>')

    out.append("</svg>")
    with open(dst, "w") as f:
        f.write("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
