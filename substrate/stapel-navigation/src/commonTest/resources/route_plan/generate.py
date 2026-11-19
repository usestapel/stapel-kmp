"""Writes the shared route-plan fixtures from stapel-tools' `build_nav_route_plan`.

The Kotlin `resolveNav` is held to the Python planner by these files: the planner decides, this
script records its decision, `RoutePlanFixtureTest` replays it. Never edit a fixture by hand —
re-run this script against a stapel-tools commit and commit the result together with the commit
id it prints.

Usage (from an empty throwaway venv with stapel-tools installed from a read-only clone):

    git clone git@github.com:usestapel/stapel-tools.git /tmp/tools && git -C /tmp/tools checkout origin/main
    python3.12 -m venv /tmp/venv && /tmp/venv/bin/pip install /tmp/tools
    /tmp/venv/bin/python -I generate.py --tools-commit "$(git -C /tmp/tools rev-parse HEAD)" --out .
"""

import argparse
import copy
import json
import pathlib
from unittest import mock

from stapel_tools import _frontend_templates as ft
from stapel_tools.create_project import FRONTEND_REACT_LIBS


def mirrored(entry):
    return {k: v for k, v in entry.items() if k != "component"}


def registry_pair(key):
    lib = FRONTEND_REACT_LIBS[key]
    return {"package": lib["package"], "version": lib["version"], "nav": [mirrored(e) for e in lib["nav"]]}


def entry(id_, path, *, level="top", parent=None, order=0, requires_auth=True, surface=None,
          icon="AppstoreOutlined", menu=True, extra=None):
    e = {
        "id": id_, "labelKey": f"{id_}.label", "icon": icon, "route": {"path": path},
        "placement": {"level": level} if parent is None else {"level": level, "parentId": parent},
        "menuVisibleDefault": menu, "requiresAuth": requires_auth, "order": order,
    }
    if surface is not None:
        e["surface"] = surface
    if extra:
        e.update(extra)
    return e


def synthetic(nav):
    return [{"package": "@fixture/synthetic", "version": "0.0.0", "nav": nav}]


def run_case(name, pairs, extra_declared=()):
    declared = ft.declared_nav_ids() | set(extra_declared)
    case = {
        "name": name,
        "icons": sorted(ft.NAV_ICON_REGISTRY),
        "declared": sorted(declared),
        "input": copy.deepcopy(pairs),
    }
    try:
        with mock.patch.object(ft, "declared_nav_ids", lambda: declared):
            plan = ft.build_nav_route_plan(copy.deepcopy(pairs))
    except ft.NavContractError as err:
        lines = [ln.strip()[2:] for ln in str(err).splitlines() if ln.strip().startswith("- ")]
        case["expectedError"] = {"ids": sorted({ln.split(":", 1)[0] for ln in lines}), "message": str(err)}
        return case
    routes = {}
    for r in plan["absolute_routes"]:
        routes[r["entry"]["id"]] = r["path"]
    for r in plan["app_children"]:
        routes[r["entry"]["id"]] = "/app/" + r["path"]
    input_ids = [e["id"] for p in pairs for e in p["nav"]]
    case["expected"] = {
        "routes": routes,
        "containerRoots": plan["container_roots"],
        "unrouted": sorted(i for i in input_ids if i not in routes),
    }
    return case


def cases():
    keys = [k for k, v in FRONTEND_REACT_LIBS.items() if v.get("nav")]
    for key in keys:
        yield f"registry-{key}-alone", [registry_pair(key)], ()
    yield "registry-all", [registry_pair(k) for k in keys], ()
    yield "registry-auth-profiles-notifications", [registry_pair(k) for k in ("auth", "profiles", "notifications")], ()
    yield "synthetic-public-relative-top-hoists-children", synthetic([
        entry("fx.help", "help", requires_auth=False, surface="public", order=5),
        entry("fx.help_topic", "topic/:topicId", level="submenu", parent="fx.help", requires_auth=False, surface="public", order=1),
    ]), ("fx.help", "fx.help_topic")
    yield "synthetic-child-of-absolute-top-is-not-routed", synthetic([
        entry("fx.shop", "/shop", requires_auth=False, surface="public", order=1),
        entry("fx.shop_item", "item/:id", level="submenu", parent="fx.shop", requires_auth=False, order=2),
    ]), ("fx.shop", "fx.shop_item")
    yield "synthetic-grandchild-is-an-orphan", synthetic([
        entry("fx.top", "top", order=1),
        entry("fx.child", "child", level="submenu", parent="fx.top", order=1),
        entry("fx.grandchild", "grand", level="submenu", parent="fx.child", order=1),
    ]), ("fx.top", "fx.child", "fx.grandchild")
    yield "synthetic-order-ties-sort-by-id", synthetic([
        entry("fx.b", "b", order=3), entry("fx.a", "a", order=3), entry("fx.c", "/c", order=1, requires_auth=False),
        entry("fx.a_kid2", "k2", level="submenu", parent="fx.a", order=2),
        entry("fx.a_kid1", "k1", level="submenu", parent="fx.a", order=2),
    ]), ("fx.a", "fx.b", "fx.c", "fx.a_kid1", "fx.a_kid2")
    yield "synthetic-derived-surface-public-when-auth-not-required", synthetic([
        entry("fx.about", "about", requires_auth=False, order=1),
        entry("fx.inbox", "inbox", requires_auth=True, order=2),
        entry("fx.qr", "qr", requires_auth=True, surface="public", order=3),
    ]), ("fx.about", "fx.inbox", "fx.qr")
    yield "synthetic-error-unknown-icon", synthetic([entry("fx.x", "x", icon="NotAnIcon")]), ("fx.x",)
    yield "synthetic-error-undeclared-parent", synthetic([
        entry("fx.y", "y", level="submenu", parent="nobody.declares"),
    ]), ("fx.y",)
    yield "synthetic-error-route-index", synthetic([entry("fx.z", "z", extra={"route": {"path": "z", "index": True}})]), ("fx.z",)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tools-commit", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    out = pathlib.Path(args.out)
    names = []
    for name, pairs, extra in cases():
        case = run_case(name, pairs, extra)
        case["source"] = {"repo": "usestapel/stapel-tools", "commit": args.tools_commit,
                          "function": "stapel_tools._frontend_templates.build_nav_route_plan"}
        (out / f"{name}.json").write_text(json.dumps(case, indent=2, sort_keys=True) + "\n")
        names.append(name)
    (out / "index.json").write_text(json.dumps({"commit": args.tools_commit, "cases": names}, indent=2) + "\n")
    print(f"{len(names)} fixtures from stapel-tools {args.tools_commit}")


if __name__ == "__main__":
    main()
