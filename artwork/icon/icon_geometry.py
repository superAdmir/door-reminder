# Shared icon geometry in the 108x108 adaptive-icon coordinate space.
BG_TOP = "#3A6FC4"; BG_BOTTOM = "#1D4488"; KNOB = "#2F5DA8"; BADGE = "#1E8A4C"
DOOR = "M32,78 L32,42 A14,14 0 0 1 60,42 L60,78 Z"
# Inner door panel recess (drawn in soft blue tint on the white door)
PANEL = "M36.8,73.5 L36.8,42.8 A9.2,9.2 0 0 1 55.2,42.8 L55.2,73.5 Z"
SILL = "M31,77 L69,77 L69,80.5 L31,80.5 Z"
KNOB_C = (40.6, 58.5, 2.3)
BADGE_C = (62, 66, 12)
LOCK_BODY = "M57.9,63.6 L66.1,63.6 A1.5,1.5 0 0 1 67.6,65.1 L67.6,71.1 A1.5,1.5 0 0 1 66.1,72.6 L57.9,72.6 A1.5,1.5 0 0 1 56.4,71.1 L56.4,65.1 A1.5,1.5 0 0 1 57.9,63.6 Z"
SHACKLE = "M58.6,63.6 L58.6,60.9 A3.4,3.4 0 0 1 65.4,60.9 L65.4,63.6"
def circle(cx, cy, r):
    return f"M{cx-r},{cy} A{r},{r} 0 1 0 {cx+r},{cy} A{r},{r} 0 1 0 {cx-r},{cy} Z"
def svg(size, view="0 0 108 108", shape=None, transparent_bg=False):
    # Clip shapes are expressed in viewBox coordinates so they also apply to zoomed views
    # (e.g. "18 18 72 72" used for the legacy launcher rasters).
    vx, vy, vw, vh = (float(v) for v in view.split())
    clip = ""
    if shape == "round":
        clip = f'<clipPath id="c"><circle cx="{vx + vw / 2}" cy="{vy + vh / 2}" r="{vw / 2}"/></clipPath>'
    if shape == "squircle":
        clip = f'<clipPath id="c"><rect x="{vx}" y="{vy}" width="{vw}" height="{vh}" rx="{vw * 0.1:.2f}"/></clipPath>'
    cp = ' clip-path="url(#c)"' if clip else ""
    bg = "" if transparent_bg else '<rect width="108" height="108" fill="url(#g)"/>'
    kx, ky, kr = KNOB_C; bx, by, br = BADGE_C
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{view}">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{BG_TOP}"/><stop offset="1" stop-color="{BG_BOTTOM}"/></linearGradient>{clip}</defs>
<g{cp}>{bg}
<path d="{DOOR}" fill="#FFFFFF"/>
<path d="{PANEL}" fill="#DCE6F7"/>
<path d="{circle(kx,ky,kr)}" fill="{KNOB}"/>
<path d="{circle(bx,by,br+2.6)}" fill="#FFFFFF"/>
<path d="{circle(bx,by,br)}" fill="{BADGE}"/>
<path d="{LOCK_BODY}" fill="#FFFFFF"/>
<path d="{SHACKLE}" fill="none" stroke="#FFFFFF" stroke-width="2.3" stroke-linecap="round"/>
<path d="{circle(62,67.8,1.25)}" fill="{BADGE}"/>
</g></svg>'''
