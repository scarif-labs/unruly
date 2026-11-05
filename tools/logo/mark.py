"""The Unruly mark: Inter Black's "u" with its right stem kicked out (logo direction L2-D).

Imported by assets.py; needs fonttools and skia-pathops."""
import math, os, pathops
ROOT=os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
from fontTools.ttLib import TTFont
from fontTools.pens.recordingPen import RecordingPen
f=TTFont(os.path.join(ROOT, 'app/src/main/res/font/inter_black.otf'))
gs=f.getGlyphSet()
def glyph_path(ch):
    p=pathops.Path(); pen=p.getPen(); gs[f.getBestCmap()[ord(ch)]].draw(pen); return p
def flat(path, n=24):
    pts=[]
    for verb, P in path:
        if verb==pathops.PathVerb.MOVE: cur=P[0]; pts.append([cur])
        elif verb==pathops.PathVerb.LINE: cur=P[0]; pts[-1].append(cur)
        elif verb==pathops.PathVerb.CUBIC:
            a=cur;b,c,d=P
            for i in range(1,n+1):
                t=i/n;mt=1-t
                pts[-1].append((mt**3*a[0]+3*mt*mt*t*b[0]+3*mt*t*t*c[0]+t**3*d[0],mt**3*a[1]+3*mt*mt*t*b[1]+3*mt*t*t*c[1]+t**3*d[1]))
            cur=d
        elif verb==pathops.PathVerb.QUAD:
            a=cur;b,d=P
            for i in range(1,n+1):
                t=i/n;mt=1-t
                pts[-1].append((mt*mt*a[0]+2*mt*t*b[0]+t*t*d[0],mt*mt*a[1]+2*mt*t*b[1]+t*t*d[1]))
            cur=d
    return pts
u=glyph_path('u')
# right stem edges at mid height
def xs_at(y):
    out=[]
    for poly in flat(u):
        for (x1,y1),(x2,y2) in zip(poly,poly[1:]+poly[:1]):
            if (y1-y)*(y2-y)<0: out.append(x1+(y-y1)*(x2-x1)/(y2-y1))
    return sorted(out)
xs=xs_at(600); inner, outer = xs[-2], xs[-1]
w = outer-inner
# Kick: a stroke the stem's width, continuing the stem down past the baseline and bending
# out to the lower right, ending in a square cut perpendicular to its direction.
cx=(inner+outer)/2
def bez(t,P):
    mt=1-t; return tuple(mt**3*P[0][i]+3*mt*mt*t*P[1][i]+3*mt*t*t*P[2][i]+t**3*P[3][i] for i in range(2))
def dbez(t,P):
    mt=1-t; return tuple(3*mt*mt*(P[1][i]-P[0][i])+6*mt*t*(P[2][i]-P[1][i])+3*t*t*(P[3][i]-P[2][i]) for i in range(2))
# The kick, measured off the D concept and scaled to Inter's u: the outer edge stays straight
# until just above the baseline, then sweeps out; the underside leaves the bowl and runs to an
# angled cut. Drawn as one closed shape and unioned with the letter.
k = 1.0  # kick length; 1.45 was tried and kinks the outer edge
tail=pathops.Path(); pen=tail.getPen()
pen.moveTo((800,1118)); pen.lineTo((outer,1118)); pen.lineTo((outer,303))
pen.curveTo((outer,150),(outer+102*k,60),(outer+244*k,-17*k))
pen.lineTo((outer+54*k,-269*k))
pen.curveTo((outer-178*k,-240*k),(860,-120*k),(800,150))
pen.closePath()
mark=pathops.op(u, tail, pathops.PathOp.UNION)
mark.simplify()
xmin,ymin,xmax,ymax=mark.bounds
def svgd(path, scale, ox, oy):
    # font y-up -> svg y-down
    out=[]
    T=lambda p: f"{ox+(p[0]-xmin)*scale:.2f},{oy+(ymax-p[1])*scale:.2f}"
    for verb,P in path:
        if verb==pathops.PathVerb.MOVE: out.append("M"+T(P[0]))
        elif verb==pathops.PathVerb.LINE: out.append("L"+T(P[0]))
        elif verb==pathops.PathVerb.CUBIC: out.append("C"+" ".join(T(p) for p in P))
        elif verb==pathops.PathVerb.QUAD: out.append("Q"+" ".join(T(p) for p in P))
        elif verb==pathops.PathVerb.CLOSE: out.append("Z")
    return "".join(out)
import json
W,H=xmax-xmin,ymax-ymin

