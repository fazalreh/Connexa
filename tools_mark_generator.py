import math

# The C opens to the right: sweep counter-clockwise from 55 deg (top right) through
# 180 (left) to 305 (bottom right), leaving the mouth across the 3 o'clock position.
START, END, COUNT = 55.0, 305.0, 6

def build(viewport, safe_radius, ring_frac=0.60, node_frac=0.130, accent=1.34, stroke_frac=0.055):
    c = viewport / 2.0
    ring = safe_radius * ring_frac
    node = safe_radius * node_frac
    stroke = safe_radius * stroke_frac
    step = (END - START) / (COUNT - 1)
    pts = []
    for i in range(COUNT):
        a = math.radians(START + i * step)
        r = node * (accent if i == COUNT - 1 else 1.0)
        pts.append((c + ring * math.cos(a), c - ring * math.sin(a), r))
    worst = max(math.hypot(x - c, y - c) + r for x, y, r in pts)
    # Arc runs between the node centres, so the stroke tucks under both end nodes.
    x0, y0, _ = pts[0]
    x1, y1, _ = pts[-1]
    arc = f"M{x0:.1f},{y0:.1f} A{ring:.1f},{ring:.1f} 0 1,0 {x1:.1f},{y1:.1f}"
    return c, ring, node, stroke, pts, arc, worst

def circle(x, y, r):
    return f"M{x:.1f},{y:.1f}m-{r:.1f},0a{r:.1f},{r:.1f} 0 1,0 {2*r:.1f},0a{r:.1f},{r:.1f} 0 1,0 -{2*r:.1f},0"

def render(viewport, safe, colour, note):
    c, ring, node, stroke, pts, arc, worst = build(viewport, safe)
    out = [f'<?xml version="1.0" encoding="utf-8"?>', note,
           f'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           f'    android:width="{viewport}dp"', f'    android:height="{viewport}dp"',
           f'    android:viewportWidth="{viewport}"', f'    android:viewportHeight="{viewport}">', '']
    out += ['    <!-- The arc is drawn first so the nodes sit over its ends. -->', '    <path',
            f'        android:strokeColor="{colour}"', f'        android:strokeWidth="{stroke:.1f}"',
            '        android:strokeAlpha="0.45"', '        android:strokeLineCap="round"',
            '        android:fillColor="@android:color/transparent"',
            f'        android:pathData="{arc}" />', '']
    for i, (x, y, r) in enumerate(pts):
        if i == len(pts) - 1:
            out += ['', '    <!-- The accent node gives the ring a direction rather than',
                    '         leaving it a symmetrical horseshoe. -->']
        out += [f'    <path', f'        android:fillColor="{colour}"',
                f'        android:pathData="{circle(x, y, r)}" />']
    out += ['</vector>', '']
    return "\n".join(out), worst, safe

LAUNCHER_NOTE = """<!--
    Connexa mark: six nodes on an arc that opens to the right, reading as a C and as
    a gathering at the same time.

    The geometry is computed rather than placed by hand - the nodes are evenly spaced
    by angle on a single ring, which is what makes the shape read as constructed. Every
    dimension derives from the safe radius, so the same design regenerates at any size
    with its proportions intact.

    Adaptive icons are 108dp with only the centre 72dp guaranteed visible, so the
    artwork stays within 36 of the centre at 54,54.
-->"""

SPLASH_NOTE = """<!--
    The Connexa mark for the launch screen: the same six-node arc as the launcher icon,
    so the icon the user tapped and the mark that greets them are one thing.

    The system masks this slot to a circle of radius 96 about the centre at 144,144.
    Every node's distance from the centre plus its own radius must stay inside that -
    an earlier version overflowed and the mask cut a node into a wedge.

    Drawn opaque white: a launch screen appears before any theme attribute resolves.
-->"""

for path, vp, safe, colour, note in (
        ("ic_launcher_foreground.xml", 108, 36, "#FFFFFF", LAUNCHER_NOTE),
        ("ic_splash_mark.xml", 288, 96, "#FFFFFF", SPLASH_NOTE)):
    xml, worst, s = render(vp, safe, colour, note)
    open(path, "w").write(xml)
    print(f"{path}: worst extent {worst:.1f} of {s} -> {'FITS' if worst <= s else 'CLIPPED'}")
