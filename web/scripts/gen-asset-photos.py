#!/usr/bin/env python3
"""Generate compact built-in asset catalog SVGs."""
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "public" / "asset-photos"
BG = "#E7F1F6"
P = "#3D7EA6"
P2 = "#2C5E7C"
LT = "#D7EEF8"
MD = "#5A93B3"
BR = "#C4924A"
BR2 = "#8A5A28"
YL = "#E2B007"
OR = "#C46A2B"
RD = "#C44B3B"
GN = "#3E9B6E"


def svg(body: str) -> str:
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128">'
        f'<rect width="128" height="128" rx="22" fill="{BG}"/>'
        f"{body}</svg>\n"
    )


SHAPES = {
    "laptop": f'<rect x="22" y="28" width="84" height="56" rx="6" fill="{P}"/><rect x="28" y="34" width="72" height="42" rx="3" fill="{LT}"/><path d="M16 90h96l-8 10H24z" fill="{P2}"/>',
    "desktop": f'<rect x="28" y="18" width="72" height="78" rx="6" fill="{P}"/><rect x="36" y="26" width="56" height="44" rx="3" fill="{LT}"/><circle cx="64" cy="82" r="5" fill="{LT}"/><rect x="44" y="100" width="40" height="8" rx="2" fill="{P2}"/>',
    "phone": f'<rect x="44" y="18" width="40" height="92" rx="8" fill="{P}"/><rect x="49" y="28" width="30" height="64" rx="3" fill="{LT}"/><circle cx="64" cy="100" r="4" fill="{LT}"/>',
    "tablet": f'<rect x="30" y="18" width="68" height="92" rx="8" fill="{P}"/><rect x="36" y="26" width="56" height="70" rx="3" fill="{LT}"/><circle cx="64" cy="104" r="3.5" fill="{LT}"/>',
    "monitor": f'<rect x="18" y="24" width="92" height="62" rx="6" fill="{P}"/><rect x="24" y="30" width="80" height="48" rx="3" fill="{LT}"/><rect x="58" y="86" width="12" height="12" fill="{P2}"/><rect x="40" y="98" width="48" height="8" rx="2" fill="{P2}"/>',
    "tv": f'<rect x="14" y="28" width="100" height="62" rx="6" fill="{P2}"/><rect x="20" y="34" width="88" height="50" rx="3" fill="{LT}"/><rect x="50" y="92" width="28" height="8" rx="2" fill="{P2}"/>',
    "printer": f'<rect x="28" y="22" width="72" height="22" rx="4" fill="{MD}"/><rect x="18" y="42" width="92" height="42" rx="6" fill="{P}"/><rect x="34" y="52" width="60" height="18" rx="2" fill="{LT}"/><rect x="36" y="86" width="56" height="16" rx="2" fill="#C5D9E4"/>',
    "scanner": f'<rect x="16" y="40" width="96" height="48" rx="8" fill="{P}"/><rect x="24" y="50" width="80" height="16" rx="3" fill="{LT}"/><rect x="20" y="88" width="88" height="10" rx="2" fill="{P2}"/>',
    "projector": f'<rect x="22" y="46" width="84" height="36" rx="8" fill="{P}"/><circle cx="86" cy="64" r="12" fill="{LT}"/><rect x="30" y="84" width="40" height="8" rx="2" fill="{P2}"/>',
    "server": f'<rect x="26" y="22" width="76" height="84" rx="6" fill="{P}"/><rect x="34" y="32" width="60" height="16" rx="2" fill="{LT}"/><rect x="34" y="56" width="60" height="16" rx="2" fill="{LT}"/><rect x="34" y="80" width="60" height="16" rx="2" fill="{LT}"/><circle cx="42" cy="40" r="3" fill="{P}"/><circle cx="42" cy="64" r="3" fill="{P}"/><circle cx="42" cy="88" r="3" fill="{P}"/>',
    "router": f'<rect x="22" y="54" width="84" height="36" rx="8" fill="{P}"/><path d="M40 54V30M64 54V22M88 54V30" stroke="{P2}" stroke-width="6" stroke-linecap="round"/><circle cx="40" cy="72" r="4" fill="{LT}"/><circle cx="64" cy="72" r="4" fill="{LT}"/><circle cx="88" cy="72" r="4" fill="{LT}"/>',
    "ups": f'<rect x="36" y="20" width="56" height="88" rx="8" fill="{P}"/><path d="M64 36v20l12 8-24 28 8-20-12-8z" fill="{YL}"/>',
    "camera": f'<rect x="22" y="42" width="84" height="52" rx="10" fill="{P}"/><circle cx="64" cy="68" r="18" fill="{LT}"/><circle cx="64" cy="68" r="10" fill="{P2}"/><rect x="48" y="30" width="32" height="16" rx="4" fill="{MD}"/>',
    "cctv": f'<rect x="20" y="44" width="70" height="28" rx="8" fill="{P}"/><circle cx="82" cy="58" r="10" fill="{LT}"/><rect x="30" y="72" width="12" height="22" fill="{P2}"/><rect x="22" y="92" width="28" height="8" rx="2" fill="{P2}"/>',
    "radio": f'<rect x="34" y="36" width="60" height="70" rx="8" fill="{P}"/><circle cx="64" cy="64" r="14" fill="{LT}"/><rect x="60" y="18" width="8" height="22" rx="2" fill="{P2}"/><rect x="44" y="88" width="40" height="8" rx="2" fill="{LT}"/>',
    "headset": f'<path d="M28 70c0-22 16-40 36-40s36 18 36 40" fill="none" stroke="{P}" stroke-width="10" stroke-linecap="round"/><rect x="20" y="66" width="18" height="28" rx="6" fill="{P2}"/><rect x="90" y="66" width="18" height="28" rx="6" fill="{P2}"/>',
    "speaker": f'<rect x="38" y="20" width="52" height="88" rx="8" fill="{P}"/><circle cx="64" cy="46" r="12" fill="{LT}"/><circle cx="64" cy="82" r="16" fill="{LT}"/><circle cx="64" cy="82" r="8" fill="{P2}"/>',
    "watch": f'<rect x="50" y="14" width="28" height="22" rx="4" fill="{P2}"/><rect x="50" y="92" width="28" height="22" rx="4" fill="{P2}"/><rect x="40" y="34" width="48" height="60" rx="14" fill="{P}"/><circle cx="64" cy="64" r="16" fill="{LT}"/>',
    "pos": f'<rect x="24" y="22" width="80" height="48" rx="6" fill="{P}"/><rect x="30" y="28" width="68" height="28" rx="3" fill="{LT}"/><rect x="36" y="78" width="56" height="28" rx="4" fill="{MD}"/>',
    "barcode": f'<rect x="20" y="36" width="88" height="56" rx="8" fill="{P}"/><g fill="{LT}"><rect x="30" y="46" width="4" height="36"/><rect x="38" y="46" width="8" height="36"/><rect x="50" y="46" width="3" height="36"/><rect x="58" y="46" width="10" height="36"/><rect x="72" y="46" width="4" height="36"/><rect x="80" y="46" width="7" height="36"/><rect x="92" y="46" width="3" height="36"/></g>',
    "badge": f'<rect x="34" y="28" width="60" height="76" rx="8" fill="{P}"/><circle cx="64" cy="52" r="14" fill="{LT}"/><rect x="44" y="74" width="40" height="8" rx="2" fill="{LT}"/><rect x="48" y="88" width="32" height="6" rx="2" fill="#C5D9E4"/>',
    "keys": f'<circle cx="48" cy="52" r="16" fill="none" stroke="{P}" stroke-width="8"/><path d="M60 60l36 36" stroke="{P2}" stroke-width="8" stroke-linecap="round"/><path d="M82 82v12M92 92v10" stroke="{P2}" stroke-width="6" stroke-linecap="round"/>',
    "helmet": f'<path d="M22 78c0-28 18-48 42-48s42 20 42 48v6H22z" fill="{YL}"/><rect x="18" y="80" width="92" height="12" rx="4" fill="#C49206"/><rect x="58" y="40" width="12" height="28" fill="#F6E08A"/>',
    "vest": f'<path d="M36 28l12 14h32l12-14 8 12v64H28V40z" fill="{YL}"/><rect x="56" y="48" width="16" height="48" fill="#F6E08A"/>',
    "boots": f'<path d="M30 40h28v36H30z" fill="{P2}"/><path d="M30 76h40l10 20H30z" fill="{BR2}"/><path d="M70 40h28v36H70z" fill="{P2}"/><path d="M70 76h40l10 20H70z" fill="{BR2}"/>',
    "extinguisher": f'<rect x="50" y="36" width="28" height="70" rx="10" fill="{RD}"/><rect x="46" y="28" width="36" height="12" rx="3" fill="{P2}"/><path d="M64 28V16h18" fill="none" stroke="{P2}" stroke-width="6" stroke-linecap="round"/>',
    "firstaid": f'<rect x="24" y="32" width="80" height="64" rx="10" fill="{RD}"/><path d="M48 64h32M64 48v32" stroke="#fff" stroke-width="10" stroke-linecap="round"/>',
    "package": f'<path d="M24 48l40-18 40 18-40 18z" fill="{BR}"/><path d="M24 48v40l40 18V66z" fill="#A67838"/><path d="M64 66v40l40-18V48z" fill="#D4A85C"/>',
    "pallet": f'<rect x="18" y="70" width="92" height="14" rx="2" fill="#A67838"/><rect x="22" y="86" width="16" height="16" fill="{BR2}"/><rect x="56" y="86" width="16" height="16" fill="{BR2}"/><rect x="90" y="86" width="16" height="16" fill="{BR2}"/><rect x="30" y="36" width="68" height="34" rx="3" fill="{BR}"/>',
    "crate": f'<rect x="24" y="36" width="80" height="60" rx="6" fill="{BR}"/><path d="M24 56h80M24 76h80M44 36v60M84 36v60" stroke="{BR2}" stroke-width="4"/>',
    "barrel": f'<path d="M36 28h56c6 10 6 62 0 72H36c-6-10-6-62 0-72z" fill="{OR}"/><path d="M32 48h64M32 80h64" stroke="#8A4A1C" stroke-width="5"/>',
    "container": f'<rect x="16" y="40" width="96" height="52" rx="4" fill="{GN}"/><path d="M16 58h96M16 74h96M40 40v52M64 40v52M88 40v52" stroke="#2E734F" stroke-width="3"/>',
    "cart": f'<rect x="28" y="36" width="64" height="46" rx="6" fill="{MD}"/><path d="M28 82h72" stroke="{P2}" stroke-width="6"/><circle cx="44" cy="100" r="8" fill="{P2}"/><circle cx="88" cy="100" r="8" fill="{P2}"/><path d="M20 28h16" stroke="{P2}" stroke-width="6" stroke-linecap="round"/>',
    "palletjack": f'<rect x="20" y="70" width="88" height="10" fill="{YL}"/><rect x="90" y="40" width="12" height="40" fill="{YL}"/><circle cx="36" cy="92" r="8" fill="{P2}"/><circle cx="86" cy="92" r="8" fill="{P2}"/>',
    "forklift": f'<rect x="40" y="50" width="52" height="36" rx="4" fill="{YL}"/><rect x="20" y="72" width="22" height="8" fill="#8A8A8A"/><circle cx="52" cy="96" r="10" fill="{P2}"/><circle cx="86" cy="96" r="10" fill="{P2}"/><rect x="78" y="28" width="10" height="24" fill="{MD}"/>',
    "car": f'<path d="M18 74l12-22h50l22 22v18H18z" fill="{P}"/><rect x="40" y="56" width="28" height="16" rx="2" fill="{LT}"/><circle cx="38" cy="96" r="10" fill="{P2}"/><circle cx="90" cy="96" r="10" fill="{P2}"/>',
    "van": f'<rect x="16" y="48" width="96" height="40" rx="6" fill="{P}"/><rect x="22" y="54" width="28" height="20" rx="3" fill="{LT}"/><circle cx="38" cy="98" r="10" fill="{P2}"/><circle cx="92" cy="98" r="10" fill="{P2}"/>',
    "truck": f'<rect x="14" y="50" width="52" height="36" rx="4" fill="{MD}"/><rect x="66" y="36" width="48" height="50" rx="4" fill="{P}"/><circle cx="36" cy="98" r="10" fill="{P2}"/><circle cx="90" cy="98" r="10" fill="{P2}"/>',
    "bicycle": f'<circle cx="36" cy="84" r="16" fill="none" stroke="{P2}" stroke-width="6"/><circle cx="92" cy="84" r="16" fill="none" stroke="{P2}" stroke-width="6"/><path d="M36 84h24l16-28h16M60 84l16-28M52 56h28" fill="none" stroke="{P}" stroke-width="6" stroke-linecap="round"/>',
    "motorcycle": f'<circle cx="34" cy="90" r="14" fill="{P2}"/><circle cx="96" cy="90" r="14" fill="{P2}"/><path d="M34 90h40l18-28h12" fill="none" stroke="{P}" stroke-width="8" stroke-linecap="round"/><rect x="54" y="52" width="28" height="14" rx="4" fill="{MD}"/>',
    "excavator": f'<rect x="46" y="52" width="48" height="32" rx="4" fill="{YL}"/><circle cx="58" cy="96" r="12" fill="{P2}"/><circle cx="90" cy="96" r="12" fill="{P2}"/><path d="M46 60L18 40l8 28" fill="none" stroke="{OR}" stroke-width="8" stroke-linecap="round"/>',
    "toolbox": f'<rect x="20" y="48" width="88" height="52" rx="8" fill="{OR}"/><rect x="48" y="32" width="32" height="20" rx="4" fill="#8A4A1C"/><rect x="32" y="64" width="64" height="12" rx="3" fill="#F0D2B0"/>',
    "drill": f'<rect x="18" y="52" width="64" height="28" rx="8" fill="{P}"/><path d="M82 60h28l-8 6 8 6H82z" fill="#8A8A8A"/><rect x="28" y="40" width="18" height="16" rx="3" fill="{P2}"/>',
    "ladder": f'<path d="M36 18l-8 92M92 18l8 92" stroke="{BR}" stroke-width="8"/><path d="M34 40h58M32 62h62M30 84h66" stroke="{BR2}" stroke-width="6"/>',
    "generator": f'<rect x="22" y="44" width="84" height="48" rx="8" fill="{P}"/><circle cx="64" cy="68" r="14" fill="{LT}"/><rect x="48" y="28" width="32" height="18" rx="4" fill="{MD}"/>',
    "battery": f'<rect x="28" y="40" width="72" height="48" rx="8" fill="{GN}"/><rect x="100" y="54" width="10" height="20" rx="2" fill="{P2}"/><rect x="40" y="52" width="14" height="24" rx="2" fill="#fff"/><rect x="58" y="52" width="14" height="24" rx="2" fill="#fff"/>',
    "sensor": f'<circle cx="64" cy="64" r="28" fill="{P}"/><circle cx="64" cy="64" r="14" fill="{LT}"/><path d="M64 20v12M64 96v12M20 64h12M96 64h12" stroke="{P2}" stroke-width="6" stroke-linecap="round"/>',
    "antenna": f'<rect x="56" y="70" width="16" height="34" fill="{P2}"/><path d="M64 70L28 28M64 70l36-42" stroke="{P}" stroke-width="6" stroke-linecap="round"/><circle cx="64" cy="58" r="8" fill="{MD}"/>',
    "chair": f'<rect x="36" y="24" width="56" height="36" rx="6" fill="{P}"/><rect x="32" y="62" width="64" height="12" rx="3" fill="{P2}"/><rect x="38" y="74" width="8" height="28" fill="{P2}"/><rect x="82" y="74" width="8" height="28" fill="{P2}"/>',
    "desk": f'<rect x="16" y="48" width="96" height="14" rx="3" fill="{BR}"/><rect x="22" y="62" width="10" height="40" fill="{BR2}"/><rect x="96" y="62" width="10" height="40" fill="{BR2}"/><rect x="70" y="64" width="28" height="26" rx="3" fill="{MD}"/>',
    "cabinet": f'<rect x="30" y="18" width="68" height="92" rx="6" fill="{P}"/><rect x="38" y="28" width="52" height="32" rx="3" fill="{LT}"/><rect x="38" y="68" width="52" height="32" rx="3" fill="{LT}"/><circle cx="84" cy="44" r="3" fill="{P}"/><circle cx="84" cy="84" r="3" fill="{P}"/>',
    "fridge": f'<rect x="36" y="14" width="56" height="100" rx="8" fill="{MD}"/><rect x="42" y="22" width="44" height="28" rx="3" fill="{LT}"/><rect x="42" y="58" width="44" height="46" rx="3" fill="{LT}"/><rect x="80" y="30" width="4" height="14" fill="{P2}"/>',
    "ac": f'<rect x="16" y="40" width="96" height="44" rx="10" fill="{P}"/><path d="M28 62h72" stroke="{LT}" stroke-width="8" stroke-linecap="round"/><circle cx="96" cy="52" r="5" fill="{LT}"/>',
    "wheelchair": f'<circle cx="78" cy="86" r="20" fill="none" stroke="{P2}" stroke-width="8"/><circle cx="40" cy="94" r="10" fill="{P2}"/><path d="M40 94h28l16-36H52" fill="none" stroke="{P}" stroke-width="7" stroke-linecap="round"/><circle cx="54" cy="36" r="8" fill="{MD}"/>',
    "stretcher": f'<rect x="16" y="52" width="96" height="20" rx="6" fill="{LT}"/><rect x="16" y="48" width="96" height="8" rx="3" fill="{P}"/><circle cx="32" cy="92" r="8" fill="{P2}"/><circle cx="96" cy="92" r="8" fill="{P2}"/>',
    "generic": f'<rect x="30" y="30" width="68" height="68" rx="10" fill="{P}"/><path d="M46 64h36M64 46v36" stroke="{LT}" stroke-width="8" stroke-linecap="round"/>',
}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, body in SHAPES.items():
        (OUT / f"{name}.svg").write_text(svg(body), encoding="utf-8")
    print(f"wrote {len(SHAPES)} svgs to {OUT}")


if __name__ == "__main__":
    main()
