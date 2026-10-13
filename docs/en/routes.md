# Routes: Planning and Import

Everything about where routes come from, their format, and their coordinate systems.

## Route file format

A route is a plain text file with one coordinate per line:

```
121.43680706489432,31.027665038002322
121.43692833613818,31.027541257648274
121.43703613279938,31.02741360898802
```

- Each line is `longitude,latitude` — **longitude first**, comma-separated.
- Encoded in **BD09** (Baidu), **not** raw GPS WGS84.
- The line order is the travel order. The closer the first and last points, the better the result.

## Coordinate systems

This is where people get burned:

| System | Used for |
| --- | --- |
| **WGS84** | Raw GPS; OpenStreetMap tiles are WGS84 |
| **GCJ02** | Amap / Tencent maps; **the client shifts to this before uploading** |
| **BD09** | Baidu maps; **this is what route files store** |

The desktop planner picks points on OSM (WGS84) and **converts each click to BD09 immediately**, so exported txt files remain compatible with legacy routes and with the upload pipeline. Mobile keeps using BD09.

> **Desktop** has moved to Leaflet + OpenStreetMap; **Android / HarmonyOS** route design still uses the Baidu Maps API. Only the base map differs — the exported coordinate format is identical.

## Route planner (desktop)

1. Click "Design/Update route" on the main screen; `route_planner.html` opens in your browser.
2. Click points on the OpenStreetMap to trace a route.
3. You can **add, remove individual points, or clear all points**.
4. Click "Download route txt" to get `custom_route.txt`.
5. Back in the main window, choose "Custom…" in the **Preset route** dropdown and import it.

The route is copied into the **`Routes/` folder next to the executable** and survives restarts.

## Built-in routes and the quarantine folder

- Only `assets/Routes/default.txt` ships as a built-in route today.
- The remaining historical routes moved to `assets/Routes/NeedsReview/` (`BigLawn`, `BotanicalGarden`, `HanzeLake`, `NanTi`, `SiyuanLake`) pending manual verification of jump segments, length and endpoints.
- Routes you import yourself are unaffected and still land in `Routes/`.

> ⚠️ `assets/Routes/NeedsReview/` is a quarantine area. **Do not** move entries back into the built-in list directly — several have jump or length defects that must be validated first.

## Verifying a route

1. Select it in the desktop "Preset route" dropdown.
2. Click "Preview route" (no login needed).
3. Check on the OSM map:
   - Any red highlighted segment means a jump (> 150 m);
   - Whether the green start and red finish are close together;
   - Whether total length matches your target distance.

A route with no red segments and close endpoints is a valid built-in candidate.

## Route design (Android / HarmonyOS)

1. Open the route design page and tap points **in order**.
2. More points fit better. **Order matters, and start/finish should be close.**

![Android route design](../images/android-route.png)

## Deleting an imported route

The "Delete" button next to the preset route dropdown removes a route from `Routes/`.

- Built-in `default.txt` and legacy `user.txt` are **protected** and cannot be deleted.
- Importing a duplicate name appends `_1`, `_2`, etc. instead of overwriting.
