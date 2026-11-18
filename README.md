# Visuals

Rein visuelle, clientseitige Fabric-Mod für Minecraft Java **1.21.11** (Java 21). Sie fügt keine Blöcke, Items oder Spielmechaniken hinzu und verändert nur, was der eigene Client zeichnet oder abspielt. Gesteuert wird alles über ein vollständiges Einstellungs-GUI.

## Stand

| Abschnitt | Inhalt | Status |
|---|---|---|
| 1 | Config, zentrale Options-Registrierung, Sprachdateien | fertig |
| 2 | Screen mit Reiter-Leiste | offen |
| 3 | Beispiel-Reiter „Himmel“ und Widget-Typen | offen |
| 4 | Live-Vorschau | offen |
| 5 | Presets, Import/Export, Suche, Rückgängig | offen |

## Bauen

```
./gradlew build        # Jar liegt danach in build/libs/
./gradlew runClient    # Testclient starten
```

Versionen (siehe `gradle.properties`): Fabric Loader 0.18.1, Loom 1.14, Fabric API 0.141.3+1.21.11. Mappings: Mojang (Yarn wird nach 1.21.11 nicht mehr gepflegt).

## Aufbau

```
dev.visuals
├── VisualsMod              Einstiegspunkt (nur Client)
├── config                  OptionDef, OptionRegistry, ConfigManager, Codecs, Migrationen
│   └── def                 Reiter und Optionen (hier wird definiert, nichts sonst)
├── util                    Log
└── (folgen) gui, effects, mixin
```

## Neue Option hinzufügen

1. In der passenden `…Options`-Klasse unter `config/def` eine Konstante anlegen, z. B.
   `OptionDef.slider("sky.starAmount", 1.0, 0.0, 2.0, 0.05).group("stars").build()`
2. In deren `register(...)` eine Zeile ergänzen.
3. In `en_us.json` und `de_de.json` Name, Tooltip und Gruppe eintragen
   (`visuals.option.<id>.name`, `.tooltip`, `visuals.group.<reiter>.<gruppe>`).

Das GUI baut sich daraus selbst auf.

## Config

`config/visuals/config.json`, mit `configVersion`. Beschädigte Dateien werden als `config.json.broken-<zeit>` beiseitegelegt, danach gelten die Standardwerte. Migrationen liegen in `ConfigMigrations`.
