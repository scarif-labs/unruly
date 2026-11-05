"""Writes the logo drawables into app/src/main/res from mark.py."""
import os, pathops
from mark import *  # mark, svgd, bounds, the font
RES=os.path.join(ROOT, 'app/src/main/res')
RED='#E3191B'; INK='#111111'
W,H=xmax-xmin,ymax-ymin
def placed(box, cx, cy):
    s=box/max(W,H)
    # optical centring: the kick hangs low, so lift the letter slightly
    return svgd(mark, s, cx-W*s/2, cy-H*s/2-box*0.03)
def vector(size, viewport, paths, extra=''):
    body=''.join(f'\n    <path android:fillColor="{c}" android:pathData="{d}" />' for c,d in paths)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from Inter Black's "u" plus the kick (logo direction L2-D). Don't hand-edit;
     regenerate with tools/logo/assets.py (see tools/logo/README.md). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{size}dp"{extra}
    android:viewportWidth="{viewport}"
    android:viewportHeight="{viewport}">{body}
</vector>
'''
# Adaptive icon: 108 viewport, safe circle radius 33 around centre; mark box 44.
open(f'{RES}/drawable/ic_launcher_foreground.xml','w').write(vector(108,108,[(INK,placed(44,54,54))]))
open(f'{RES}/drawable/ic_launcher_background.xml','w').write(vector(108,108,[(RED,'M0,0h108v108H0z')]))
# Splash icon (Android 12+): 288 viewport for 240dp... use the documented 240dp with a 160dp circle.
open(f'{RES}/drawable/ic_splash.xml','w').write(vector(240,240,[(INK,placed(104,120,120))]))
# Notification / widget / shortcut glyph: white silhouette in 24.
open(f'{RES}/drawable/ic_unruly_24.xml','w').write(vector(24,24,[('@android:color/white',placed(18,12,12.3))]))
# Wordmark for the splash branding slot (200x80dp): "unruly" in Inter Black, tracked -0.04em.
from fontTools.pens.transformPen import TransformPen
word=pathops.Path(); x=0; cmap=f.getBestCmap(); upm=f['head'].unitsPerEm
for ch in 'unruly':
    g=gs[cmap[ord(ch)]]; pen=TransformPen(word.getPen(), (1,0,0,1,x,0)); g.draw(pen); x+=g.width-0.04*upm
word.simplify()
bx0,by0,bx1,by1=word.bounds
ww,wh=bx1-bx0,by1-by0
s=150/ww
def wd(path):
    out=[]
    T=lambda p: f"{25+(p[0]-bx0)*s:.2f},{40-wh*s/2+(by1-p[1])*s:.2f}"
    for verb,P in path:
        if verb==pathops.PathVerb.MOVE: out.append("M"+T(P[0]))
        elif verb==pathops.PathVerb.LINE: out.append("L"+T(P[0]))
        elif verb==pathops.PathVerb.CUBIC: out.append("C"+" ".join(T(p) for p in P))
        elif verb==pathops.PathVerb.QUAD: out.append("Q"+" ".join(T(p) for p in P))
        elif verb==pathops.PathVerb.CLOSE: out.append("Z")
    return "".join(out)
v=vector(200,200,[(INK,wd(word))]).replace('android:height="200dp"','android:height="80dp"').replace('android:viewportHeight="200"','android:viewportHeight="80"')
open(f'{RES}/drawable/ic_splash_wordmark.xml','w').write(v)
print("wrote launcher, splash, wordmark and notification drawables")
