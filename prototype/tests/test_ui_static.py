"""
UI safety and accessibility static scan (task 11, Property 82).

Verifies that no unsafe HTML injection patterns exist and that ALLOW/ASK/BLOCK
always carry both text and a glyph (never colour alone).
"""

import re
from pathlib import Path


def test_no_unsafe_html_injection():
    """
    Property 82 part 1: No new unsafe HTML injection patterns.
    
    Scans static/index.html for prohibited patterns:
    - innerHTML (except exactly two allow-listed numeric-only uses)
    - outerHTML
    - insertAdjacentHTML
    - document.write
    - eval
    - new Function
    - inline event handlers (onclick=, onerror=, etc.)
    - javascript: URLs
    
    This is a static scan; manual hostile-string verification is recorded separately.
    """
    html_path = Path(__file__).parent.parent / "static" / "index.html"
    content = html_path.read_text(encoding="utf-8")
    
    # Find all innerHTML occurrences
    inner_html_matches = list(re.finditer(r'\.innerHTML\s*=', content, re.IGNORECASE))
    
    # Exactly 2 are allowed: both are numeric-only SVG rendering (chart and cursor)
    assert len(inner_html_matches) == 2, (
        f"Expected exactly 2 innerHTML uses (allow-listed numeric SVG), found {len(inner_html_matches)}"
    )
    
    # Verify both are in the SVG chart rendering context
    for match in inner_html_matches:
        start = max(0, match.start() - 200)
        end = min(len(content), match.end() + 200)
        context = content[start:end]
        # Should be in chart-related code with numeric coordinates
        assert 'chart' in context.lower() or 'cursor' in context.lower(), (
            f"innerHTML at position {match.start()} not in recognized chart context"
        )
    
    # No other unsafe patterns
    assert not re.search(r'\.outerHTML\s*=', content, re.IGNORECASE), "outerHTML assignment found"
    assert not re.search(r'insertAdjacentHTML\s*\(', content, re.IGNORECASE), "insertAdjacentHTML found"
    assert not re.search(r'document\.write\s*\(', content, re.IGNORECASE), "document.write found"
    
    # eval and new Function (not in strings)
    # Simple check: look for eval( or new Function( outside of comments
    lines_no_comments = [
        line.split('//')[0] for line in content.split('\n')
        if not line.strip().startswith('*') and not line.strip().startswith('//')
    ]
    code_no_comments = '\n'.join(lines_no_comments)
    
    assert not re.search(r'\beval\s*\(', code_no_comments), "eval() found"
    assert not re.search(r'\bnew\s+Function\s*\(', code_no_comments), "new Function() found"
    
    # No inline event handlers in HTML tags
    # Match on* attributes (onclick, onerror, onload, etc.)
    inline_handlers = re.findall(r'<[^>]+\s+on\w+\s*=', content, re.IGNORECASE)
    assert len(inline_handlers) == 0, f"Inline event handlers found: {inline_handlers}"
    
    # No javascript: URLs
    js_urls = re.findall(r'href\s*=\s*["\']javascript:', content, re.IGNORECASE)
    assert len(js_urls) == 0, f"javascript: URLs found: {js_urls}"


def test_color_scheme_light_only():
    """
    Property 82 part 2: Dark mode block is neutralized and light theme is enforced.
    
    Verifies:
    - No prefers-color-scheme: dark block
    - color-scheme: light is set
    """
    html_path = Path(__file__).parent.parent / "static" / "index.html"
    content = html_path.read_text(encoding="utf-8")
    
    # No dark mode block
    assert 'prefers-color-scheme: dark' not in content, (
        "prefers-color-scheme: dark block found (should be removed per Req 29.4)"
    )
    
    # Light theme enforced
    assert 'color-scheme:light' in content or 'color-scheme: light' in content, (
        "color-scheme: light not found"
    )


def test_gate_decisions_text_and_glyph():
    """
    Property 82 part 3: ALLOW/ASK/BLOCK always carry text + glyph, never colour alone.
    
    Verifies that .badge.ALLOW, .badge.ASK, .badge.BLOCK all have ::before content
    with glyphs (✓, ?, ✕).
    """
    html_path = Path(__file__).parent.parent / "static" / "index.html"
    content = html_path.read_text(encoding="utf-8")
    
    # Find the CSS rules for badge glyphs
    # Should have .badge.ALLOW::before, .badge.ASK::before, .badge.BLOCK::before
    assert re.search(r'\.badge\.ALLOW::before\{content:', content), (
        ".badge.ALLOW::before glyph not found"
    )
    assert re.search(r'\.badge\.ASK::before\{content:', content), (
        ".badge.ASK::before glyph not found"
    )
    assert re.search(r'\.badge\.BLOCK::before\{content:', content), (
        ".badge.BLOCK::before glyph not found"
    )
    
    # Verify the comment states: "state never depends on colour alone: text plus a glyph"
    assert 'state never depends on colour alone' in content, (
        "Accessibility comment about text+glyph not found"
    )


def test_accessibility_basics():
    """
    Property 82 part 4: Basic accessibility attributes.
    
    Spot-checks for:
    - Tab roles and aria-selected
    - aria-live on dynamic content
    - Combobox aria attributes
    - Label/for pairs
    """
    html_path = Path(__file__).parent.parent / "static" / "index.html"
    content = html_path.read_text(encoding="utf-8")
    
    # Tab roles
    assert 'role="tab"' in content, "Tab roles not found"
    assert 'aria-selected' in content, "aria-selected not found"
    assert 'role="tablist"' in content, "tablist role not found"
    assert 'role="tabpanel"' in content, "tabpanel role not found"
    
    # aria-live on status/error elements
    assert 'aria-live="polite"' in content, "aria-live not found for dynamic content"
    
    # Combobox for autocomplete
    assert 'role="combobox"' in content, "Combobox role not found"
    assert 'aria-autocomplete="list"' in content, "aria-autocomplete not found"
    assert 'role="listbox"' in content, "Listbox role not found"
    
    # Error elements have role="alert"
    assert 'role="alert"' in content, "Alert role not found for errors"
    
    # Some labels with for attributes exist
    assert 'label' in content.lower() and 'for=' in content, "Label/for pairs not found"
