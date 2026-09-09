#!/usr/bin/env python3
"""Generate a double-clickable local HTML user guide from docs/user-guide."""

from __future__ import annotations

import os
import re
import shutil
import sys
from dataclasses import dataclass, field
from html import escape
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "user-guide"
DST = ROOT / "说明书"
LIB = ROOT / "_mdlib"
sys.path.insert(0, str(LIB))

import markdown  # noqa: E402

HINT_RE = re.compile(
    r'\{%\s*hint\s+style="([^"]+)"\s*%\}(.*?)\{%\s*endhint\s*%\}',
    re.S,
)
MD_HREF_RE = re.compile(r'href="([^"]+?)\.md(#[^"]*)?"')
SKIP = {"SUMMARY.md", "LANGS.md"}

ZH_SLUGS = {
    "入门": "intro",
    "安装与运维": "install",
    "总览": "overview",
    "资产中心": "asset-center",
    "设备与空间": "devices",
    "告警中心": "alerts",
    "数据分析": "analytics",
    "MQTT 接入": "mqtt",
    "平台管理": "platform",
    "个人中心": "account",
    "附录": "appendix",
}
EN_SLUGS = {
    "Getting started": "intro",
    "Install & operations": "install",
    "Overview": "overview",
    "Asset Center": "asset-center",
    "Devices & Space": "devices",
    "Alert Center": "alerts",
    "Analytics": "analytics",
    "MQTT Integration": "mqtt",
    "Platform Administration": "platform",
    "Account": "account",
    "Appendix": "appendix",
}


@dataclass
class Group:
    lang: str
    title: str
    items: list[tuple[str, str]] = field(default_factory=list)

    @property
    def slug(self) -> str:
        table = ZH_SLUGS if self.lang == "zh" else EN_SLUGS
        return table.get(self.title, re.sub(r"[^a-z0-9]+", "-", self.title.lower()).strip("-"))

    @property
    def group_page(self) -> str | None:
        if len(self.items) <= 1:
            return None
        return f"{self.lang}/groups/{self.slug}.html"


def strip_frontmatter(text: str) -> str:
    if text.startswith("---"):
        end = text.find("\n---", 3)
        if end != -1:
            return text[end + 4 :].lstrip("\n")
    return text


def hints_to_html(text: str) -> str:
    def repl(m: re.Match[str]) -> str:
        return f'\n\n<div class="hint {m.group(1)}">\n\n{m.group(2).strip()}\n\n</div>\n\n'

    return HINT_RE.sub(repl, text)


def mermaid_to_pre(text: str) -> str:
    def repl(m: re.Match[str]) -> str:
        return f"\n\n<pre class=\"diagram\">{escape(m.group(1).strip())}</pre>\n\n"

    return re.sub(r"```mermaid\n(.*?)```", repl, text, flags=re.S)


def parse_groups(summary: str) -> list[Group]:
    groups: list[Group] = []
    current: Group | None = None
    for line in summary.splitlines():
        if line.startswith("## "):
            raw = line[3:].strip()
            if raw.startswith("中文 · "):
                current = Group("zh", raw[len("中文 · ") :])
            elif raw.startswith("English · "):
                current = Group("en", raw[len("English · ") :])
            else:
                current = Group("zh", raw)
            groups.append(current)
            continue
        m = re.match(r"\* \[(.+?)\]\((.+?)\)", line.strip())
        if m and current is not None:
            current.items.append((m.group(1), m.group(2)))
    return groups


def html_name(md_rel: str) -> str:
    p = Path(md_rel)
    stem = p.with_suffix("") if p.suffix == ".md" else p
    if stem.as_posix() in {"README", "./README"}:
        return "index.html"
    return str(stem.with_suffix(".html"))


def rel_href(from_html: Path, to_html: Path) -> str:
    return Path(os.path.relpath(to_html, from_html.parent)).as_posix()


def render_md(text: str) -> str:
    text = strip_frontmatter(text)
    text = hints_to_html(text)
    text = mermaid_to_pre(text)
    md = markdown.Markdown(extensions=["extra", "sane_lists", "tables"])
    html = md.convert(text)
    html = MD_HREF_RE.sub(lambda m: f'href="{html_name(m.group(1))}{m.group(2) or ""}"', html)
    return html


NAV_CSS = """
:root {
  --bg: #f6f7f9;
  --panel: #fff;
  --text: #1f2328;
  --muted: #5c6570;
  --line: #e6e8ec;
  --brand: #1a5f4a;
  --link: #0b6e4f;
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; height: 100%; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Noto Sans SC", sans-serif;
  color: var(--text);
  background: var(--bg);
  display: flex;
  height: 100vh;
  overflow: hidden;
}
nav {
  width: 280px;
  flex: 0 0 280px;
  background: var(--panel);
  border-right: 1px solid var(--line);
  padding: 16px 12px 32px;
  overflow-y: auto;
  height: 100vh;
}
nav .brand { font-weight: 700; color: var(--brand); padding: 4px 8px 14px; font-size: 15px; }
nav .brand a { color: inherit; text-decoration: none; }
nav .top-link { margin-bottom: 10px; }
nav .section { margin-bottom: 4px; }
nav .parent {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
  margin: 0;
  padding: 7px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  text-decoration: none;
  text-align: left;
  cursor: pointer;
}
nav .parent:hover { background: #eef6f2; }
nav .parent.current, nav .parent.active { color: var(--brand); }
nav .chevron {
  display: inline-block;
  width: 0;
  height: 0;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  border-left: 5px solid currentColor;
  flex: 0 0 auto;
  transform: rotate(0deg);
  transition: transform 0.15s ease;
}
nav .section.open > .parent .chevron { transform: rotate(90deg); }
nav .children { display: none; padding: 0 0 8px 20px; }
nav .section.open > .children { display: block; }
nav a.item {
  display: block;
  padding: 5px 8px;
  border-radius: 6px;
  color: var(--muted);
  text-decoration: none;
  font-size: 13px;
  line-height: 1.4;
  font-weight: 400;
}
nav a.item:hover { background: #eef6f2; color: var(--text); }
nav a.item.active { background: #1a5f4a; color: #fff; }
main { flex: 1; padding: 32px 40px 80px; overflow-y: auto; height: 100vh; }
article { background: var(--panel); border: 1px solid var(--line); border-radius: 12px; padding: 28px 36px; max-width: 860px; }
h1 { margin-top: 0; font-size: 28px; }
h2 { margin-top: 1.6em; font-size: 20px; border-bottom: 1px solid var(--line); padding-bottom: 6px; }
h3 { font-size: 16px; }
p, li { line-height: 1.7; }
a { color: var(--link); }
table { border-collapse: collapse; width: 100%; margin: 12px 0 20px; font-size: 14px; }
th, td { border: 1px solid var(--line); padding: 8px 10px; text-align: left; vertical-align: top; }
th { background: #f3f5f7; }
code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.9em; background: #f3f5f7; padding: 1px 5px; border-radius: 4px; }
pre { background: #111827; color: #e5e7eb; padding: 14px 16px; border-radius: 8px; overflow: auto; }
pre code { background: none; color: inherit; padding: 0; }
pre.diagram { background: #f3f5f7; color: var(--text); font-size: 12px; }
.hint { border-left: 4px solid #3b82f6; background: #eff6ff; padding: 10px 14px; margin: 16px 0; border-radius: 0 8px 8px 0; }
.hint.warning { border-color: #d97706; background: #fffbeb; }
.hint.danger { border-color: #dc2626; background: #fef2f2; }
.hint.success { border-color: #059669; background: #ecfdf5; }
.hint.info { border-color: #2563eb; background: #eff6ff; }
.home-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 28px; }
.home-cards a {
  display: block; background: var(--panel); border: 1px solid var(--line);
  border-radius: 12px; padding: 28px 24px; text-decoration: none; color: var(--text);
}
.home-cards a:hover { border-color: var(--brand); }
.home-cards strong { display: block; font-size: 20px; color: var(--brand); margin-bottom: 8px; }
.group-list { list-style: none; padding: 0; }
.group-list a {
  display: block; padding: 12px 14px; border: 1px solid var(--line);
  border-radius: 8px; margin: 8px 0; text-decoration: none; color: var(--text);
}
.group-list a:hover { border-color: var(--brand); }
@media (max-width: 860px) {
  body { display: block; height: auto; overflow: auto; }
  nav { width: auto; height: auto; }
  main { height: auto; padding: 16px; overflow: visible; }
  .home-cards { grid-template-columns: 1fr; }
}
"""

NAV_JS = """
<script>
(function () {
  var nav = document.querySelector("nav");
  if (!nav) return;
  var scrollKey = "am-guide-nav-scroll";
  var saved = sessionStorage.getItem(scrollKey);
  if (saved !== null) nav.scrollTop = Number(saved);
  nav.querySelectorAll("a").forEach(function (a) {
    a.addEventListener("click", function () {
      sessionStorage.setItem(scrollKey, String(nav.scrollTop));
    });
  });
  nav.querySelectorAll(".section[data-slug]").forEach(function (section) {
    var btn = section.querySelector("button.parent");
    if (!btn) return;
    btn.addEventListener("click", function () {
      var open = !section.classList.contains("open");
      section.classList.toggle("open", open);
      btn.setAttribute("aria-expanded", open ? "true" : "false");
    });
  });
})();
</script>
"""

PAGE_TMPL = """<!DOCTYPE html>
<html lang="{lang}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{title}</title>
  <style>{css}</style>
</head>
<body>
<nav>
  <div class="brand"><a href="{home}">资产管理平台说明书</a></div>
  {nav}
</nav>
<main>
  <article>
    {body}
  </article>
</main>
{js}
</body>
</html>
"""


def page_lang(current: Path, dst: Path) -> str:
    rel = current.resolve().relative_to(dst.resolve()).as_posix()
    if rel == "index.html":
        return "home"
    if rel.startswith("zh/"):
        return "zh"
    if rel.startswith("en/"):
        return "en"
    return "home"


def build_nav(groups: list[Group], current: Path, dst: Path) -> str:
    view = page_lang(current, dst)
    home = rel_href(current, dst / "index.html")
    chunks = [
        f'<a class="item top-link{" active" if view == "home" else ""}" href="{home}">选择语言 / Language</a>'
    ]
    for group in groups:
        if view in {"zh", "en"} and group.lang != view:
            continue
        if view == "home":
            continue
        child_htmls = [dst / html_name(path) for _, path in group.items]
        group_html = dst / group.group_page if group.group_page else None
        in_section = current.resolve() in {p.resolve() for p in child_htmls} or (
            group_html is not None and current.resolve() == group_html.resolve()
        )
        current_cls = " current" if in_section else ""
        if group.group_page:
            parent_active = " active" if group_html and current.resolve() == group_html.resolve() else ""
            open_cls = " open is-current" if in_section else ""
            expanded = "true" if in_section else "false"
            chunks.append(f'<div class="section{open_cls}" data-slug="{escape(group.slug)}">')
            chunks.append(
                f'<button type="button" class="parent{current_cls}{parent_active}" '
                f'aria-expanded="{expanded}"><span class="chevron" aria-hidden="true"></span>'
                f"{escape(group.title)}</button>"
            )
            chunks.append('<div class="children">')
            for title, md_path in group.items:
                target = dst / html_name(md_path)
                href = rel_href(current, target)
                active = " active" if current.resolve() == target.resolve() else ""
                chunks.append(f'<a class="item{active}" href="{href}">{escape(title)}</a>')
            chunks.append("</div></div>")
        else:
            target = dst / html_name(group.items[0][1])
            href = rel_href(current, target)
            active = " active" if current.resolve() == target.resolve() else ""
            chunks.append(
                f'<a class="parent{active}" href="{href}">{escape(group.title)}</a>'
            )
    if view == "home":
        chunks.append(f'<a class="parent" href="{rel_href(current, dst / "zh/README.html")}">简体中文</a>')
        chunks.append(f'<a class="parent" href="{rel_href(current, dst / "en/README.html")}">English</a>')
    return "\n".join(chunks)


def title_from_md(text: str, fallback: str) -> str:
    text = strip_frontmatter(text)
    for line in text.splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return fallback


def write_page(path: Path, lang: str, title: str, home: str, nav: str, body: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        PAGE_TMPL.format(
            lang=lang,
            title=escape(title),
            css=NAV_CSS,
            home=home,
            nav=nav,
            body=body,
            js=NAV_JS,
        ),
        encoding="utf-8",
    )


def main() -> None:
    if DST.exists():
        shutil.rmtree(DST)
    DST.mkdir(parents=True)
    groups = parse_groups((SRC / "SUMMARY.md").read_text())

    md_files = [
        p
        for p in SRC.rglob("*.md")
        if p.name not in SKIP and p.relative_to(SRC).as_posix() not in {"README.md"}
    ]

    for src_md in md_files:
        rel = src_md.relative_to(SRC)
        out = DST / rel.with_suffix(".html")
        raw = src_md.read_text()
        body = render_md(raw)
        title = title_from_md(raw, src_md.stem)
        html_lang = "zh-CN" if rel.as_posix().startswith("zh/") else "en"
        write_page(
            out,
            html_lang,
            title,
            rel_href(out, DST / "index.html"),
            build_nav(groups, out, DST),
            body,
        )

    for group in groups:
        if not group.group_page:
            continue
        out = DST / group.group_page
        links = []
        for title, md_path in group.items:
            href = rel_href(out, DST / html_name(md_path))
            links.append(f'<li><a href="{href}">{escape(title)}</a></li>')
        intro = "本章包含以下页面：" if group.lang == "zh" else "Pages in this section:"
        body = f"<h1>{escape(group.title)}</h1><p>{intro}</p><ul class=\"group-list\">{''.join(links)}</ul>"
        html_lang = "zh-CN" if group.lang == "zh" else "en"
        write_page(
            out,
            html_lang,
            group.title,
            rel_href(out, DST / "index.html"),
            build_nav(groups, out, DST),
            body,
        )

    index = DST / "index.html"
    body = """
    <h1>资产管理平台使用说明书</h1>
    <p>双击本文件即可阅读，无需联网、无需上传。请选择语言：</p>
    <div class="home-cards">
      <a href="zh/README.html"><strong>简体中文</strong>从产品简介开始</a>
      <a href="en/README.html"><strong>English</strong>Start with the introduction</a>
    </div>
    """
    write_page(index, "zh-CN", "资产管理平台使用说明书", "index.html", build_nav(groups, index, DST), body)

    opener = DST / "打开说明书.command"
    opener.write_text("#!/bin/bash\ncd \"$(dirname \"$0\")\"\nopen index.html\n", encoding="utf-8")
    opener.chmod(0o755)
    print(f"generated {sum(1 for _ in DST.rglob('*.html'))} html pages -> {DST}")


if __name__ == "__main__":
    main()
