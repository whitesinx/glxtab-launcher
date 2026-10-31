#!/usr/bin/env python3
"""Convert Lucide (stroke-based, 24x24) SVG icons into Android vector drawables.

Usage: python3 scripts/lucide2vector.py <svg_dir> <res/drawable dir>
Each icon becomes ic_<name>.xml (dashes -> underscores). Strokes are white and
tinted at runtime by Compose, so one drawable works for every theme.
"""
import os, re, sys
import xml.etree.ElementTree as ET

def f(v): return float(v or 0)

def shape_to_path(el):
    tag = el.tag.split('}')[-1]
    a = el.attrib
    if tag == 'path':
        return a['d']
    if tag == 'circle':
        cx, cy, r = f(a.get('cx')), f(a.get('cy')), f(a.get('r'))
        return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 {-2*r},0"
    if tag == 'ellipse':
        cx, cy, rx, ry = f(a.get('cx')), f(a.get('cy')), f(a.get('rx')), f(a.get('ry'))
        return f"M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2*rx},0 a{rx},{ry} 0 1,0 {-2*rx},0"
    if tag == 'line':
        return f"M{f(a.get('x1'))},{f(a.get('y1'))} L{f(a.get('x2'))},{f(a.get('y2'))}"
    if tag in ('polyline', 'polygon'):
        pts = re.split(r'[\s,]+', a['points'].strip())
        pairs = [f"{pts[i]},{pts[i+1]}" for i in range(0, len(pts) - 1, 2)]
        return "M" + " L".join(pairs) + (" Z" if tag == 'polygon' else "")
    if tag == 'rect':
        x, y, w, h = f(a.get('x')), f(a.get('y')), f(a.get('width')), f(a.get('height'))
        rx = f(a.get('rx', a.get('ry')))
        ry = f(a.get('ry', a.get('rx')))
        if rx == 0:
            return f"M{x},{y} h{w} v{h} h{-w} Z"
        return (f"M{x + rx},{y} h{w - 2*rx} a{rx},{ry} 0 0,1 {rx},{ry} v{h - 2*ry} "
                f"a{rx},{ry} 0 0,1 {-rx},{ry} h{-(w - 2*rx)} a{rx},{ry} 0 0,1 {-rx},{-ry} "
                f"v{-(h - 2*ry)} a{rx},{ry} 0 0,1 {rx},{-ry} Z")
    return None

def convert(svg_path):
    root = ET.parse(svg_path).getroot()
    paths = [p for el in root.iter() if (p := shape_to_path(el))]
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated from Lucide (ISC license) by scripts/lucide2vector.py -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="24dp" android:height="24dp"',
           '    android:viewportWidth="24" android:viewportHeight="24">']
    for d in paths:
        out.append(f'    <path android:pathData="{d}" android:fillColor="#00000000" '
                   'android:strokeColor="#FFFFFFFF" android:strokeWidth="2" '
                   'android:strokeLineCap="round" android:strokeLineJoin="round" />')
    out.append('</vector>')
    return "\n".join(out) + "\n"

if __name__ == '__main__':
    src, dst = sys.argv[1], sys.argv[2]
    for name in sorted(os.listdir(src)):
        if name.endswith('.svg'):
            res = 'ic_' + name[:-4].replace('-', '_') + '.xml'
            with open(os.path.join(dst, res), 'w') as fh:
                fh.write(convert(os.path.join(src, name)))
            print(res)
