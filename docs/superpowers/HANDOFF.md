# Facebook+ Manager handoff

This is Facebook+ Manager, a one-tap fork of Morphe Manager. It is not Morphe.

- Package: `app.eightbitsoftworks.facebookplus.manager` (debug suffix `.debug`)
- Launcher label: `Facebook+ Manager` (never Morph / Morph Debug)
- User-facing English copy must not name HushFacebook

## Three clocks

| Clock | Phone already gets it? | Needs our APK? |
|---|---|---|
| HushFacebook `.mpp` | Yes | No, unless `Patcher-Version` is newer than we ship |
| `morphe-patcher` | No | Yes |
| Morphe Manager UI | No | Optional |

## Start here

Read `docs/superpowers/UPDATE-PROTOCOL.md` first.

The phrases **implement update protocol**, **run the update protocol**, **swap the patcher**, and **keep up with upstream** mean: follow that file in order.

Pins: `facebook-plus-manager/upstream-pins.json`

Specs:

- Branded shell: `docs/superpowers/specs/2026-10-28-facebook-plus-manager-branded-shell-design.md`
- Keep-up: `docs/superpowers/specs/2026-11-02-upstream-keep-up-design.md`
- Plan: `docs/superpowers/plans/2026-11-02-upstream-keep-up.md`

Never ship Morphe's APK. Never point `app-release.json` at Morphe. Never change `applicationId`.
