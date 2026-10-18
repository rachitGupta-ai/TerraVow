#!/usr/bin/env python3
"""Generate the TerraVow hackathon pitch deck (16:9 .pptx) and docs/deck/deck-outline.md.

Run:  python3 docs/deck/build_deck.py          (needs python-pptx and Pillow)
Output: docs/deck/TerraVow-Hackathon-Deck.pptx, docs/deck/deck-outline.md

Notes
- Every external statistic is listed in STATS below with its URL; the same URLs are written
  into the speaker notes of the slide that uses them.
- Prototype numbers (health, lifespan, pollution, payback) come from the deterministic engine run
  against the synthetic demo fleet; they are ILLUSTRATIVE and labelled as such on the slides.
- Images in docs/deck/assets/ are screenshots of the local prototype (Gemini not configured, so the
  fallback path is shown) and Mermaid diagrams rendered from docs/*.md.
"""
import json
import math
import os
from pathlib import Path

from lxml import etree
from PIL import Image, ImageFont
from pptx import Presentation
from pptx.chart.data import CategoryChartData
from pptx.dml.color import RGBColor
from pptx.enum.chart import XL_CHART_TYPE, XL_LABEL_POSITION, XL_LEGEND_POSITION
from pptx.enum.shapes import MSO_CONNECTOR, MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.oxml.ns import qn
from pptx.util import Emu, Inches, Pt

HERE = Path(__file__).resolve().parent
ASSETS = HERE / "assets"
REPO = HERE.parent.parent
OUT_PPTX = HERE / "TerraVow-Hackathon-Deck.pptx"
OUT_MD = HERE / "deck-outline.md"

# ----------------------------------------------------------------------------- theme
BG = "0B0F14"
PANEL = "111820"
PANEL2 = "16212B"
BORDER = "2A3F52"
TEXT = "F0F4F8"
MUTED = "9FB3C8"
GREEN = "3DDC97"
BLUE = "4DA3F0"
AMBER = "F2B84B"
RED = "FF7A7A"
DKGREEN = "0F2E18"
DKBLUE = "0D2A4A"
DKAMBER = "3A2A0A"
DKRED = "3A1115"
GREY = "5B6B7B"
FONT = "Calibri"
N0, BL, GR, RD, AM = 'n0', 'bl', 'gr', 'rd', 'am'  # lane step kinds

W, H = 13.333, 7.5
MX = 0.6  # side margin

_ARIAL = "/System/Library/Fonts/Supplemental/Arial.ttf"
_ARIAL_B = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
_font_cache = {}
WARNINGS = []


def rgb(h):
    return RGBColor.from_string(h)


def _font(size, bold):
    key = (round(size * 4), bold)
    if key not in _font_cache:
        path = _ARIAL_B if bold else _ARIAL
        _font_cache[key] = ImageFont.truetype(path, max(1, int(round(size * 4))))
    return _font_cache[key]


def _text_w(txt, size, bold):
    return _font(size, bold).getlength(txt) / 4.0  # points


def est_lines(txt, size, bold, width_pt):
    """Conservative wrapped-line estimate (Arial is wider than Calibri)."""
    lines = 0
    for para in txt.split("\n"):
        words = para.split(" ")
        cur = 0.0
        n = 1
        space = _text_w(" ", size, bold)
        for w_ in words:
            ww = _text_w(w_, size, bold)
            if cur == 0:
                cur = ww
            elif cur + space + ww <= width_pt:
                cur += space + ww
            else:
                n += 1
                cur = ww
        lines += n
    return lines


# ----------------------------------------------------------------------------- primitives
class Deck:
    def __init__(self):
        self.prs = Presentation()
        self.prs.slide_width = Inches(W)
        self.prs.slide_height = Inches(H)
        self.meta = []
        self.cur = None

    def new(self, title, kicker="", notes="", sources=None, visual="", footer="", appendix=False, number=True):
        s = self.prs.slides.add_slide(self.prs.slide_layouts[6])
        s.background.fill.solid()
        s.background.fill.fore_color.rgb = rgb(BG)
        n = len(self.prs.slides)
        self.cur = n
        self.meta.append(dict(n=n, title=title, kicker=kicker, notes=notes, sources=sources or [],
                              visual=visual, appendix=appendix))
        s.notes_slide.notes_text_frame.text = notes + (
            ("\n\nSources:\n" + "\n".join(sources)) if sources else "")
        if title:
            add_text(s, MX, 0.62, 12.1, 1.0, title, size=28, bold=True, anchor="t")
        if kicker:
            add_text(s, MX, 0.3, 8, 0.32, kicker.upper(), size=12, bold=True, color=GREEN)
        if number:
            add_text(s, W - MX - 3.5, 0.3, 3.5, 0.32, "TerraVow", size=12, bold=True, color=GREEN, align="r")
        if footer:
            add_text(s, MX, 7.0, 11.3, 0.4, footer, size=9, color=MUTED, anchor="t")
        if number:
            add_text(s, W - MX - 1.0, 7.0, 1.0, 0.3, str(n), size=10, color=MUTED, align="r")
        return s


def _apply_run(r, size, color, bold, italic=False, font=FONT):
    r.font.size = Pt(size)
    r.font.bold = bold
    r.font.italic = italic
    r.font.name = font
    r.font.color.rgb = rgb(color)


def add_text(slide, x, y, w, h, paras, size=14, color=TEXT, bold=False, align="l", anchor="t",
             shape=None, inset=0.05, line_sp=None, italic=False, check=True):
    """Add a text box (or write into `shape`). paras: str | list[str | dict | list[run]]."""
    if shape is None:
        tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    else:
        tb = shape
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = Inches(inset)
    tf.margin_top = tf.margin_bottom = Inches(0.03)
    tf.vertical_anchor = {"t": MSO_ANCHOR.TOP, "m": MSO_ANCHOR.MIDDLE, "b": MSO_ANCHOR.BOTTOM}[anchor]
    if isinstance(paras, str):
        paras = [paras]
    first = True
    total_h = 0.0
    width_pt = (w - 2 * inset) * 72
    for p in paras:
        d = p if isinstance(p, dict) else {"t": p}
        runs = d["t"]
        if isinstance(runs, str):
            runs = [(runs, {})]
        psize = d.get("size", size)
        pcolor = d.get("color", color)
        pbold = d.get("bold", bold)
        palign = d.get("align", align)
        para = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        para.alignment = {"l": PP_ALIGN.LEFT, "c": PP_ALIGN.CENTER, "r": PP_ALIGN.RIGHT}[palign]
        if line_sp or d.get("line_sp"):
            para.line_spacing = d.get("line_sp", line_sp)
        sa = d.get("space_after", 0)
        para.space_after = Pt(sa)
        plain = ""
        anybold = pbold
        maxsize = psize
        for rt, ro in runs:
            r = para.add_run()
            r.text = rt
            rs = ro.get("size", psize)
            _apply_run(r, rs, ro.get("color", pcolor), ro.get("bold", pbold), ro.get("italic", italic))
            plain += rt
            anybold = anybold or ro.get("bold", False)
            maxsize = max(maxsize, rs)
        nl = est_lines(plain, maxsize, anybold, width_pt)
        total_h += nl * maxsize * 1.2 * (d.get("line_sp", line_sp) or 1.0) + sa
    if check:
        avail = h * 72 - 6
        if shape is None or True:
            if total_h > avail + 1:
                WARNINGS.append(f"slide? text may overflow ({total_h:.0f}pt > {avail:.0f}pt): {str(paras)[:70]}")
    return tb


def rect(slide, x, y, w, h, fill=PANEL, line=BORDER, lw=1.0, radius=0.06, shape=MSO_SHAPE.ROUNDED_RECTANGLE,
         dash=False):
    sp = slide.shapes.add_shape(shape, Inches(x), Inches(y), Inches(w), Inches(h))
    if shape == MSO_SHAPE.ROUNDED_RECTANGLE:
        sp.adjustments[0] = min(0.5, radius / max(0.01, min(w, h)))
    if fill is None:
        sp.fill.background()
    else:
        sp.fill.solid()
        sp.fill.fore_color.rgb = rgb(fill)
    if line is None:
        sp.line.fill.background()
    else:
        sp.line.color.rgb = rgb(line)
        sp.line.width = Pt(lw)
        if dash:
            ln = sp.line._get_or_add_ln()
            pd = etree.SubElement(ln, qn("a:prstDash"))
            pd.set("val", "dash")
    sp.shadow.inherit = False
    return sp


def set_alpha(shape, pct):
    sf = shape.fill._xPr.find(qn("a:solidFill"))
    if sf is not None:
        a = etree.SubElement(sf[0], qn("a:alpha"))
        a.set("val", str(int(pct * 1000)))


def card(slide, x, y, w, h, paras, fill=PANEL, line=BORDER, size=14, color=TEXT, bold=False, align="l",
         anchor="m", radius=0.08, lw=1.0, dash=False, inset=0.12):
    sp = rect(slide, x, y, w, h, fill=fill, line=line, lw=lw, radius=radius, dash=dash)
    add_text(slide, x, y, w, h, paras, size=size, color=color, bold=bold, align=align, anchor=anchor,
             shape=sp, inset=inset)
    return sp


def chip(slide, x, y, w, text, fill=DKGREEN, line=GREEN, color=GREEN, size=11, h=0.3, bold=True):
    return card(slide, x, y, w, h, text, fill=fill, line=line, size=size, color=color, bold=bold, align="c",
                anchor="m", radius=h / 2, inset=0.04)


def arrow(slide, x1, y1, x2, y2, color=MUTED, w=1.75, head=True, dash=False):
    c = slide.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    c.line.color.rgb = rgb(color)
    c.line.width = Pt(w)
    ln = c.line._get_or_add_ln()
    if dash:
        pd = etree.SubElement(ln, qn("a:prstDash"))
        pd.set("val", "dash")
    if head:
        te = etree.SubElement(ln, qn("a:tailEnd"))
        te.set("type", "triangle")
    return c


def numdot(slide, x, y, n, fill=GREEN, color="04210F", d=0.38, size=14):
    return card(slide, x, y, d, d, str(n), fill=fill, line=None, size=size, color=color, bold=True, align="c",
                anchor="m", radius=d / 2, inset=0.0)


def picture_fit(slide, path, x, y, w, h, frame=True, pad=0.06):
    im = Image.open(path)
    iw, ih = im.size
    if frame:
        rect(slide, x, y, w, h, fill=PANEL, line=BORDER, radius=0.06)
        x, y, w, h = x + pad, y + pad, w - 2 * pad, h - 2 * pad
    sc = min(w / iw, h / ih)
    pw, ph = iw * sc, ih * sc
    px, py = x + (w - pw) / 2, y + (h - ph) / 2
    slide.shapes.add_picture(str(path), Inches(px), Inches(py), Inches(pw), Inches(ph))
    return px, py, pw, ph


def chart_clear_bg(chart):
    cs = chart._chartSpace
    sp = cs.find(qn("c:spPr"))
    if sp is None:
        sp = etree.SubElement(cs, qn("c:spPr"))
        # spPr must come before txPr; move it
        txpr = cs.find(qn("c:txPr"))
        if txpr is not None:
            txpr.addprevious(sp)
    for ch in list(sp):
        sp.remove(ch)
    etree.SubElement(sp, qn("a:noFill"))
    ln = etree.SubElement(sp, qn("a:ln"))
    etree.SubElement(ln, qn("a:noFill"))


def style_axis(ax, size=11, color=MUTED, grid=False, line=True):
    ax.tick_labels.font.size = Pt(size)
    ax.tick_labels.font.color.rgb = rgb(color)
    ax.tick_labels.font.name = FONT
    ax.has_major_gridlines = grid
    if grid:
        ax.major_gridlines.format.line.color.rgb = rgb("22323F")
        ax.major_gridlines.format.line.width = Pt(0.75)
    if line:
        ax.format.line.color.rgb = rgb("3A4F61")
    else:
        ax.format.line.fill.background()


def grid_rows(slide, x, y, col_w, row_h, rows, header=True, size=12, header_fill=PANEL2, zebra=True,
              aligns=None, bold_first=False):
    cy = y
    for ri, row in enumerate(rows):
        cx = x
        is_h = header and ri == 0
        for ci, cell in enumerate(row):
            fill = header_fill if is_h else (PANEL if (ri % 2 == 1 or not zebra) else "0E141B")
            col = GREEN if is_h else TEXT
            al = (aligns[ci] if aligns else "l")
            card(slide, cx, cy, col_w[ci], row_h, cell, fill=fill, line=BORDER, size=size, color=col,
                 bold=is_h or (bold_first and ci == 0), align=al, anchor="m", radius=0.0, lw=0.5, inset=0.08)
            cx += col_w[ci]
        cy += row_h
    return cy


# ----------------------------------------------------------------------------- facts
STATS = [
    dict(id="S1", stat="Manufacturing value added is about 14.9% of world GDP (14.87% in 2025, 14.89% in 2024)",
         src="World Bank, World Development Indicators NV.IND.MANF.ZS",
         url="https://data.worldbank.org/indicator/NV.IND.MANF.ZS",
         api="https://api.worldbank.org/v2/country/WLD/indicator/NV.IND.MANF.ZS?format=json", slide="2"),
    dict(id="S2", stat="Industry was 24% (14 GtCO2-eq) of 2019 net global anthropogenic GHG emissions; 34% when electricity and heat "
                       "emissions are attributed to the sectors that use them. Energy supply 34%, AFOLU 22%, transport 15%, buildings 6%.",
         src="IPCC AR6 WGIII Summary for Policymakers, statement B.2.1",
         url="https://www.ipcc.ch/report/ar6/wg3/chapter/summary-for-policymakers/", slide="2"),
    dict(id="S3", stat="India: manufacturing contributes about 16-17% of GDP",
         src="PIB, BHAVYA explainer, 14 Oct 2026",
         url="https://www.pib.gov.in/PressNoteDetails.aspx?ModuleId=3&NoteId=160457&id=160457", slide="2"),
    dict(id="S4", stat="India MSMEs: about 31.1% of GDP, 48.58% of exports, about 35.4% of manufacturing output, more than 7.47 crore "
                       "enterprises (across manufacturing, services and trade)",
         src="PIB backgrounder, 14 May 2026",
         url="https://static.pib.gov.in/WriteReadData/specificdocs/documents/2026/may/doc2026514869801.pdf",
         slide="2, 17"),
    dict(id="S5", stat="Operations and maintenance programs targeting energy efficiency are estimated to save 5% to 20% on energy bills "
                       "without significant capital investment (US federal sites guide)",
         src="US DOE FEMP, Operations & Maintenance Best Practices Guide, Release 3.0",
         url="https://www1.eere.energy.gov/femp/pdfs/OandM.pdf", slide="4"),
    dict(id="S6", stat="CPCB has identified 17 categories of highly polluting industry (incl. dyes and dye-intermediates, fertiliser, iron and steel, "
                       "cement); 4,578 highly polluting units reported by SPCBs/PCCs (as of the Dec 2022 answer)",
         src="Rajya Sabha Starred Question 101, 15 Dec 2022 (MoEFCC)",
         url="https://sansad.in/getFile/annex/258/AS101.pdf?source=pqars", slide="7, 17"),
]
URL = {s["id"]: s["url"] for s in STATS}

# Prototype figures: deterministic engine run on the synthetic demo fleet (local run, anchor date 2026-10-17/18).
K4_MONTHS = ["Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct"]
K4_HEALTH = [66.8, 82.7, 82.3, 79.4, 79.1, 78.7, 65.8, 71.4, 71.1, 57.2, 43.3, 28.4]
K4_EFFLUENT = [841.98, 824.57, 824.9, 827.35, 827.67, 828.0, 843.25, 835.82, 836.32, 854.92, 880.83, 918.44]
K4_CO2E_BASE, K4_CO2E_NOW = 43200.0, 45549.61
K4_EFF_BASE, K4_EFF_NOW = 800.0, 918.44
PAYBACK = [  # (option, cash payback yrs MSME-adjusted, incl. exposure proxy yrs)  -- demo-synthetic MSME subsidy
    ("Dust suppression + closed-circuit grinding", 5.67, 3.39),
    ("High-efficiency kiln, heat recovery", 15.10, 10.41),
    ("Remanufacture worn components", 22.18, 11.88),
    ("Electric / hybrid drive", 28.78, 12.92),
]

INDUSTRIES = [
    ("Textile dyeing & finishing", ["air", "water", "chemical"]),
    ("Metals: steel & foundry", ["air", "water", "soil"]),
    ("Chemicals & fertiliser", ["air", "water", "chemical", "soil"]),
    ("Mining & mineral processing", ["air", "water", "soil"]),
]
DOMAIN_COL = {"air": (DKBLUE, BLUE), "water": ("0B2F3A", "35C6D6"), "chemical": (DKAMBER, AMBER),
              "soil": ("2E2113", "C58A4B")}


def catalogue_examples():
    try:
        d = json.load(open(REPO / "prototype/data/catalogue.json"))
        ents = d["entries"] if isinstance(d, dict) else d
    except Exception:
        return {}
    out = {}
    for e in ents:
        out.setdefault(e["industry"], []).append(f'{e["manufacturer"]} {e["model"]}')
    return out


# ----------------------------------------------------------------------------- slides
def build():
    D = Deck()
    ex = catalogue_examples()

    # ---------------------------------------------------------------- 1 Title
    s = D.new("", notes=(
        "Open with the hook, slowly: everything in this room was made in a factory. Phones, medicines, the clothes we are wearing. "
        "Factories are the growth engine of the world, and they are also among its biggest polluters. "
        "TerraVow is the bridge between the two. It links the health of a machine to the pollution it creates, tells the operator what "
        "to fix and when, and shows greener options with the incentives that can pay for them. "
        "We built it on Google Cloud with Gemini, and every number you will see comes from a deterministic engine, not from the model. "
        "In the next three minutes we will show the problem, the product running, and why you can trust it."),
        visual="Dark title slide with factory silhouette, ring and tagline", number=False)
    # decorative ring + factory
    ring = rect(s, 7.6, 0.9, 5.4, 5.4, fill=None, line=GREEN, lw=1.25, shape=MSO_SHAPE.OVAL)
    ring2 = rect(s, 8.0, 1.3, 4.6, 4.6, fill=None, line=BLUE, lw=0.75, shape=MSO_SHAPE.OVAL)
    bx, by = 8.0, 3.6
    rect(s, bx, by + 0.9, 4.6, 1.5, fill="17232E", line="2F4A5F", radius=0.0, shape=MSO_SHAPE.RECTANGLE)
    for i in range(4):  # saw-tooth roof
        t = rect(s, bx + 0.15 + i * 1.1, by + 0.25, 1.1, 0.65, fill="17232E", line="2F4A5F", radius=0.0,
                 shape=MSO_SHAPE.RIGHT_TRIANGLE)
    rect(s, bx + 3.7, by - 0.9, 0.38, 1.8, fill="1D2C39", line="2F4A5F", radius=0.0, shape=MSO_SHAPE.RECTANGLE)
    rect(s, bx + 3.05, by - 0.4, 0.34, 1.3, fill="1D2C39", line="2F4A5F", radius=0.0, shape=MSO_SHAPE.RECTANGLE)
    for (cx, cy, r_, col, al) in [(bx + 3.85, by - 1.2, 0.38, "6B7C8C", 55), (bx + 4.1, by - 1.65, 0.5, "6B7C8C", 40),
                                  (bx + 3.2, by - 0.7, 0.3, "6B7C8C", 55)]:
        o = rect(s, cx - r_, cy - r_, 2 * r_, 2 * r_, fill=col, line=None, shape=MSO_SHAPE.OVAL)
        set_alpha(o, al)
    for i in range(6):  # lit windows
        w_ = rect(s, bx + 0.35 + i * 0.7, by + 1.25, 0.32, 0.22, fill=GREEN if i % 3 else AMBER, line=None,
                  radius=0.0, shape=MSO_SHAPE.RECTANGLE)
        set_alpha(w_, 80)
    leaf = rect(s, bx + 0.5, by + 2.55, 3.6, 0.04, fill=GREEN, line=None, radius=0.0, shape=MSO_SHAPE.RECTANGLE)
    add_text(s, MX, 0.7, 6, 0.4, "AI FOR GREENER FACTORIES", size=13, bold=True, color=GREEN)
    add_text(s, MX, 1.35, 7.2, 1.4, "TerraVow", size=72, bold=True)
    add_text(s, MX, 2.85, 6.9, 1.65, ["Healthy machines.", "Cleaner factories.", "Growth that lasts."], size=30, bold=True,
             color=GREEN)
    add_text(s, MX, 4.55, 6.6, 1.0,
             "AI that links machine health to pollution, tells operators what to fix and when, and shows greener "
             "alternatives with the incentives that can pay for them.", size=16, color=MUTED)
    x = MX
    for t, wd in [("Gemini", 1.15), ("Cloud Run", 1.3), ("Firestore", 1.2)]:
        chip(s, x, 5.85, wd, t, fill=DKBLUE, line=BLUE, color=BLUE, size=12, h=0.34)
        x += wd + 0.15
    add_text(s, MX, 6.5, 8, 0.4, "[Team name]   |   [Hackathon and theme label]   |   [Date]", size=12, color=AMBER)

    # ---------------------------------------------------------------- 2 Paradox
    s = D.new("Factories grow the world, and they are among its biggest polluters", "The paradox",
              notes=(
                  "Here is the paradox. On the left, the growth engine: manufacturing is about 15 percent of world GDP according to the World Bank, "
                  "16 to 17 percent of India's GDP according to the Press Information Bureau, and in India small and medium enterprises make "
                  "about 35 percent of manufacturing output. On the right, the cost. The IPCC says industry produced 24 percent of global "
                  "greenhouse gas emissions in 2019, and 34 percent once the electricity factories use is attributed to them. That makes it the largest "
                  "end-use share. And carbon is only one part: factories also release into air, water, chemicals and soil. "
                  "Caveat for the careful: the World Bank figure is manufacturing only, the IPCC figure is the wider industry sector, so the two shares "
                  "are not like for like. The point is that both are true, and factories cannot simply switch off."),
              sources=[f"World Bank: {URL['S1']}", f"IPCC AR6 WGIII SPM B.2.1: {URL['S2']}", f"PIB manufacturing: {URL['S3']}",
                       f"PIB MSME: {URL['S4']}"],
              footer="Sources: World Bank NV.IND.MANF.ZS (2025); IPCC AR6 WGIII SPM B.2.1 (2019); PIB 14 Oct 2026 and 14 May 2026. "
                     "Manufacturing value added and the IPCC industry sector are different scopes.",
              visual="Left: growth stat cards; right: native bar chart of 2019 GHG share by sector with industry highlighted")
    # left panel
    rect(s, MX, 1.75, 5.75, 4.95, fill=PANEL, line=GREEN, lw=1.25)
    add_text(s, MX + 0.25, 1.85, 5.2, 0.35, "THE GROWTH ENGINE", size=13, bold=True, color=GREEN)
    add_text(s, MX + 0.25, 2.2, 3.1, 1.2, "~15%", size=66, bold=True, color=GREEN)
    add_text(s, MX + 3.2, 2.4, 2.4, 1.0, "of world GDP is manufacturing value added", size=14, color=TEXT)
    for i, (big, small) in enumerate([("16-17%", "of India's GDP is manufacturing"),
                                      ("35.4%", "of India's manufacturing output comes from MSMEs")]):
        yy = 3.75 + i * 1.4
        rect(s, MX + 0.25, yy, 5.25, 1.2, fill=PANEL2, line=BORDER)
        add_text(s, MX + 0.4, yy + 0.1, 2.0, 1.0, big, size=36, bold=True, color=GREEN, anchor="m")
        add_text(s, MX + 2.45, yy + 0.1, 2.95, 1.0, small, size=14, anchor="m")
    # right panel chart
    rect(s, 6.9, 1.75, 5.83, 4.95, fill=PANEL, line=RED, lw=1.25)
    add_text(s, 7.15, 1.85, 5.3, 0.35, "THE COST", size=13, bold=True, color=RED)
    add_text(s, 7.15, 2.2, 5.3, 0.55, "Share of 2019 global GHG emissions, by sector", size=14, color=TEXT)
    cd = CategoryChartData()
    cd.categories = ["Energy supply", "Industry", "Agriculture, land use", "Transport", "Buildings"]
    cd.add_series("Share of 2019 net global GHG emissions (%)", (34, 24, 22, 15, 6))
    gf = s.shapes.add_chart(XL_CHART_TYPE.BAR_CLUSTERED, Inches(7.05), Inches(2.7), Inches(5.55), Inches(2.75), cd)
    ch = gf.chart
    chart_clear_bg(ch)
    ch.has_legend = False
    ch.has_title = False
    plot = ch.plots[0]
    plot.gap_width = 45
    plot.has_data_labels = True
    dl = plot.data_labels
    dl.font.size = Pt(13)
    dl.font.bold = True
    dl.font.color.rgb = rgb(TEXT)
    dl.number_format = '0"%"'
    dl.number_format_is_linked = False
    dl.position = XL_LABEL_POSITION.OUTSIDE_END
    ser = plot.series[0]
    ser.format.fill.solid()
    ser.format.fill.fore_color.rgb = rgb("4A5B6B")
    pt = ser.points[1]
    pt.format.fill.solid()
    pt.format.fill.fore_color.rgb = rgb(RED)
    style_axis(ch.category_axis, size=12, color=TEXT)
    ch.category_axis.reverse_order = True
    ch.category_axis.format.line.color.rgb = rgb("3A4F61")
    va = ch.value_axis
    va.visible = False
    va.maximum_scale = 42
    va.minimum_scale = 0
    va.has_major_gridlines = False
    card(s, 7.15, 5.55, 5.35, 1.0,
         [[("Industry: 24% direct, ", {"bold": True, "color": RED}),
           ("34% ", {"bold": True, "color": RED, "size": 18}),
           ("once electricity is attributed to the factories that use it.", {})]],
         fill=DKRED, line=RED, size=13, anchor="m")

    # ---------------------------------------------------------------- 3 Hidden cause
    s = D.new("The hidden cause: worn machines quietly burn more and leak more", "The hidden cause",
              notes=(
                  "Here is the part nobody sees. A worn seal lets air in, a drifting burner makes soot, a clogged filter forces the fan to work harder. "
                  "Each one is a small maintenance snag, and each one makes the machine burn more fuel and release more per tonne of output. "
                  "On the left is a kiln from our synthetic demo fleet. Over twelve months its health score falls from the high seventies to 28, "
                  "and its effluent per day climbs from the healthy baseline of 800 cubic metres to about 918. That is an illustrative model, "
                  "not a measurement, and we say so on every screen. For an outside anchor: the US Department of Energy estimates that operations "
                  "and maintenance programs aimed at energy efficiency can save 5 to 20 percent on energy bills without significant capital. "
                  "That is a US federal-sites guide, so read it as direction, not a promise for any one plant."),
              sources=[f"US DOE FEMP O&M Best Practices Guide: {URL['S5']}",
                       "Prototype figures: TerraVow engine (prototype/app/engine.py) on seeded machine K-4, GET /api/machines/K-4/health, run 2026-10-18"],
              footer="Left: Kiln K-4, synthetic demo fleet, illustrative engine model (not a measurement). Right: US DOE FEMP O&M Best Practices Guide, "
                     "Release 3.0 (US federal sites).",
              visual="Two native line charts (health falling, effluent rising) + three snag cards + DOE evidence strip")
    rect(s, MX, 1.75, 6.5, 4.95, fill=PANEL, line=BORDER)
    add_text(s, MX + 0.2, 1.82, 6.1, 0.33, "KILN K-4 OVER 12 MONTHS  |  ILLUSTRATIVE", size=12, bold=True, color=MUTED)
    for k, (label, series, color, top, nf) in enumerate([
            ("Health score (0-100)", K4_HEALTH, GREEN, 2.2, "0"),
            ("Effluent, m3 per day", K4_EFFLUENT, RED, 4.4, "0")]):
        add_text(s, MX + 0.2, top, 4, 0.3, label, size=13, bold=True, color=color)
        cd = CategoryChartData()
        cd.categories = K4_MONTHS
        cd.add_series(label, series)
        gf = s.shapes.add_chart(XL_CHART_TYPE.LINE_MARKERS, Inches(MX + 0.1), Inches(top + 0.28), Inches(6.3), Inches(1.75), cd)
        ch = gf.chart
        chart_clear_bg(ch)
        ch.has_legend = False
        ch.has_title = False
        sr = ch.plots[0].series[0]
        sr.format.line.color.rgb = rgb(color)
        sr.format.line.width = Pt(2.75)
        sr.smooth = False
        sr.marker.format.fill.solid()
        sr.marker.format.fill.fore_color.rgb = rgb(color)
        sr.marker.format.line.color.rgb = rgb(color)
        style_axis(ch.category_axis, size=10)
        style_axis(ch.value_axis, size=10, grid=True, line=False)
        if k == 0:
            ch.value_axis.minimum_scale = 20
            ch.value_axis.maximum_scale = 90
        else:
            ch.value_axis.minimum_scale = 780
            ch.value_axis.maximum_scale = 940
        # end label
        pl = ch.plots[0]
        last = sr.points[11]
        last.data_label.has_text_frame = True
        last.data_label.text_frame.text = ("28.4" if k == 0 else "918")
        last.data_label.position = XL_LABEL_POSITION.ABOVE
        for r in last.data_label.text_frame.paragraphs[0].runs:
            r.font.size = Pt(13)
            r.font.bold = True
            r.font.color.rgb = rgb(color)
    # right: snag cards
    add_text(s, 7.45, 1.75, 5.3, 0.35, "SMALL SNAGS, BIG CONSEQUENCES", size=12, bold=True, color=MUTED)
    snags = [("A worn seal", "Air leaks in. The burner works harder.", BLUE),
             ("A drifting burner", "Soot builds up. Fuel and NOx rise.", AMBER),
             ("A clogged filter", "The fan strains. Emissions per tonne climb.", RED)]
    for i, (a, b, c) in enumerate(snags):
        yy = 2.15 + i * 0.92
        rect(s, 7.45, yy, 5.28, 0.8, fill=PANEL2, line=c, lw=1.0)
        add_text(s, 7.6, yy + 0.04, 1.85, 0.72, a, size=15, bold=True, color=c, anchor="m")
        add_text(s, 9.45, yy + 0.04, 3.2, 0.72, b, size=13, anchor="m")
    card(s, 7.45, 5.0, 5.28, 1.7,
         [{"t": [("5-20%", {"size": 30, "bold": True, "color": GREEN}),
                 ("  lower energy bills with good O&M", {"size": 14})], "space_after": 2},
          {"t": "US DOE estimate for federal sites. Direction, not a promise for any one plant.", "size": 12,
           "color": MUTED}],
         fill=DKGREEN, line=GREEN, anchor="m")

    # ---------------------------------------------------------------- 4 Gap
    s = D.new("Today's tools cannot connect a deferred repair to a pollution event", "The gap today",
              notes=(
                  "So why does this go unseen? Because each team owns one piece. Maintenance systems track work orders and downtime cost, not emissions. "
                  "ESG and carbon-accounting tools report what you already emitted, using industry-average factors rather than the state of your machine. "
                  "Compliance and monitoring tools tell you whether a reading is above or below a limit, not why. "
                  "And a plain chatbot sounds confident but cannot show a calculation you can audit. "
                  "Nobody connects 'we deferred this repair' to 'we just created avoidable emissions and cost'. "
                  "And the factory cannot simply stop. Output, jobs and customers depend on it. It needs a practical path that keeps it running while it gets cleaner. "
                  "These categories come from our competitive review; vendor claims there should be re-verified before quoting."),
              sources=["docs/competitive-landscape.md and docs/overview.md in the TerraVow repo (category-level assessment; vendor positioning to be re-verified)"],
              footer="Category-level assessment from docs/competitive-landscape.md and docs/overview.md. Individual vendors may differ.",
              visual="Four dark cards (tool category, what it does, blind spot) and a red gap banner")
    cols = [("Maintenance systems", "CMMS / EAM, predictive maintenance", "Tracks downtime and work orders", "Blind to emissions cost", BLUE),
            ("ESG and carbon accounting", "Scope 1/2/3 ledgers", "Reports what was already emitted", "Industry averages, not your machine", GREEN),
            ("Compliance and monitoring", "EHS workflow, CEMS", "Above or below the limit, right now", "Not why, and often self-reported", AMBER),
            ("Plain LLM chatbot", "Ask a general model", "Fluent, quick answers", "Numbers you cannot audit", RED)]
    cw = 2.88
    for i, (t, sub, does, blind, c) in enumerate(cols):
        x = MX + i * (cw + 0.19)
        rect(s, x, 1.8, cw, 3.3, fill=PANEL, line=c, lw=1.25)
        add_text(s, x + 0.15, 1.9, cw - 0.3, 0.7, t, size=17, bold=True, color=c)
        add_text(s, x + 0.15, 2.6, cw - 0.3, 0.55, sub, size=12, color=MUTED)
        add_text(s, x + 0.15, 3.2, cw - 0.3, 0.3, "WHAT IT DOES", size=10, bold=True, color=MUTED)
        add_text(s, x + 0.15, 3.48, cw - 0.3, 0.6, does, size=13)
        add_text(s, x + 0.15, 4.1, cw - 0.3, 0.3, "BLIND SPOT", size=10, bold=True, color=c)
        add_text(s, x + 0.15, 4.38, cw - 0.3, 0.65, blind, size=13, bold=True)
    card(s, MX, 5.35, 12.13, 0.7,
         [[("Nobody connects ", {}), ("\"we deferred this repair\"", {"bold": True, "color": AMBER}),
           (" to ", {}), ("\"we just created avoidable emissions and cost\"", {"bold": True, "color": RED})]],
         fill=DKRED, line=RED, size=16, align="c")
    card(s, MX, 6.15, 12.13, 0.65,
         "And factories cannot just shut down. They need a practical path that keeps output growing while they get cleaner.",
         fill=PANEL2, line=GREEN, size=15, color=GREEN, bold=True, align="c")

    # ---------------------------------------------------------------- 5 Solution
    s = D.new("TerraVow is the bridge: from machine health to cleaner, funded upgrades", "The solution",
              notes=(
                  "This is TerraVow in one line of flow. Step one, people on the shop floor report snags against a specific machine model. "
                  "Step two, a deterministic engine turns that snag history into a health score and an estimate of remaining life. "
                  "Step three, the health score is linked to pollution intensity across air, water, chemical and soil. "
                  "Step four, TerraVow recommends greener machines and process changes. "
                  "Step five, it shows payback with the Indian incentives that may apply, for a small enterprise or a large one, and labels what is unverified. "
                  "Underneath all of it sit three promises: a safety gate that vetoes anything that adds or merely moves pollution, "
                  "an audit trail you can verify, and a clean split where Gemini reasons and writes while the engine does the maths."),
              sources=["docs/overview.md, docs/product-features.md (TerraVow repo)"],
              footer="Prototype scope: Requirements 18-30 (Python/FastAPI). See roadmap for what is not built.",
              visual="Five-step chevron flow with three principle cards below")
    steps = [("1", "Report", "Snags logged per machine model"), ("2", "Health", "Score and remaining life"),
             ("3", "Pollution", "Air, water, chemical, soil"), ("4", "Greener options", "Machines and process changes"),
             ("5", "Funded payback", "India incentives, MSME or large")]
    bw, gap = 2.2, 0.28
    for i, (n_, t, sub) in enumerate(steps):
        x = MX + i * (bw + gap)
        rect(s, x, 1.95, bw, 2.2, fill=PANEL, line=GREEN if i in (1, 2) else BORDER, lw=1.5)
        numdot(s, x + 0.15, 2.07, n_)
        add_text(s, x + 0.12, 2.55, bw - 0.24, 0.5, t, size=18, bold=True)
        add_text(s, x + 0.12, 3.1, bw - 0.24, 0.95, sub, size=14, color=MUTED)
        if i < 4:
            arrow(s, x + bw + 0.03, 3.05, x + bw + gap - 0.03, 3.05, color=GREEN, w=2.5)
    pr = [("Safety gate", "Vetoes any option that raises total emissions or merely moves pollution from air to water.", RED, DKRED),
          ("Audit trail", "Every recommendation and closure is written to a hash-chained log.", BLUE, DKBLUE),
          ("Numbers you can trust", "Gemini reasons and writes. A deterministic engine computes every figure.", GREEN, DKGREEN)]
    for i, (t, d, c, f) in enumerate(pr):
        x = MX + i * 4.1
        rect(s, x, 4.5, 3.93, 2.1, fill=f, line=c, lw=1.25)
        add_text(s, x + 0.2, 4.6, 3.5, 0.45, t, size=19, bold=True, color=c)
        add_text(s, x + 0.2, 5.1, 3.55, 1.45, d, size=15)

    # ---------------------------------------------------------------- 6 Four industries
    s = D.new("One flow, four industries, each with its own AI-built template", "Industries",
              notes=(
                  "The same flow works across four heavy industries, and each declares which pollution domains it touches. "
                  "Textile dyeing and finishing is air, water and chemical. Metals, steel and foundry, is air, water and soil. "
                  "Chemicals and fertiliser touches all four. Mining and mineral processing is air, water and soil. "
                  "The prototype ships a sample catalogue of twenty machine models, five per industry. It is sample data, not an authoritative manufacturer database, and labelled that way. "
                  "If a model is missing, the user adds it manually and Gemini with Google Search looks up the specs. "
                  "These are also industries India's pollution regulator watches closely: the Central Pollution Control Board lists seventeen highly polluting categories "
                  "including dyes, fertiliser, iron and steel, and cement, and 4,578 highly polluting units were reported in a December 2022 parliamentary answer."),
              sources=[f"CPCB 17 categories, Rajya Sabha Q101: {URL['S6']}", "prototype/data/industries.json, catalogue.json (TerraVow repo)"],
              footer="Domains from prototype/data/industries.json. Catalogue is sample data. CPCB: Rajya Sabha Starred Q101, 15 Dec 2022 (sansad.in).",
              visual="Four industry cards with domain chips and example machines + CPCB banner")
    cw = 2.88
    for i, (name, doms) in enumerate(INDUSTRIES):
        x = MX + i * (cw + 0.19)
        rect(s, x, 1.8, cw, 3.7, fill=PANEL, line=BORDER, lw=1.25)
        add_text(s, x + 0.15, 1.9, cw - 0.3, 0.75, name, size=18, bold=True)
        cx, cy = x + 0.15, 2.8
        for d in doms:
            f, l = DOMAIN_COL[d]
            wd = 0.2 + 0.105 * len(d)
            if cx + wd > x + cw - 0.1:
                cx, cy = x + 0.15, cy + 0.38
            chip(s, cx, cy, wd, d.upper(), fill=f, line=l, color=l, size=10, h=0.3)
            cx += wd + 0.08
        key = ["textile", "metals", "chemicals", "mining"][i]
        names = ex.get(key, [])[:3]
        add_text(s, x + 0.15, 3.75, cw - 0.3, 0.3, "SAMPLE MODELS", size=10, bold=True, color=MUTED)
        add_text(s, x + 0.15, 4.03, cw - 0.3, 1.4, [{"t": n, "space_after": 3} for n in names], size=13)
    card(s, MX, 5.75, 12.13, 0.95,
         [[("On India's regulator list: ", {"bold": True, "color": AMBER}),
           ("CPCB flags 17 highly polluting categories, including dyes, fertiliser, iron and steel and cement. "
            "4,578 such units reported (Dec 2022).", {})]],
         fill=DKAMBER, line=AMBER, size=14, anchor="m")

    # ---------------------------------------------------------------- 7 Report
    s = D.new("Step 1 | Report: pick a machine, get a form built for that exact model", "Live product walkthrough",
              notes=(
                  "Now the product, running. This is the Report a snag tab. The reporter picks an industry and searches the catalogue, with autocomplete. "
                  "If the model is not listed, they add it manually, and Gemini with Google Search grounding looks up the public specs, flagged as AI retrieved and unverified. "
                  "The first time any user opens a model, Gemini drafts a deep template: at least six job types, twelve snag types, eight operating parameters and the pollutant capture fields for that industry. "
                  "It is generated once and cached, so the next user gets it instantly. "
                  "In this screenshot Gemini is not configured, so you are seeing the fallback: a clearly labelled generic industry template. The product still works end to end. "
                  "Free text is cleaned of phone numbers and emails before it is saved."),
              sources=["Live prototype screenshot (local run, Gemini not configured). docs/user-flow.md Reporter journey; docs/product-features.md sections 2-4"],
              footer="Screenshot of the local prototype with Gemini not configured: the generic fallback template is shown and labelled. Sample catalogue data.",
              visual="Real UI screenshot (report tab) + 3 callouts + reporter flow strip")
    picture_fit(s, ASSETS / "ui_report.png", MX, 1.75, 8.1, 4.4)
    callouts = [("Search or add", "Autocomplete over 20 sample models. Not listed? Add it; Gemini with Google Search looks up specs."),
                ("Form built for the model", "First use triggers a Gemini template: 6+ job types, 12+ snag types, 8+ parameters. Cached."),
                ("Privacy by default", "Emails and phone numbers are stripped from free text before storage.")]
    for i, (t, d) in enumerate(callouts):
        y = 1.75 + i * 1.5
        rect(s, 8.9, y, 3.83, 1.4, fill=PANEL, line=BORDER)
        numdot(s, 9.0, y + 0.1, i + 1)
        add_text(s, 9.45, y + 0.08, 3.2, 0.38, t, size=14, bold=True)
        add_text(s, 9.0, y + 0.5, 3.65, 0.9, d, size=12, color=MUTED)
    labels = ["Industry", "Model", "Template", "Snag form", "Guidance"]
    for i, t in enumerate(labels):
        x = MX + i * 1.6
        card(s, x, 6.3, 1.4, 0.5, t, fill=DKBLUE if i == 2 else PANEL2, line=BLUE if i == 2 else BORDER, size=12,
             bold=True, align="c", color=BLUE if i == 2 else TEXT, inset=0.02)
        if i < 4:
            arrow(s, x + 1.41, 6.55, x + 1.59, 6.55, color=MUTED, w=1.5)

    # ---------------------------------------------------------------- 8 Resolver
    s = D.new("Step 2 | Resolve: worst snags first, with guidance for the fix", "Live product walkthrough",
              notes=(
                  "Next, the resolver. The queue lists open and assigned snags with the highest severity first. Each card carries guidance: the recommended job, the immediate action, "
                  "time to fix, safety hazards and when to escalate. The resolver accepts, resolves with notes, downtime and cost, then closes. "
                  "Closing is where the loop pays off: it recomputes the machine's health from its full snag history and writes an audit entry. "
                  "Honest note: the Reporter and Resolver switch is workflow guidance, not access control. The prototype has no authentication, and a production system would add proper role-based access. "
                  "These are synthetic seeded snags, labelled as such."),
              sources=["Live prototype screenshot (local run). docs/user-flow.md Resolver journey; docs/product-features.md section 5"],
              footer="Screenshot of the local prototype. Seeded snags are synthetic. No authentication in the prototype: the role switch is workflow guidance only.",
              visual="Real UI screenshot (resolver queue) + status chain + callouts")
    picture_fit(s, ASSETS / "ui_resolver.png", MX, 1.75, 6.3, 4.65)
    cc = [("Severity-first queue", "Each card: recommended job, immediate action, time to fix, hazards, escalation."),
          ("Close recomputes health", "Closing replays history and updates score, lifespan and pollution, plus an audit entry."),
          ("Honest about access", "No auth in the demo. The role switch guides the workflow; it is not a security control.")]
    for i, (t, d) in enumerate(cc):
        y = 1.75 + i * 1.3
        rect(s, 7.1, y, 5.63, 1.2, fill=PANEL, line=BORDER)
        numdot(s, 7.22, y + 0.12, i + 1, fill=GREEN if i < 2 else AMBER)
        add_text(s, 7.7, y + 0.06, 4.95, 0.38, t, size=16, bold=True)
        add_text(s, 7.7, y + 0.44, 4.95, 0.75, d, size=13, color=MUTED)
    add_text(s, 7.1, 5.72, 5.6, 0.28, "WORKFLOW STATES (EACH TRANSITION IS AN APPEND-ONLY EVENT)", size=10, bold=True, color=MUTED)
    stt = [("OPEN", GREY, PANEL2), ("ASSIGNED", BLUE, DKBLUE), ("RESOLVED", AMBER, DKAMBER), ("CLOSED", GREEN, DKGREEN)]
    for i, (t, c, f) in enumerate(stt):
        x = 7.1 + i * 1.42
        card(s, x, 6.05, 1.2, 0.5, t, fill=f, line=c, size=12, bold=True, color=c if c != GREY else TEXT, align="c", inset=0.02)
        if i < 3:
            arrow(s, x + 1.21, 6.3, x + 1.41, 6.3, color=MUTED, w=2)

    # ---------------------------------------------------------------- 9 Health
    s = D.new("Step 3 | Health, remaining life and pollution now move together", "Live product walkthrough",
              notes=(
                  "This is where TerraVow earns its name. Every closed or open snag feeds a deterministic health score. For Kiln K-4 in our synthetic fleet it is 28.4 out of 100, Critical. "
                  "From that and the machine's age against its design life, the engine estimates about 342 days of remaining life, with a range, and labels it as an estimate, not a prediction of failure. "
                  "The table links health to pollution intensity across air, water and soil: effluent is about 918 cubic metres a day against an 800 healthy reference. "
                  "The chart shows health over twelve months, and the pollution trend mirrors it. "
                  "All of this is plain code, with a visible How this is calculated panel. The language model never computes these numbers. Everything is labelled illustrative."),
              sources=["Live prototype screenshots (local run) of GET /api/machines/K-4/health; formulas in prototype/app/engine.py; docs/product-features.md sections 7-8"],
              footer="Kiln K-4, synthetic demo fleet. Health, lifespan and intensity are illustrative engine outputs, not measurements or certification.",
              visual="Three real UI crops: health gauge, pollution table, health timeline + key facts")
    picture_fit(s, ASSETS / "ui_health_table.png", MX, 1.7, 12.13, 3.3)
    facts = [("28.4 / 100", "health band: Critical", RED), ("~342 days", "estimated remaining life (range 257-428)", AMBER)]
    for i, (a, b, c) in enumerate(facts):
        x = MX + i * 3.05
        rect(s, x, 5.12, 2.9, 1.55, fill=PANEL2, line=c, lw=1.25)
        add_text(s, x + 0.1, 5.2, 2.7, 0.7, a, size=28, bold=True, color=c, anchor="m")
        add_text(s, x + 0.1, 5.92, 2.7, 0.7, b, size=13, anchor="t")
    rect(s, MX + 6.2, 5.12, 5.93, 1.55, fill=PANEL, line=BORDER)
    add_text(s, MX + 6.3, 5.14, 5.7, 0.28, "Health over the last 12 months (0-100)", size=11, bold=True, color=GREEN)
    cd = CategoryChartData()
    cd.categories = K4_MONTHS
    cd.add_series("Health", K4_HEALTH)
    gf = s.shapes.add_chart(XL_CHART_TYPE.LINE_MARKERS, Inches(MX + 6.25), Inches(5.38), Inches(5.8), Inches(1.27), cd)
    ch = gf.chart
    chart_clear_bg(ch)
    ch.has_legend = False
    ch.has_title = False
    sr = ch.plots[0].series[0]
    sr.format.line.color.rgb = rgb(GREEN)
    sr.format.line.width = Pt(2.5)
    sr.smooth = False
    sr.marker.format.fill.solid()
    sr.marker.format.fill.fore_color.rgb = rgb(GREEN)
    sr.marker.format.line.color.rgb = rgb(GREEN)
    style_axis(ch.category_axis, size=9)
    style_axis(ch.value_axis, size=9, grid=True, line=False)
    ch.value_axis.minimum_scale = 20
    ch.value_axis.maximum_scale = 100
    ch.value_axis.major_unit = 40
    add_text(s, MX, 6.7, 6.0, 0.28, "Computed by the engine, never by the LLM", size=11, color=GREEN, bold=True)

    # ---------------------------------------------------------------- 10 Sustainability
    s = D.new("Step 4 | Greener options with honest paybacks and India incentives", "Live product walkthrough",
              notes=(
                  "Last step in the walkthrough: Sustainability. For the selected machine, TerraVow lists greener machine alternatives, operating changes and assembly-stage changes. "
                  "For each it shows capex, cash benefit and two paybacks: cash payback, and payback including a notional exposure proxy that is not cash. "
                  "It does not hide bad news: this electric drive shows a payback beyond the 25-year design life on cash alone, around 28.8 years even after the illustrative incentive. "
                  "A toggle switches between small, MSME, and large enterprise and recomputes incentive-adjusted capex and payback. India schemes in the demo are mostly qualitative and marked UNVERIFIED or illustrative; "
                  "the quantified subsidies are demo-synthetic values and are labelled that way. "
                  "Every option passes the Decision Gate, and in this demo each one lands on Needs confirmation, meaning a human decides."),
              sources=["Live prototype screenshot (local run) of POST /api/machines/K-4/recommendations, MSME; prototype/app/engine.py candidate_economics; docs/user-flow.md Sustainability journey"],
              footer="Screenshot of the local prototype (curated fallback alternatives, Gemini not configured). Capex, benefits and incentive values are illustrative; schemes UNVERIFIED.",
              visual="Real UI screenshot of one alternative's economics + 4 callouts")
    picture_fit(s, ASSETS / "ui_sustain.png", MX, 1.75, 6.3, 4.1)
    ad = [("MSME-adjusted capex", "\u20b914,28,25,000"), ("Adjusted cash payback", "15.1 years"), ("With exposure proxy", "10.4 years")]
    for i, (a, b) in enumerate(ad):
        x = MX + i * 2.14
        card(s, x, 5.95, 2.02, 0.85, [{"t": a, "size": 11, "color": MUTED}, {"t": b, "size": 16, "bold": True, "color": GREEN}],
             fill=DKGREEN, line=GREEN, align="c")
    cc = [("Three kinds of change", "Machine swaps, operating-process changes and assembly-stage changes, curated or Gemini-suggested."),
          ("Two paybacks, no spin", "Cash payback and payback with an exposure proxy. Beyond design life is shown, not capped."),
          ("MSME or Large", "Toggle recomputes incentive-adjusted capex. Real-named schemes are UNVERIFIED; subsidy values are demo-synthetic."),
          ("Gate badge on every item", "Needs confirmation (ASK) in the demo: a human decides, nothing auto-approves.")]
    for i, (t, d) in enumerate(cc):
        y = 1.75 + i * 1.28
        rect(s, 7.1, y, 5.63, 1.18, fill=PANEL, line=BORDER)
        numdot(s, 7.22, y + 0.1, i + 1, fill=GREEN if i != 2 else AMBER)
        add_text(s, 7.7, y + 0.06, 4.95, 0.38, t, size=15, bold=True)
        add_text(s, 7.7, y + 0.44, 4.95, 0.72, d, size=12, color=MUTED)

    # ---------------------------------------------------------------- 11 Architecture
    s = D.new("Architecture: Gemini for language, a deterministic engine for numbers", "How it works",
              notes=(
                  "Here is how it fits together, simplified. A single-page app talks to one FastAPI service, designed for Cloud Run. Inside it, a workflow layer handles reporters and resolvers; "
                  "a deterministic engine computes health, lifespan, pollution intensity and economics; the Decision Gate vets every recommendation; and a cache-once layer decides when Gemini is allowed to be called at all. "
                  "Gemini 2.5 Flash, through AI Studio or Vertex AI, drafts templates, looks up specs with Google Search grounding, and suggests alternatives. Firestore stores everything, with an in-memory fallback so the demo runs anywhere. "
                  "Being honest about the Google stack: Gemini, Google Search grounding, Firestore and Cloud Run are what the prototype uses. BigQuery, Vertex AI Search, Agent Engine, Pub/Sub and Cloud Storage are in the full design, "
                  "and a Java reference engine exists, but they are not in the demo path."),
              sources=["docs/system-architecture.md; prototype/README.md 'What actually runs' and 'What is NOT built'; prototype/deploy.sh"],
              footer="Source: docs/system-architecture.md and prototype/README.md. Live Cloud Run and Firestore wiring is checked via /api/health (gemini_mode, store_backend) before submission.",
              visual="Dark boxes architecture diagram with Google services and a 'not in the demo path' strip")
    card(s, MX, 2.55, 1.95, 1.4, ["Browser", {"t": "single-page app", "size": 12, "color": MUTED}], fill=PANEL, line=BORDER, size=16,
         bold=True, align="c")
    arrow(s, MX + 1.97, 3.25, 3.3, 3.25, color=GREEN, w=2.5)
    add_text(s, MX + 1.9, 2.95, 0.9, 0.3, "HTTPS", size=10, color=MUTED, align="c")
    rect(s, 3.3, 1.8, 5.55, 3.45, fill="0E141B", line=GREEN, lw=1.75)
    add_text(s, 3.45, 1.85, 5.3, 0.35, "TerraVow API  |  FastAPI on Cloud Run", size=14, bold=True, color=GREEN)
    inner = [("Workflow", "reporter, resolver, snag history", PANEL, BORDER, TEXT),
             ("Deterministic engine", "health, lifespan, intensity, economics", DKGREEN, GREEN, GREEN),
             ("Decision Gate", "hard veto, then confidence", DKRED, RED, RED),
             ("cache_once", "Gemini at most once per key", DKBLUE, BLUE, BLUE)]
    for i, (t, d, f, l, c) in enumerate(inner):
        xx = 3.45 + (i % 2) * 2.68
        yy = 2.3 + (i // 2) * 1.4
        card(s, xx, yy, 2.55, 1.25, [{"t": t, "size": 15, "bold": True, "color": c}, {"t": d, "size": 12, "color": MUTED}],
             fill=f, line=l, align="c", anchor="m")
    # Gemini
    card(s, 9.55, 1.8, 3.18, 1.45, [{"t": "Gemini 2.5 Flash", "size": 16, "bold": True, "color": BLUE},
                                    {"t": "AI Studio or Vertex AI", "size": 12, "color": MUTED},
                                    {"t": "structured output, bounded retry", "size": 12, "color": MUTED}],
         fill=DKBLUE, line=BLUE, align="c", lw=1.5)
    card(s, 9.55, 3.4, 3.18, 0.95, [{"t": "Google Search grounding", "size": 14, "bold": True, "color": BLUE},
                                    {"t": "specs and alternatives", "size": 12, "color": MUTED}],
         fill=DKBLUE, line=BLUE, align="c")
    arrow(s, 8.87, 2.9, 9.53, 2.55, color=BLUE, w=2.5)
    arrow(s, 8.87, 3.0, 9.53, 3.7, color=BLUE, w=2)
    # Firestore
    card(s, 3.3, 5.45, 5.55, 0.95, [{"t": "Firestore (in-memory fallback)", "size": 15, "bold": True, "color": AMBER},
                                    {"t": "templates, snags, alternatives, incentives, hash-chained audit", "size": 12, "color": MUTED}],
         fill=DKAMBER, line=AMBER, align="c")
    arrow(s, 6.07, 5.27, 6.07, 5.43, color=AMBER, w=2.5)
    # notes: engine boundary
    card(s, 9.55, 4.55, 3.18, 1.85,
         [{"t": "Rule", "size": 12, "bold": True, "color": GREEN},
          {"t": "Gemini never computes a number. It drafts, looks up and explains. The engine validates and calculates.",
           "size": 13}],
         fill=DKGREEN, line=GREEN, anchor="m")
    add_text(s, MX, 4.1, 2.3, 1.3, "Works without a Gemini key: generic templates and curated alternatives.", size=12, color=MUTED)
    card(s, MX, 6.42, 12.13, 0.55,
         [[("Specified in the full design, not in the demo path: ", {"bold": True, "color": AMBER}),
           ("Vertex AI Agent Engine, BigQuery, Vertex AI Search, Pub/Sub, Cloud Storage. Java reference engine runs separately.", {"color": MUTED})]],
         fill=None, line=AMBER, size=12, dash=True, anchor="m")

    # ---------------------------------------------------------------- 12 Cache-once
    s = D.new("Technical depth: generate once per model, serve everyone", "Technical merit",
              notes=(
                  "This is the technical heart. Generating a deep, model-specific template with Gemini is slow and costs tokens, so it must happen at most once per model, even when many users arrive at once. "
                  "Step one: read the store; a hit returns immediately. Step two: on a miss, join a single flight, so one request becomes leader and the others wait and share its result. "
                  "Step three: the leader re-reads the store, a double-checked read. Step four: only now call Gemini, up to three attempts, validating against ten structural rules and sending the errors back as a hint. "
                  "Step five: create-if-absent is the final guard; if another writer won, ours is discarded and we return theirs. Step six: return generated or cache-hit. "
                  "If Gemini is unavailable, or the output stays invalid, a deterministic fallback template is returned and never stored, so a bad day cannot poison the cache. "
                  "Being straight about limits: this guarantees one stored document, but the cross-replica lease is not built yet. Two replicas could both call Gemini; only one result is kept."),
              sources=["docs/system-architecture.md 'Cache-Once Create-If-Absent Sequence'; docs/user-flow.md 'Template Ensure Cache Sequence'; prototype/app/cache_once.py"],
              footer="Source: docs/system-architecture.md and prototype/app/cache_once.py. Cross-replica lease is specified but not yet built.",
              visual="Six-step flow with guard layers and fallback branch")
    st = [("1", "Read store", "hit returns now", GREEN),
          ("2", "Join flight", "one leader per key", BLUE),
          ("3", "Re-read", "double-checked read", BLUE),
          ("4", "Call Gemini", "max 3 tries, 10 rules", BLUE),
          ("5", "create_if_absent", "loser discards", GREEN),
          ("6", "Return", "generated or cache-hit", GREEN)]
    bw = 1.85
    gp = 0.206
    for i, (n_, t, d, c) in enumerate(st):
        x = MX + i * (bw + gp)
        f = DKBLUE if c == BLUE else DKGREEN
        rect(s, x, 1.85, bw, 1.75, fill=f, line=c, lw=1.5)
        numdot(s, x + 0.1, 1.95, n_, fill=c)
        add_text(s, x + 0.08, 2.4, bw - 0.16, 0.5, t, size=16, bold=True)
        add_text(s, x + 0.08, 2.9, bw - 0.16, 0.65, d, size=12, color=MUTED)
        if i < 5:
            arrow(s, x + bw + 0.01, 2.72, x + bw + gp - 0.01, 2.72, color=MUTED, w=2)
    # fallback branch
    card(s, MX + 3 * (bw + gp), 3.95, bw * 2 + gp, 0.8,
         [{"t": "Gemini off, slow or invalid", "size": 13, "bold": True, "color": AMBER},
          {"t": "deterministic fallback template", "size": 12, "color": TEXT}],
         fill=DKAMBER, line=AMBER, align="c")
    arrow(s, MX + 3 * (bw + gp) + bw / 2, 3.62, MX + 3 * (bw + gp) + bw / 2, 3.93, color=AMBER, w=2)
    card(s, MX + 5 * (bw + gp) - 0.0, 3.95, bw, 0.8,
         [{"t": "never stored", "size": 14, "bold": True, "color": RED}], fill=DKRED, line=RED, align="c")
    arrow(s, MX + 3 * (bw + gp) + 2 * bw + gp, 4.35, MX + 5 * (bw + gp) - 0.02, 4.35, color=RED, w=2)
    # guarantees
    gs = [("Double-checked read", "Never calls Gemini if a result just landed."),
          ("Single flight", "Waiters share the leader's outcome, even a failure."),
          ("Final guard", "create_if_absent keeps exactly one document."),
          ("Fail safe", "Persist error is generated-unsaved; fallback is never cached.")]
    for i, (t, d) in enumerate(gs):
        x = MX + i * 3.06
        rect(s, x, 5.0, 2.95, 1.2, fill=PANEL, line=BORDER)
        add_text(s, x + 0.12, 5.05, 2.7, 0.35, t, size=14, bold=True, color=GREEN)
        add_text(s, x + 0.12, 5.42, 2.7, 0.75, d, size=12, color=MUTED)
    card(s, MX, 6.35, 12.13, 0.5,
         [[("Honest limit: ", {"bold": True, "color": AMBER}),
           ("one stored document is guaranteed; the cross-replica lease is not built, so two replicas may both pay for a Gemini call.",
            {})]], fill=None, line=AMBER, size=12, dash=True, anchor="m")

    # ---------------------------------------------------------------- 13 AI boundary
    s = D.new("Why AI, and why you can trust it: Gemini reasons, the engine calculates", "Why AI",
              notes=(
                  "Why is Gemini essential here, and why can you trust the output? Machine templates differ by model and by industry: a jet dyeing machine and a rotary kiln fail in completely different ways. "
                  "Nobody can hand-write thousands of those taxonomies, but Gemini can draft one for any model, grounded with Google Search for specs. It also suggests greener alternatives and explains results in plain language. "
                  "But the model never computes. Everything it returns passes through validation: schema checks, bounds, and a rule that invalid candidates are dropped, never quietly clamped into something else. "
                  "Gemini gives ranges; the engine takes the midpoint and calculates capex, avoided emissions and payback. On the right, the engine owns health, lifespan, intensity, economics and the gate verdict. "
                  "And if there is no Gemini key at all, the product degrades to a generic template and curated alternatives, with the same engine and the same gate."),
              sources=["docs/system-architecture.md 'Deterministic-Numbers-vs-LLM Boundary' and 'Graceful Degradation'; prototype/app/engine.py, gemini_client.py"],
              footer="Source: docs/system-architecture.md. Gemini 2.5 Flash with structured output, bounded retry (1-5, default 3) and per-attempt timeouts.",
              visual="Three columns: Gemini | validation | engine, with fallback band")
    colx = [MX, 4.95, 8.88]
    cwid = [3.95, 3.0, 3.85]
    # Gemini
    rect(s, colx[0], 1.8, cwid[0], 3.55, fill=DKBLUE, line=BLUE, lw=1.5)
    add_text(s, colx[0] + 0.2, 1.88, 3.5, 0.4, "GEMINI REASONS AND WRITES", size=13, bold=True, color=BLUE)
    add_text(s, colx[0] + 0.2, 2.35, cwid[0] - 0.4, 2.95,
             [{"t": "Drafts the per-model template", "space_after": 6}, {"t": "Looks up specs (Google Search)", "space_after": 6},
              {"t": "Suggests alternatives as low-high ranges", "space_after": 6}, {"t": "Explains results in plain language"}],
             size=17)
    # validate
    rect(s, colx[1], 1.8, cwid[1], 3.55, fill=PANEL, line=AMBER, lw=1.5)
    add_text(s, colx[1] + 0.15, 1.88, cwid[1] - 0.3, 0.4, "VALIDATE", size=13, bold=True, color=AMBER, align="c")
    add_text(s, colx[1] + 0.15, 2.35, cwid[1] - 0.3, 2.95,
             [{"t": "Schema and 10 template rules", "space_after": 6}, {"t": "Bounds and finiteness", "space_after": 6},
              {"t": "Drop invalid, never clamp", "space_after": 6}, {"t": "Midpoint of ranges", "space_after": 6},
              {"t": "Bounded retry and timeout"}], size=15, align="c")
    # engine
    rect(s, colx[2], 1.8, cwid[2], 3.55, fill=DKGREEN, line=GREEN, lw=1.5)
    add_text(s, colx[2] + 0.2, 1.88, 3.4, 0.4, "THE ENGINE CALCULATES", size=13, bold=True, color=GREEN)
    add_text(s, colx[2] + 0.2, 2.35, cwid[2] - 0.4, 2.95,
             [{"t": "Health score and lifespan", "space_after": 6}, {"t": "Pollution intensity per domain", "space_after": 6},
              {"t": "Capex, benefit, payback", "space_after": 6}, {"t": "Incentive adjustment", "space_after": 6},
              {"t": "Gate verdict"}], size=17)
    arrow(s, colx[0] + cwid[0] + 0.02, 3.55, colx[1] - 0.02, 3.55, color=MUTED, w=3)
    arrow(s, colx[1] + cwid[1] + 0.02, 3.55, colx[2] - 0.02, 3.55, color=MUTED, w=3)
    card(s, MX, 5.6, 12.13, 1.1,
         [{"t": [("No Gemini key? ", {"bold": True, "color": AMBER}),
                 ("Everything still runs: generic industry template, curated alternatives, the same engine and the same gate. "
                  "Every fallback is labelled on screen.", {})], "size": 15}],
         fill=DKAMBER, line=AMBER, anchor="m")

    # ---------------------------------------------------------------- 14 Responsible AI
    s = D.new("Responsible by design: a hard veto that no confidence score can override", "Trust and safety",
              notes=(
                  "Safety here is structural, not a disclaimer. Every recommendation passes the Decision Gate in a fixed order. First, a hard veto: if the option would increase total emissions, "
                  "or merely shift pollution from one medium to another, say from air into water, it is blocked, however confident the model is. Second, confidence: at or above 0.80 allow, at or above 0.60 ask a human, below that block. "
                  "Third, a trust cap that can only lower a verdict. Fourth, an audit entry is written to a hash-chained log before the response returns. "
                  "In the demo, nothing reaches Allow: AI-suggested options carry confidence 0.55 and curated ones 0.70, so a person always confirms. "
                  "Around the gate: estimate and illustrative labels everywhere, personal data stripped from free text, vendor validation required for AI suggestions, and plain honesty that the demo has no authentication."),
              sources=["docs/overview.md 'Decision Gate'; docs/user-flow.md Sustainability journey; prototype/app/gate.py; prototype/README.md"],
              footer="Source: prototype/app/gate.py and docs/user-flow.md. Thresholds as built: allow >= 0.80, ask >= 0.60. Outputs are advisory, not certification.",
              visual="Four-stage gate flow with BLOCK/ASK/ALLOW badges and trust checklist")
    stg = [("1", "Hard veto", "Total emissions up, or pollution moved air to water?", "BLOCK", RED, DKRED),
           ("2", "Confidence", "0.80 and up: ALLOW  |  0.60 and up: ASK  |  else BLOCK", "", AMBER, DKAMBER),
           ("3", "Trust cap", "Low data trust can only lower the verdict", "", BLUE, DKBLUE),
           ("4", "Audit", "Hash-chained entry written before the response", "", GREEN, DKGREEN)]
    for i, (n_, t, d, badge, c, f) in enumerate(stg):
        y = 1.8 + i * 1.22
        rect(s, MX, y, 7.2, 1.1, fill=f, line=c, lw=1.5)
        numdot(s, MX + 0.12, y + 0.36, n_, fill=c)
        add_text(s, MX + 0.65, y + 0.06, 2.0, 0.5, t, size=18, bold=True, color=c)
        add_text(s, MX + 0.65, y + 0.5, 6.4, 0.58, d, size=13)
        if i < 3:
            arrow(s, MX + 3.6, y + 1.1, MX + 3.6, y + 1.22, color=MUTED, w=2)
    # badges
    bx = 8.05
    add_text(s, bx, 1.75, 4.7, 0.3, "WHAT THE USER SEES", size=11, bold=True, color=MUTED)
    for i, (t, d, c, f) in enumerate([("ALLOW", "Allowed by the safety check", GREEN, DKGREEN),
                                      ("ASK", "Needs confirmation", BLUE, DKBLUE),
                                      ("BLOCK", "Blocked by the safety check", RED, DKRED)]):
        yy = 2.1 + i * 0.62
        chip(s, bx, yy, 0.95, t, fill=f, line=c, color=c, size=12, h=0.42)
        add_text(s, bx + 1.1, yy, 3.6, 0.42, d, size=13, anchor="m")
    add_text(s, bx, 4.05, 4.7, 0.3, "AROUND THE GATE", size=11, bold=True, color=MUTED)
    tr = ["Labels: estimate, illustrative, not a certification", "PII stripped from free text",
          "AI options: vendor validation required", "Demo: nothing reaches ALLOW; a human decides",
          "No auth in the demo, stated on screen"]
    add_text(s, bx, 4.35, 4.7, 2.4, [{"t": [("\u2713  ", {"color": GREEN, "bold": True}), (t, {})], "space_after": 4} for t in tr], size=13)

    # ---------------------------------------------------------------- 15 Differentiation
    s = D.new("Not another CMMS, ESG ledger or chatbot: TerraVow connects the dots", "Differentiation",
              notes=(
                  "How are we different? We assessed five neighbouring categories. Predictive-maintenance and CMMS tools know machine health but do not price pollution or recommend greener alternatives. "
                  "ESG and carbon accounting are ledger-grade for what you have emitted, but they use average factors, not your machine's state. "
                  "EHS and monitoring tools capture readings and paperwork, but not the why. A plain chatbot can suggest ideas but cannot give you auditable numbers or a safety veto. "
                  "TerraVow combines per-machine health, pollution linkage, greener options with payback, Indian incentive adjustment, deterministic auditable numbers, and a hard veto in one flow. "
                  "In the interest of honesty: predictive-maintenance vendors have large data moats we do not, and our incentive row is only partial because the scheme values are illustrative. "
                  "This table is a category-level judgement from our competitive review, not a claim about any one vendor."),
              sources=["docs/competitive-landscape.md in the TerraVow repo (categories and vendor sources linked there; re-verify before quoting)"],
              footer="Category-level assessment (our judgement) from docs/competitive-landscape.md; individual products vary. Legend: full = built, partial = limited or illustrative, none = not offered.",
              visual="Capability matrix with filled/half/empty markers for 5 categories vs TerraVow")
    heads = ["Capability", "Maintenance / CMMS", "ESG and carbon", "EHS and CEMS", "Plain chatbot", "TerraVow"]
    F, P_, N = "\u25CF", "\u25D0", "\u25CB"
    rows = [heads,
            ["Per-machine health from snag history", F, N, N, N, F],
            ["Links health to air, water, chemical, soil", P_, N, P_, N, F],
            ["Greener option with payback", N, N, N, P_, F],
            ["India incentive-adjusted (MSME / large)", N, N, N, N, P_],
            ["Auditable, deterministic numbers", F, F, P_, N, F],
            ["Veto on fixes that move pollution", N, N, N, N, F]]
    cwv = [4.1, 1.55, 1.55, 1.55, 1.55, 1.83]
    cy = 1.8
    rh = 0.56
    for ri, row in enumerate(rows):
        cx = MX
        for ci, cell in enumerate(row):
            hdr = ri == 0
            tv = ci == 5
            fill = (DKGREEN if tv else PANEL2) if hdr else (DKGREEN if tv else (PANEL if ri % 2 else "0E141B"))
            line = GREEN if tv else BORDER
            col = TEXT
            sz = 13
            if not hdr and ci > 0:
                sz = 22
                col = GREEN if cell == F else (AMBER if cell == P_ else GREY)
            if hdr:
                col = GREEN if tv else TEXT
            card(s, cx, cy, cwv[ci], rh, cell, fill=fill, line=line, size=sz if not hdr else 12, color=col,
                 bold=hdr or ci == 0 and False, align="l" if ci == 0 else "c", anchor="m", radius=0.0, lw=1.0 if tv else 0.5,
                 inset=0.1)
            cx += cwv[ci]
        cy += rh
    add_text(s, MX, cy + 0.12, 12.1, 0.35,
             [[("\u25CF", {"color": GREEN, "bold": True}), (" built     ", {}), ("\u25D0", {"color": AMBER, "bold": True}),
               (" partial or illustrative     ", {}), ("\u25CB", {"color": GREY, "bold": True}), (" not offered", {})]], size=13)
    card(s, MX, cy + 0.5, 12.13, 0.5,
         [[("Where we are behind: ", {"bold": True, "color": AMBER}),
           ("maintenance vendors have large fleets of real sensor data; we have a synthetic demo fleet.", {})]],
         fill=None, line=AMBER, size=13, dash=True, anchor="m")

    # ---------------------------------------------------------------- 16 Impact
    s = D.new("Illustrative impact: one Critical kiln, and what each fix would be worth", "Impact (illustrative)",
              notes=(
                  "A worked example from the synthetic demo fleet, so treat every number as illustrative. Kiln K-4 has fallen to a health of 28.4, Critical, with roughly 342 days of estimated life left. "
                  "Against its healthy reference it is running about 14.8 percent higher on effluent, around 118 cubic metres a day, and 5.4 percent higher on CO2e, roughly 2.3 tonnes a day. "
                  "Now the options, for a small enterprise with the illustrative MSME subsidy. Dust suppression and closed-circuit grinding pays back in about 5.7 years on cash and 3.4 including the exposure proxy. "
                  "A high-efficiency kiln about 15 years; remanufacture about 22; an electric drive reaches 28.8 years on cash, beyond the design life, though about 12.9 including the exposure proxy. "
                  "And dry-stack tailings with water recycling has no payback at all. We show that rather than hide it, because trust is the product. "
                  "Prioritising by health and payback is how a plant with limited capital chooses where to start."),
              sources=["Prototype run: GET /api/machines/K-4/health and POST /api/machines/K-4/recommendations (MSME), 2026-10-18; prototype/app/engine.py. Demo-synthetic MSME subsidy; curated illustrative alternatives."],
              footer="All figures: synthetic demo fleet, curated illustrative alternatives, demo-synthetic MSME subsidy, illustrative pollution baselines. Not a measurement, quotation or advice.",
              visual="Stat tiles for K-4 + native clustered bar chart of payback years with a no-payback callout")
    tiles = [("28.4 / 100", "health: Critical", RED), ("~342 days", "estimated life left", AMBER),
             ("+14.8%", "effluent vs healthy reference (~118 m3/day)", RED),
             ("+5.4%", "CO2e vs healthy reference (~2.3 t/day)", RED)]
    for i, (a, b, c) in enumerate(tiles):
        y = 1.8 + i * 1.18
        rect(s, MX, y, 4.3, 1.06, fill=PANEL, line=c, lw=1.25)
        add_text(s, MX + 0.12, y + 0.04, 1.95, 0.98, a, size=24, bold=True, color=c, anchor="m")
        add_text(s, MX + 2.1, y + 0.04, 2.1, 0.98, b, size=12, anchor="m")
    add_text(s, 5.2, 1.75, 7.5, 0.35, "PAYBACK IN YEARS, MSME, INCENTIVE-ADJUSTED (DESIGN LIFE 25 YEARS)", size=12, bold=True, color=MUTED)
    cd = CategoryChartData()
    cd.categories = [p[0] for p in PAYBACK]
    cd.add_series("Cash payback", [p[1] for p in PAYBACK])
    cd.add_series("Including exposure proxy (not cash)", [p[2] for p in PAYBACK])
    gf = s.shapes.add_chart(XL_CHART_TYPE.BAR_CLUSTERED, Inches(5.1), Inches(2.1), Inches(7.65), Inches(3.55), cd)
    ch = gf.chart
    chart_clear_bg(ch)
    ch.has_title = False
    ch.has_legend = True
    ch.legend.position = XL_LEGEND_POSITION.BOTTOM
    ch.legend.include_in_layout = False
    ch.legend.font.size = Pt(12)
    ch.legend.font.color.rgb = rgb(TEXT)
    plot = ch.plots[0]
    plot.gap_width = 55
    plot.overlap = -5
    plot.has_data_labels = True
    plot.data_labels.font.size = Pt(12)
    plot.data_labels.font.bold = True
    plot.data_labels.font.color.rgb = rgb(TEXT)
    plot.data_labels.number_format = "0.0"
    plot.data_labels.number_format_is_linked = False
    plot.data_labels.position = XL_LABEL_POSITION.OUTSIDE_END
    for sr, col in zip(plot.series, [BLUE, GREEN]):
        sr.format.fill.solid()
        sr.format.fill.fore_color.rgb = rgb(col)
    style_axis(ch.category_axis, size=12, color=TEXT)
    ch.category_axis.reverse_order = True
    va = ch.value_axis
    style_axis(va, size=11, grid=True, line=False)
    va.minimum_scale = 0
    va.maximum_scale = 35
    va.major_unit = 5
    card(s, 5.2, 5.8, 3.7, 0.9,
         [{"t": "Dry-stack tailings + water recycling", "size": 13, "bold": True, "color": RED},
          {"t": "No payback. Shown, not hidden.", "size": 13}], fill=DKRED, line=RED, anchor="m")
    card(s, 9.05, 5.8, 3.68, 0.9,
         [{"t": "Start with dust suppression", "size": 13, "bold": True, "color": GREEN},
          {"t": "Shortest payback, lowest capex.", "size": 13}], fill=DKGREEN, line=GREEN, anchor="m")

    # ---------------------------------------------------------------- 17 Business
    s = D.new("Where it lands: India's MSMEs and its highly polluting units", "Market and business model",
              notes=(
                  "Who is this for? India is the natural first market because the incentive logic is built in. The Press Information Bureau reports more than 7.47 crore MSMEs, across manufacturing, services and trade, "
                  "contributing about 35 percent of manufacturing output. The pollution regulator has identified seventeen highly polluting categories and 4,578 such units, as of a December 2022 parliamentary answer. "
                  "Those units are our beachhead; MSME clusters are the long tail. We are deliberately not quoting a total addressable market in dollars: we have not found a number we can defend, and we would rather leave it out than invent one. "
                  "On the right is our business-model hypothesis, not yet validated: a per-machine subscription for plants, an advisory and financing partnership that turns verified upgrade plans into funded projects, and vendor partnerships. "
                  "Pricing is to be tested with pilot plants."),
              sources=[f"PIB MSME backgrounder: {URL['S4']}", f"CPCB categories and unit count (Rajya Sabha Q101): {URL['S6']}"],
              footer="Sources: PIB backgrounder 14 May 2026 (MSME counts include services and trade); Rajya Sabha Starred Q101, 15 Dec 2022. Business model is a hypothesis; no revenue claims.",
              visual="Three sourced stat tiles + business-model hypothesis cards")
    st = [("7.47 crore", "MSMEs in India (all sectors)", GREEN), ("35.4%", "of India's manufacturing output is MSME", GREEN),
          ("4,578", "highly polluting units reported by SPCBs / PCCs (Dec 2022)", AMBER),
          ("17", "CPCB categories of highly polluting industry", AMBER)]
    for i, (a, b, c) in enumerate(st):
        x = MX + (i % 2) * 3.0
        y = 1.8 + (i // 2) * 2.35
        rect(s, x, y, 2.88, 2.2, fill=PANEL, line=c, lw=1.25)
        add_text(s, x + 0.1, y + 0.15, 2.7, 0.9, a, size=34, bold=True, color=c, anchor="m")
        add_text(s, x + 0.12, y + 1.1, 2.65, 1.05, b, size=13)
    add_text(s, 6.95, 1.75, 5.8, 0.35, "BUSINESS MODEL HYPOTHESES  |  NOT YET VALIDATED", size=12, bold=True, color=AMBER)
    bm = [("Plant subscription", "Priced per machine tracked. Reporter and resolver seats included."),
          ("Upgrade advisory", "Turn gated, incentive-adjusted plans into funded projects with partners."),
          ("Vendor and financier partnerships", "Greener-equipment suppliers and green-finance lenders pay for qualified leads.")]
    for i, (t, d) in enumerate(bm):
        y = 2.15 + i * 1.3
        rect(s, 6.95, y, 5.78, 1.18, fill=PANEL, line=BORDER)
        numdot(s, 7.08, y + 0.12, i + 1, fill=AMBER)
        add_text(s, 7.6, y + 0.08, 5.0, 0.4, t, size=16, bold=True)
        add_text(s, 7.6, y + 0.5, 5.0, 0.66, d, size=13, color=MUTED)
    card(s, 6.95, 6.1, 5.78, 0.6, "No market-size or revenue figure is claimed beyond the sourced counts.", fill=None,
         line=AMBER, size=13, color=AMBER, bold=True, dash=True, align="c")

    # ---------------------------------------------------------------- 18 Roadmap
    s = D.new("Roadmap: what is built, what is next, and what we have not claimed", "Roadmap",
              notes=(
                  "A straight view of where we are. Done: four industries, a twenty-model sample catalogue, the cache-once AI template, the reporter and resolver workflow, "
                  "the deterministic health, lifespan and pollution engine, sustainable alternatives with incentives, the Decision Gate and a hash-chained audit trail. "
                  "The prototype has 151 automated tests and we ran them all green. "
                  "Next, in priority order: the cross-replica lease so only one replica ever calls Gemini for a key, a request deadline and waiter cap, verifying every India scheme with its issuing authority, real role-based access, and measuring template depth and latency with a live key. "
                  "Later: sensor ingestion, regulation-grounded retrieval, the data integrity gate that checks sources and baselines, and a supply-chain footprint passport. "
                  "And what is out of scope for now: official certification and a public registry. The score is an evidence record, not a certificate."),
              sources=["prototype/README.md ('What is NOT built'); docs/system-architecture.md ('Not Yet Implemented', 'Multi-Replica Safety'); pytest run: 151 passed (2026-10-18)"],
              footer="Source: prototype/README.md and docs/system-architecture.md. Test result from a local pytest run on 2026-10-18: 151 passed.",
              visual="Three columns Done / Next / Later + out-of-scope strip")
    cols = [("DONE", GREEN, DKGREEN, ["4 industries, 20-model sample catalogue", "AI template, cache-once (single process)",
                                     "Reporter and Resolver workflow", "Health, lifespan, pollution engine",
                                     "Alternatives, incentives, Decision Gate", "Hash-chained audit; 151 tests passing"]),
            ("NEXT", BLUE, DKBLUE, ["Cross-replica lease (Firestore)", "Request deadline and waiter cap",
                                    "Verify India schemes with authorities", "Real role-based access control",
                                    "Measure template depth and latency", "Show grounding citations in the UI"]),
            ("LATER", GREY, PANEL2, ["Sensor ingestion (Pub/Sub, BigQuery)", "Regulation retrieval (Vertex AI Search)",
                                     "Agent Engine orchestration", "Integrity gate: source and baseline checks",
                                     "Supply-chain footprint passport", "Calibrate against real machine data"])]
    for i, (t, c, f, items) in enumerate(cols):
        x = MX + i * 4.1
        rect(s, x, 1.8, 3.93, 4.15, fill=f, line=c if c != GREY else BORDER, lw=1.5)
        add_text(s, x + 0.2, 1.88, 3.5, 0.4, t, size=16, bold=True, color=c if c != GREY else TEXT)
        add_text(s, x + 0.2, 2.35, 3.55, 3.55,
                 [{"t": [("\u2022 ", {"color": c if c != GREY else MUTED, "bold": True}), (it, {})], "space_after": 9} for it in items],
                 size=16)
    card(s, MX, 6.15, 12.13, 0.6,
         [[("Out of scope for now: ", {"bold": True, "color": RED}),
           ("official certification and a public registry. Scores are evidence records, not certificates.", {})]],
         fill=DKRED, line=RED, size=14, anchor="m")

    # ---------------------------------------------------------------- 19 Team + video
    s = D.new("Who we are, where to try it, and the 3-minute demo", "Team and demo plan",
              notes=(
                  "Placeholders to fill before submitting: team names and roles, the live Cloud Run link, the public repository, and the video link. "
                  "Along the bottom is the plan for the under-three-minute demo video, matched to this deck: the paradox hook, then pick a machine and see the AI template generated once, "
                  "report a snag, resolve it, watch health and pollution move, look at greener options and paybacks, then the safety gate and architecture, and a short close. "
                  "Record the live site, not slides, keep it under three minutes, and say illustrative sample image or synthetic demo fleet out loud wherever it applies."),
              sources=["prototype/SUBMISSION.md checklist; CDAC-hackathon/docs/ai-builder-cup-decision-brief.md (submission pack: deck PDF, live link, public repo, video under 3 minutes)"],
              footer="Fill the amber placeholders before submission. Submission pack: deck PDF, live link, public repo, video under 3 minutes.",
              visual="Team placeholders, link placeholders, 8-segment demo storyboard timeline")
    for i in range(4):
        x = MX + i * 3.06
        card(s, x, 1.8, 2.95, 1.05, [{"t": "[Name]", "size": 16, "bold": True, "color": AMBER},
                                     {"t": "[Role: e.g. AI / backend / product]", "size": 12, "color": MUTED}],
             fill=PANEL, line=AMBER, dash=True, align="c", anchor="m")
    links = [("Live app", "[Cloud Run URL]"), ("Repo", "[GitHub URL]"), ("Demo video", "[Video URL]"), ("Deck PDF", "[PDF link]")]
    for i, (a, b) in enumerate(links):
        x = MX + i * 3.06
        card(s, x, 3.0, 2.95, 0.8, [{"t": a, "size": 12, "bold": True, "color": MUTED}, {"t": b, "size": 15, "color": AMBER, "bold": True}],
             fill=PANEL, line=BORDER, align="c", anchor="m")
    add_text(s, MX, 4.0, 12, 0.3, "DEMO VIDEO STORYBOARD  |  TARGET 2:50, HARD LIMIT 3:00", size=12, bold=True, color=GREEN)
    seg = [("0:00", "Hook: the paradox", 15, GREY), ("0:15", "Pick model, AI template once", 20, BLUE),
           ("0:35", "Report a snag", 25, BLUE), ("1:00", "Resolve and close", 20, BLUE),
           ("1:20", "Health and pollution", 25, GREEN), ("1:45", "Options, payback, incentives", 35, GREEN),
           ("2:20", "Safety gate, architecture", 20, RED), ("2:40", "Close and call to action", 10, AMBER)]
    total = 170
    x = MX
    fullw = 12.13
    for t, d, secs, c in seg:
        wd = fullw * secs / total
        f = {GREY: PANEL2, BLUE: DKBLUE, GREEN: DKGREEN, RED: DKRED, AMBER: DKAMBER}[c]
        rect(s, x, 4.4, wd - 0.04, 0.8, fill=f, line=c, lw=1.25, radius=0.04)
        add_text(s, x, 4.4, wd - 0.04, 0.8, t, size=12, bold=True, color=TEXT if c == GREY else c, align="c", anchor="m")
        add_text(s, x - 0.02, 5.27, max(wd, 0.9), 1.2, d, size=11, color=TEXT, align="l", check=False)
        x += wd
    add_text(s, MX, 6.35, 12.1, 0.45,
             "Say 'illustrative sample image' and 'synthetic demo fleet' on camera. Record the live site, not slides.",
             size=13, color=MUTED, italic=True)

    # ---------------------------------------------------------------- 20 Closing
    s = D.new("", "", notes=(
        "Close with the line. Factories will keep growing, and they should: they are how the world makes things. What changes is how. "
        "Fix the machine, cut the smoke, keep the growth. TerraVow gives every plant a way to see what a worn machine is really costing, in rupees and in emissions, "
        "to choose a greener fix that a human approves, and to see how incentives could pay for it. "
        "What we need next: a pilot plant in a highly polluting sector, help verifying India scheme terms with the issuing authorities, and your feedback today. "
        "Please try it live. The link, the repo and the video are on the screen. Thank you."),
        visual="Dark closing slide with big line and call-to-action cards", number=False)
    rect(s, 8.9, 0.5, 5.2, 5.2, fill=None, line=GREEN, lw=1.0, shape=MSO_SHAPE.OVAL)
    rect(s, 9.4, 1.0, 4.2, 4.2, fill=None, line=BLUE, lw=0.75, shape=MSO_SHAPE.OVAL)
    add_text(s, MX, 0.7, 6, 0.4, "TERRAVOW", size=14, bold=True, color=GREEN)
    add_text(s, MX, 1.3, 9.2, 2.9, ["Fix the machine.", "Cut the smoke.", "Keep the growth."], size=54, bold=True, line_sp=1.0)
    add_text(s, MX, 4.2, 9.0, 0.9, "Factories will keep growing. TerraVow makes sure the growth is clean.", size=20, color=GREEN)
    asks = [("Try it", "[Live URL]"), ("Read it", "[Repo URL]"), ("Pilot it", "[Contact]")]
    for i, (a, b) in enumerate(asks):
        x = MX + i * 3.0
        card(s, x, 5.35, 2.85, 1.0, [{"t": a, "size": 14, "bold": True, "color": GREEN}, {"t": b, "size": 15, "color": AMBER, "bold": True}],
             fill=PANEL, line=GREEN, align="c", anchor="m", lw=1.25)
    add_text(s, MX, 6.55, 12, 0.5,
             "We are looking for: one pilot plant in a highly polluting sector  |  help verifying India scheme terms  |  your feedback",
             size=13, color=MUTED)

    # ================================================================= APPENDIX
    # A1 architecture detail (native, legible)
    s = D.new("Appendix A | Component map, as built", "Appendix",
              notes=(
                  "Backup slide for technical questions. This is the as-built component map of the Python prototype, drawn as layers. "
                  "The single-page app calls FastAPI routes. Below them sit the domain modules: industries, catalogue, templates, snags, sustainability, and the deterministic engine. "
                  "Underneath are the shared services: cache_once, which is the only path to Gemini for templates, specs and alternatives; the Gemini client; the Decision Gate; the store with memory and Firestore backends; and idempotent seeding. "
                  "Externally there is Gemini 2.5 Flash with Google Search grounding, and nine Firestore collections. The full Mermaid rendering is in docs/deck/assets/diagram_components.png."),
              sources=["docs/system-architecture.md component map (full Mermaid render: docs/deck/assets/diagram_components.png)"],
              footer="Source: docs/system-architecture.md (Component Map). Simplified into layers for legibility; the full-resolution diagram is in docs/deck/assets/.",
              visual="Native layered component map (browser, routes, domain modules, services, externals, storage)", appendix=True)
    card(s, MX, 1.75, 2.6, 0.8, [{"t": "Browser", "size": 15, "bold": True}, {"t": "index.html + vanilla JS", "size": 12, "color": MUTED}],
         fill=PANEL, line=BORDER, align="c")
    arrow(s, MX + 2.62, 2.15, MX + 3.0, 2.15, color=GREEN, w=2.5)
    card(s, MX + 3.02, 1.75, 9.1, 0.8,
         [{"t": [("FastAPI  ", {"bold": True, "color": GREEN, "size": 15}),
                 ("main.py (app, startup seeding, /api/health)  +  routes_ext.py (all /api endpoints)", {"size": 13})]}],
         fill="0E141B", line=GREEN, lw=1.5, align="l", inset=0.2)
    add_text(s, MX, 2.62, 6, 0.28, "DOMAIN MODULES", size=11, bold=True, color=MUTED)
    mods = [("industries.py", "4 industries, domains, model_key", PANEL, BORDER, TEXT),
            ("catalogue.py", "search, manual entry", PANEL, BORDER, TEXT),
            ("templates.py", "validate, form schema, base templates", PANEL, BORDER, TEXT),
            ("snags.py", "submit, redact, transitions, queue", PANEL, BORDER, TEXT),
            ("sustain.py", "alternatives, incentives, gate use", PANEL, BORDER, TEXT),
            ("engine.py", "health, lifespan, intensity, economics", DKGREEN, GREEN, GREEN)]
    mw = 1.93
    for i, (t, d, f, l, c) in enumerate(mods):
        x = MX + i * (mw + 0.11)
        card(s, x, 2.9, mw, 1.15, [{"t": t, "size": 13, "bold": True, "color": c}, {"t": d, "size": 11, "color": MUTED}],
             fill=f, line=l, align="c", inset=0.06)
    add_text(s, MX, 4.12, 6, 0.28, "SHARED SERVICES", size=11, bold=True, color=MUTED)
    svc = [("cache_once.py", "Gemini at most once per key", DKBLUE, BLUE, BLUE),
           ("gemini_client.py", "template, specs, alternatives", DKBLUE, BLUE, BLUE),
           ("gate.py", "hard veto, then confidence", DKRED, RED, RED),
           ("store.py", "memory or Firestore, create_if_absent", DKAMBER, AMBER, AMBER),
           ("seed.py", "idempotent seeding and reset", PANEL, BORDER, TEXT)]
    sw = 2.33
    for i, (t, d, f, l, c) in enumerate(svc):
        x = MX + i * (sw + 0.12)
        card(s, x, 4.4, sw, 1.05, [{"t": t, "size": 13, "bold": True, "color": c}, {"t": d, "size": 11, "color": MUTED}],
             fill=f, line=l, align="c", inset=0.06)
    add_text(s, MX, 5.52, 6, 0.28, "EXTERNAL AND STORAGE", size=11, bold=True, color=MUTED)
    card(s, MX, 5.8, 4.7, 0.95,
         [{"t": "Gemini 2.5 Flash  +  Google Search grounding", "size": 13, "bold": True, "color": BLUE},
          {"t": "AI Studio or Vertex AI; structured output, bounded retry", "size": 11, "color": MUTED}],
         fill=DKBLUE, line=BLUE, align="c")
    card(s, MX + 4.85, 5.8, 7.28, 0.95,
         [{"t": "Storage: Firestore, or in-memory fallback", "size": 13, "bold": True, "color": AMBER},
          {"t": "industries, catalogue, templates, snag_history, snags, alternatives, incentives, seed_meta, audit (hash-chained)",
           "size": 11, "color": MUTED}],
         fill=DKAMBER, line=AMBER, align="c")

    # A2 data model (native, legible)
    s = D.new("Appendix B | Data model: nine collections, one audit chain", "Appendix",
              notes=(
                  "Backup slide on data. Nine collections hold everything. Industries and the sample catalogue are reference data. "
                  "Templates and alternatives are the cached AI outputs, and both refuse to store any document whose source is not AI, so a fallback can never poison the cache. "
                  "Snag history holds one header per machine and snags hold the append-only event log. Incentives are seeded and labelled unverified or illustrative. "
                  "Seed metadata drives idempotent seeding and demo reset, and the audit collection is an append-only hash chain that survives a reset. "
                  "The full entity-relationship diagram is in docs/deck/assets/diagram_datamodel.png."),
              sources=["docs/system-architecture.md Data Model and Collection Details (full ER render: docs/deck/assets/diagram_datamodel.png)"],
              footer="Source: docs/system-architecture.md. Catalogue is sample data; seeded snags and incentives are synthetic or illustrative.",
              visual="Native 3x3 grid of collection cards with relationship arrows", appendix=True)
    cols = [
        [("industries", "key: id", "4 industries, their pollution domains and default baselines. Never reset.", PANEL, BORDER, TEXT),
         ("catalogue", "key: industry--manufacturer--model", "20 sample models (5 per industry) plus manual entries. Illustrative economics.", PANEL, BORDER, TEXT),
         ("templates", "key: model_key--schema version", "AI documents only; fallbacks are never stored. Model, time, grounding recorded.", DKBLUE, BLUE, BLUE)],
        [("snag_history", "key: machine_id", "Machine header: tag, install year, model. 6 seeded fleet machines.", PANEL, BORDER, TEXT),
         ("snags", "key: SNG-12hex", "Status OPEN to CLOSED, append-only events, redacted answers. 7 seeded open.", PANEL, BORDER, TEXT),
         ("alternatives", "key: model_key--country--version", "AI suggestions cached per model and country. Curated fallback.", DKBLUE, BLUE, BLUE)],
        [("incentives", "key: country--scheme_id", "9 seeded: 7 real-named UNVERIFIED, 2 demo-synthetic ILLUSTRATIVE.", DKAMBER, AMBER, AMBER),
         ("seed_meta", "key: anchor | incentives | complete", "Anchor date and seed version. Drives idempotent seeding and reset.", PANEL, BORDER, TEXT),
         ("audit", "key: AUD-timestamp-hex", "Append-only. entry_hash = sha256(prior hash + entry). Kept on reset.", DKGREEN, GREEN, GREEN)],
    ]
    cw, ch_, gx, gy = 3.7, 1.5, 0.515, 0.15
    # rows x cols grid: row r, col c
    for r in range(3):
        for c in range(3):
            t, k, d, f, l, tc = cols[r][c]
            x = MX + c * (cw + gx)
            y = 1.75 + r * (ch_ + gy)
            rect(s, x, y, cw, ch_, fill=f, line=l, lw=1.25)
            add_text(s, x + 0.12, y + 0.04, cw - 0.24, 0.38, t, size=16, bold=True, color=tc)
            add_text(s, x + 0.12, y + 0.4, cw - 0.24, 0.28, k, size=11, color=MUTED)
            add_text(s, x + 0.12, y + 0.7, cw - 0.24, 0.82, d, size=12)
            if r < 2 and c < 2 and r == 0:
                pass
    # relationship arrows in the horizontal gaps (row 0 and row 1)
    for r, labels in [(0, ["industry", "model_key"]), (1, ["machine_id", "model_key"])]:
        for c in range(2):
            x1 = MX + (c + 1) * cw + c * gx + 0.02
            yy = 1.75 + r * (ch_ + gy) + ch_ / 2
            arrow(s, x1, yy, x1 + gx - 0.04, yy, color=GREEN, w=2.25)
    add_text(s, MX, 6.75, 12.1, 0.25, "Arrows: foreign-key style links (industry, model_key, machine_id). Full ER diagram: docs/deck/assets/diagram_datamodel.png",
             size=10, color=MUTED)

    # A3 user flow diagrams (native lanes, legible)
    s = D.new("Appendix C | The four user flows, step by step", "Appendix",
              notes=(
                  "Backup slide with the four flows from the repo's user-flow document, condensed into lanes. "
                  "Reporter: choose an industry, search or add the machine, get the template through the cache-once path, pick or register an instance, fill the dynamic form, validation and redaction, then a guidance card. "
                  "Resolver: open the severity-first queue, accept, resolve with notes, downtime and cost, close, which writes the audit entry and recomputes health. "
                  "Health: replay the snag history, compute score, lifespan, pollution intensity and a twelve-point timeline. "
                  "Sustainability: choose machine and enterprise size, get cached AI or curated alternatives, compute economics in the engine, pass the Decision Gate, apply incentives, show adjusted payback. "
                  "Blue steps involve Gemini, green steps are the deterministic engine, amber steps are fallbacks and red steps are the safety gate. "
                  "The complete flowcharts are in docs/user-flow.md and rendered in docs/deck/assets/diagram_*.png."),
              sources=["docs/user-flow.md (full Mermaid renders: docs/deck/assets/diagram_reporter.png, diagram_resolver.png, diagram_health.png, diagram_sustain.png)"],
              footer="Source: docs/user-flow.md, condensed. Blue = Gemini involved, green = deterministic engine, amber = fallback, red = safety gate. Full flowcharts in docs/deck/assets/.",
              visual="Four native lanes of colour-coded steps (Reporter, Resolver, Health, Sustainability)", appendix=True)
    lanes = [
        ("Reporter", [("Pick industry", N0), ("Search or add model", BL), ("Template via cache-once", BL), ("Pick or register machine", N0),
                      ("Fill dynamic form", N0), ("Validate + redact", GR), ("Guidance card", N0)]),
        ("Resolver", [("Severity-first queue", N0), ("Accept: OPEN to ASSIGNED", N0), ("Resolve: notes, downtime, cost", N0),
                      ("Close: RESOLVED to CLOSED", N0), ("Audit entry", GR), ("Health recomputed", GR)]),
        ("Health", [("Select machine", N0), ("Replay snag history", GR), ("Health score", GR), ("Remaining lifespan", GR),
                    ("Pollution intensity", GR), ("12-point timeline", GR)]),
        ("Sustainability", [("Machine + MSME or Large", N0), ("Current usage", GR), ("AI (cached) or curated options", BL),
                            ("Economics in engine", GR), ("Decision Gate", RD), ("Incentives applied", AM), ("Adjusted payback", GR)]),
    ]
    sty = {N0: (PANEL2, BORDER, TEXT), BL: (DKBLUE, BLUE, TEXT), GR: (DKGREEN, GREEN, TEXT), RD: (DKRED, RED, TEXT), AM: (DKAMBER, AMBER, TEXT)}
    for li, (name, steps) in enumerate(lanes):
        y = 1.75 + li * 1.28
        card(s, MX, y, 1.45, 1.1, name, fill=PANEL, line=GREEN, size=13, bold=True, color=GREEN, align="c")
        n = len(steps)
        avail = 12.13 - 1.6
        gap = 0.16
        bw_ = (avail - gap * (n - 1)) / n
        for si, (txt, kind) in enumerate(steps):
            f, l, c = sty[kind]
            x = MX + 1.6 + si * (bw_ + gap)
            card(s, x, y, bw_, 1.1, txt, fill=f, line=l, size=12, color=c, bold=True, align="c", inset=0.05)
            if si < n - 1:
                arrow(s, x + bw_ + 0.01, y + 0.55, x + bw_ + gap - 0.01, y + 0.55, color=MUTED, w=1.5)

    # A4 API list
    s = D.new("Appendix D | API surface", "Appendix",
              notes=(
                  "Backup slide listing the HTTP API. Industries and catalogue; the template ensure endpoint, which is the cache-once entry point, and a fetch-only twin that never calls Gemini; "
                  "machine instances and snags with the role-gated accept, resolve and close transitions; the resolver queue; health, which returns score, lifespan, pollution and a twelve-point timeline; "
                  "recommendations and incentives; and system endpoints for health, demo reset and audit chain integrity. All write endpoints validate every field and return field-level errors."),
              sources=["prototype/README.md 'API Endpoints'; docs/system-architecture.md 'Key API Endpoints'"],
              footer="Source: prototype/README.md. 422 = validation, 403 = wrong role, 409 = invalid transition, 429 = cap reached.",
              visual="Compact endpoint table", appendix=True)
    rows = [["Method", "Path", "Purpose"],
            ["GET", "/api/industries", "Four industries and their pollution domains"],
            ["GET", "/api/catalogue/search", "Scoped, accent-insensitive search (max 10)"],
            ["POST", "/api/catalogue/manual", "Add a model; Gemini + Google Search looks up specs"],
            ["POST", "/api/template/ensure", "Cache-once get-or-generate template (Gemini only if absent)"],
            ["GET", "/api/template/{key}", "Fetch stored template; never calls Gemini"],
            ["POST", "/api/instances", "Register a machine instance"],
            ["POST", "/api/snags", "Submit snag: validated, redacted, guidance returned"],
            ["GET", "/api/resolver/queue", "Open and assigned snags, severity first"],
            ["POST", "/api/snags/{id}/accept|resolve|close", "Role-gated transitions; close recomputes health"],
            ["GET", "/api/machines/{id}/health", "Score, lifespan, pollution intensity, 12-point timeline"],
            ["POST", "/api/machines/{id}/recommendations", "Alternatives, economics, gate verdicts, incentives"],
            ["GET", "/api/incentives", "India schemes by industry and enterprise size"],
            ["GET", "/api/health  |  /api/registry", "Service status  |  audit hash-chain integrity"],
            ["POST", "/api/demo/reset", "Restore seeds, keep audit and cached templates"]]
    grid_rows(s, MX, 1.75, [0.95, 4.6, 6.58], 0.335, rows, size=12, aligns=["c", "l", "l"])

    # A5 risks and limitations
    s = D.new("Appendix E | Risks and honest limitations", "Appendix",
              notes=(
                  "Backup slide on what we have not done and what could go wrong. On the left, limitations of the prototype: the numbers are illustrative models, the catalogue is sample data, "
                  "India incentives are mostly unverified, there is no authentication, cache-once is not safe across replicas yet, cached AI alternatives never expire, template latency has not been measured with a live key, "
                  "and the Java reference engine is separate from the Python demo path. On the right, product risks and how we would address them: incumbents adding emissions modules, cold-start on real data, "
                  "and over-trust in AI output. Our mitigations are the deterministic engine, the gate, the labels and human confirmation."),
              sources=["prototype/README.md 'What is NOT built' and 'Honesty'; docs/product-features.md section 10; docs/competitive-landscape.md 'Where we have real competitors'"],
              footer="Source: prototype/README.md, docs/product-features.md, docs/competitive-landscape.md.",
              visual="Two columns: limitations and risks with mitigations", appendix=True)
    lim = ["Health, intensity, economics use illustrative assumptions; not calibrated on real machines",
           "Catalogue is 20 sample models; seeded snag histories are synthetic",
           "India incentives mostly UNVERIFIED; quantified subsidies are demo-synthetic",
           "No authentication; role switch is workflow guidance",
           "Cache-once is single-process safe; cross-replica lease not built",
           "Cached AI alternatives never expire; latency not yet measured live",
           "Java reference engine is separate from the Python demo path",
           "Not an official certification; outputs need qualified-engineer review"]
    rect(s, MX, 1.75, 6.5, 5.0, fill=PANEL, line=AMBER, lw=1.25)
    add_text(s, MX + 0.2, 1.82, 6.0, 0.35, "LIMITATIONS OF THE PROTOTYPE", size=12, bold=True, color=AMBER)
    add_text(s, MX + 0.2, 2.2, 6.1, 4.5, [{"t": [("\u2022 ", {"color": AMBER, "bold": True}), (t, {})], "space_after": 9} for t in lim], size=15)
    rk = [("Incumbents add emissions modules", "Win on the joined-up flow, the safety veto and India incentive logic."),
          ("Cold start on real data", "Pilot plants; start from snag logs people already keep."),
          ("Over-trust in AI output", "Engine-only numbers, hard veto, labels, human confirmation.")]
    for i, (a, b) in enumerate(rk):
        y = 1.75 + i * 1.68
        rect(s, 7.3, y, 5.43, 1.55, fill=PANEL, line=BORDER)
        add_text(s, 7.45, y + 0.08, 5.15, 0.4, a, size=15, bold=True, color=RED)
        add_text(s, 7.45, y + 0.5, 5.15, 0.3, "MITIGATION", size=10, bold=True, color=MUTED)
        add_text(s, 7.45, y + 0.78, 5.15, 0.75, b, size=13)

    return D


# ----------------------------------------------------------------------------- outline writer
VIDEO_SCRIPT = [
    ("0:00-0:15", "Slide 1-2 (screen: paradox slide, then cut to the live app)",
     "\"Everything you own was made in a factory. Manufacturing is about 15 percent of world GDP, and industry produces up to a third of greenhouse gas emissions. "
     "The engine of growth is also a leading polluter. TerraVow helps factories keep growing while getting cleaner.\""),
    ("0:15-0:35", "Live app, Report a snag tab",
     "\"A reporter picks an industry and searches for their machine. First use of a model, Gemini drafts a deep template: job types, snag types, parameters. It is generated once and cached, "
     "so the next person gets it instantly.\" (Show the cache-status badge.)"),
    ("0:35-1:00", "Live app, submit a snag",
     "\"They fill a form built for this exact model. Phone numbers and emails are stripped. The guidance card says what to do, how long it takes and the hazards.\""),
    ("1:00-1:20", "Live app, Resolver queue",
     "\"The resolver sees the worst snags first. Accept, resolve, close. Closing recomputes the machine's health.\" (Say: workflow guidance, no authentication in the demo.)"),
    ("1:20-1:45", "Live app, Machine health on Kiln K-4",
     "\"Health 28, Critical, about 342 days left, an estimate. Effluent and CO2e per day are above the healthy reference. All computed by code, never by the language model. "
     "This is a synthetic demo fleet and these are illustrative numbers.\""),
    ("1:45-2:20", "Live app, Sustainability tab, MSME toggle",
     "\"Greener options with two paybacks. The electric drive does not pay back within design life on cash. We show that. Dust suppression pays back fastest. Toggle MSME or large: incentive-adjusted. "
     "Schemes are marked unverified or illustrative. Every option shows Needs confirmation: a human decides.\""),
    ("2:20-2:40", "Slides 14 and 11 (safety gate, architecture)",
     "\"A hard veto blocks anything that raises total emissions or just moves pollution from air to water, however confident the model is. Gemini reasons and writes; an engine does every calculation. "
     "No key? It still works with labelled fallbacks.\""),
    ("2:40-2:50", "Slide 20 (close)",
     "\"Fix the machine. Cut the smoke. Keep the growth. Try TerraVow at the link below.\""),
]


def write_outline(D):
    L = []
    L.append("# TerraVow Hackathon Deck: outline, speaker notes, sources, video script\n")
    L.append("Generated by `docs/deck/build_deck.py`. Deck file: `TerraVow-Hackathon-Deck.pptx` (PDF export alongside if produced).\n")
    L.append("Prototype numbers (health, lifespan, pollution, payback) are illustrative outputs of the deterministic engine on the synthetic demo fleet. "
             "India incentive schemes are mostly UNVERIFIED/illustrative.\n")
    L.append("## Slide list\n")
    for m in D.meta:
        tag = "Appendix" if m["appendix"] else "Main"
        L.append(f"{m['n']}. **{m['title'] or ('Title slide' if m['n'] == 1 else 'Closing slide')}** ({tag})")
    L.append("\n## Slide-by-slide outline and speaker notes\n")
    for m in D.meta:
        title = m["title"] or ("TerraVow: Healthy machines. Cleaner factories. Growth that lasts." if m["n"] == 1
                               else "Fix the machine. Cut the smoke. Keep the growth.")
        L.append(f"### Slide {m['n']}: {title}")
        if m["kicker"]:
            L.append(f"*Section:* {m['kicker']}")
        L.append(f"*Visual:* {m['visual']}\n")
        L.append("**Speaker notes (30-60 s):**\n")
        L.append(m["notes"] + "\n")
        if m["sources"]:
            L.append("**Sources / references:**\n")
            for sname in m["sources"]:
                L.append(f"- {sname}")
            L.append("")
    L.append("## External statistics used (all looked up and checked at the source)\n")
    L.append("| ID | Statistic | Source | URL | Slide(s) |\n|---|---|---|---|---|")
    for st in STATS:
        u = st["url"] + (f" (API: {st['api']})" if st.get("api") else "")
        L.append(f"| {st['id']} | {st['stat']} | {st['src']} | {u} | {st['slide']} |")
    L.append("\nNot verified and therefore NOT used: any global/Indian textile-dyeing share of industrial water pollution, any dollar market size, any vendor-reported "
             "metric (e.g. Augury fleet size, energy-saving claims) from the competitive document.\n")
    L.append("Scope caveat: World Bank 'manufacturing value added' and the IPCC 'industry' sector are different scopes, so slide 2 does not divide one by the other.\n")
    L.append("## Prototype figures used (illustrative, from the deterministic engine, synthetic demo fleet)\n")
    L.append("Reproduce: run the prototype locally (`prototype/run_local.sh`, no Gemini key needed) then call `GET /api/machines/K-4/health` and "
             "`POST /api/machines/K-4/recommendations {\"enterprise_size\":\"MSME\"}`.\n")
    L.append("- Kiln K-4: health 28.4 (Critical), remaining lifespan ~342 days (range 257-428), age 18.8 of 25 design years.")
    L.append("- Effluent 918.44 m3/day vs healthy reference 800 (+14.8%); CO2e 45,549.6 kg/day vs 43,200 (+5.4%); excess about 118 m3 and 2.35 t CO2e per day.")
    L.append("- Health timeline (Nov to Oct): " + ", ".join(str(v) for v in K4_HEALTH) + ".")
    L.append("- MSME incentive-adjusted paybacks (cash / incl. exposure proxy, years): " +
             "; ".join(f"{n} {a} / {b}" for n, a, b in PAYBACK) + "; dry-stack tailings with water recycling: no payback.")
    L.append("- Subsidy applied is the demo-synthetic `demo-capital-subsidy-msme` (ILLUSTRATIVE). Alternatives were the curated fallback set (Gemini not configured).\n")
    L.append("## 3-minute demo video script (matches the slides; target 2:50, hard limit 3:00)\n")
    L.append("| Time | Screen | Narration |\n|---|---|---|")
    for t, sc, nar in VIDEO_SCRIPT:
        L.append(f"| {t} | {sc} | {nar} |")
    L.append("\nOn-camera disclosures: say \"synthetic demo fleet\", \"illustrative numbers\" and, for the demo photos in `prototype/static/samples/`, \"illustrative sample image\".\n")
    L.append("## Placeholders the team must fill\n")
    L.append("- Slide 1: team name, hackathon and theme label, date\n- Slide 19: team member names and roles; live Cloud Run URL; GitHub repo URL; demo video URL; deck PDF link\n"
             "- Slide 20: live URL, repo URL, contact for pilots\n")
    OUT_MD.write_text("\n".join(L), encoding="utf-8")


if __name__ == "__main__":
    deck = build()
    deck.prs.save(str(OUT_PPTX))
    write_outline(deck)
    print(f"saved {OUT_PPTX} ({len(deck.prs.slides)} slides)")
    for w in WARNINGS:
        print("WARN", w)
