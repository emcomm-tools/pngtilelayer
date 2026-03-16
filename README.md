# PNGTileLayer — YAAC Map Plugin

OpenMap plugin for [YAAC](https://www.ka2ddo.org/ka2ddo/YAAC.html) (Yet Another APRS Client) providing offline PNG tile map support and enhanced station tracking visualization for field SAR and emergency communications.

## Features

### PNG Tile Map Layer
- Slippy map tile rendering (OSM-style PNG tiles)
- Multiple tile sources with add/edit/delete management
- Offline tile cache for field operations without internet
- Configurable via **View → Map Sources...** dialog

### Station Pushpin Markers
- Colored pushpin markers replace default APRS symbols for cleaner field maps
- Callsign labels with rounded background for readability
- Per-station color assignment from a 10-color SAR palette
- Configurable via **View → Station Colors...** dialog

### BluMap-Style Breadcrumb Trails
- Shows only the last 3 historical positions per station to reduce clutter with multiple field teams
- Graduated styling by age:
  - **Newest segment** (last position → current): solid line, filled triangle
  - **Middle segment**: dashed line, outline-only triangle, 60% opacity
  - **Oldest segment**: dashed line, outline-only triangle, 35% opacity
- Directional triangles point toward the next (more recent) position

### Station Visibility
- Toggle individual stations on/off from **View → Station Visibility...** dialog
- Show All / Hide All / Refresh buttons
- Checkbox table with callsign and color swatch columns

### Trail Point Click Popup
- Click any pushpin or breadcrumb triangle to see station info:
  - **Callsign**
  - **Time received** (UTC absolute + relative age)
  - **UTM coordinates** (zone, easting, northing)
- Click empty map area for normal pan/select behavior

## Installation

Copy `PNGTileLayer.jar` to the YAAC plugins directory:

```
cp PNGTileLayer.jar /opt/yaac/plugins/
```

Restart YAAC. The plugin registers automatically.

## Building

Compile against YAAC SDK and OpenMap:

```
javac -cp lib/openmap.jar:lib/sqlite-jdbc-3.45.3.0.jar:YAAC.jar:YAACMain.jar \
      -d /tmp/yaac-tile-build -sourcepath src src/org/ka2ddo/yaac/gui/tile/*.java
```

Package into JAR with the included `META-INF/services/org.ka2ddo.yaac.pluginapi.Provider` file:

```
cd /tmp/yaac-tile-build && jar cf PNGTileLayer.jar -C /path/to/META-INF META-INF .
```

## Requirements

- YAAC provider API version 26 or newer
- OpenMap (included with YAAC)
- Java 8+

## Author

Sylvain Deguire (VA2OPS) — [EmComm-Tools Project](https://github.com/emcomm-tools)

## License

Part of the EmComm-Tools project. See the main project repository for license details.
