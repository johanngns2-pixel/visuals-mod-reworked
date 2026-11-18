package dev.visuals.config.def;

import dev.visuals.config.OptionDef;
import dev.visuals.config.OptionRegistry;

/**
 * First complete settings catalogue for the visual categories.
 * These values are client configuration only; a setting is not advertised as an active renderer
 * until its corresponding client effect implementation is registered.
 */
public final class ModuleOptions {
    private ModuleOptions() {}

    private static void toggle(OptionRegistry r, String id, boolean value, String group) {
        r.register(OptionDef.toggle(id, value).group(group).build());
    }
    private static void slider(OptionRegistry r, String id, double value, double min, double max, double step, String group, String unit) {
        r.register(OptionDef.slider(id, value, min, max, step).group(group).unit(unit).build());
    }
    private static void integer(OptionRegistry r, String id, int value, int min, int max, String group, String unit) {
        r.register(OptionDef.intSlider(id, value, min, max).group(group).unit(unit).build());
    }
    private static void color(OptionRegistry r, String id, int value, String group) {
        r.register(OptionDef.color(id, value).group(group).build());
    }
    private static void choice(OptionRegistry r, String id, String value, String group, String... choices) {
        r.register(OptionDef.dropdown(id, value, choices).group(group).build());
    }

    public static void register(OptionRegistry r) {
        // Enchantment glint
        choice(r,"glint.style","gradient","appearance","vanilla","solid","gradient","rainbow");
        color(r,"glint.primaryColor",0xFFB56BFF,"colors");
        color(r,"glint.secondaryColor",0xFF56D9FF,"colors");
        slider(r,"glint.speed",1.0,0.0,4.0,0.05,"animation","x");
        choice(r,"glint.direction","diagonal","animation","diagonal","horizontal","vertical","reverse");
        slider(r,"glint.intensity",0.75,0.0,1.0,0.05,"appearance","%");
        toggle(r,"glint.armor",true,"targets");
        toggle(r,"glint.tools",true,"targets");
        toggle(r,"glint.weapons",true,"targets");

        // Seasons
        choice(r,"seasons.mode","manual","schedule","manual","real_date","automatic");
        choice(r,"seasons.current","summer","schedule","spring","summer","autumn","winter");
        integer(r,"seasons.transitionDays",5,0,30,"schedule","days");
        slider(r,"seasons.visualStrength",1.0,0.0,1.0,0.05,"effects","%");
        toggle(r,"seasons.leaves",true,"effects");
        toggle(r,"seasons.grassTint",true,"effects");
        toggle(r,"seasons.snow",true,"effects");
        toggle(r,"seasons.weather",true,"effects");

        // Weather and fog
        toggle(r,"weather.customRain",false,"weather");
        toggle(r,"weather.customSnow",true,"weather");
        toggle(r,"weather.customThunder",true,"weather");
        slider(r,"weather.fogDensity",0.35,0.0,1.0,0.01,"fog","%");
        color(r,"weather.fogColor",0xFF9BAFC4,"fog");
        slider(r,"weather.fogStart",0.15,0.0,1.0,0.01,"fog","%");
        slider(r,"weather.fogEnd",0.9,0.1,1.0,0.01,"fog","%");
        slider(r,"weather.windStrength",0.35,0.0,1.0,0.01,"wind","%");
        slider(r,"weather.windDirection",0.0,0.0,360.0,1.0,"wind","deg");
        toggle(r,"weather.windGusts",true,"wind");

        // Lighting
        toggle(r,"light.dynamicHeldItem",true,"dynamic");
        integer(r,"light.heldItemRadius",10,1,32,"dynamic","blocks");
        toggle(r,"light.torchColor",true,"dynamic");
        color(r,"light.warmColor",0xFFFFB45C,"colors");
        color(r,"light.moonColor",0xFF9ABEFF,"colors");
        slider(r,"light.moonlightStrength",0.35,0.0,1.0,0.05,"colors","%");

        // Foliage and particles
        toggle(r,"foliage.fallingLeaves",true,"leaves");
        integer(r,"foliage.leafAmount",24,0,100,"leaves","particles");
        slider(r,"foliage.fallSpeed",0.7,0.1,2.0,0.05,"leaves","x");
        toggle(r,"foliage.biomeTint",true,"leaves");
        integer(r,"foliage.particleCap",500,50,2000,"performance","particles");
        integer(r,"foliage.renderRange",32,8,128,"performance","blocks");

        // Fireflies
        choice(r,"fireflies.style","pulse","appearance","pulse","flicker","trail");
        color(r,"fireflies.primaryColor",0xFFFFE78A,"appearance");
        color(r,"fireflies.secondaryColor",0xFF8DFFB5,"appearance");
        integer(r,"fireflies.amount",24,0,100,"behavior","count");
        slider(r,"fireflies.glow",0.8,0.0,1.0,0.05,"appearance","%");
        toggle(r,"fireflies.swarm",true,"behavior");
        toggle(r,"fireflies.nearTorches",true,"behavior");
        choice(r,"fireflies.time","night","behavior","night","always","dusk");

        // Puddles
        slider(r,"puddles.reflectionStrength",0.65,0.0,1.0,0.05,"quality","%");
        integer(r,"puddles.count",18,0,80,"quality","puddles");
        slider(r,"puddles.size",1.0,0.25,3.0,0.05,"quality","x");
        choice(r,"puddles.quality","medium","quality","low","medium","high");
        integer(r,"puddles.dryingTime",90,5,600,"behavior","seconds");
        toggle(r,"puddles.reflectSky",true,"quality");

        // Wildlife
        toggle(r,"wildlife.butterflies",true,"creatures");
        toggle(r,"wildlife.birds",true,"creatures");
        toggle(r,"wildlife.dragonflies",true,"creatures");
        toggle(r,"wildlife.bats",true,"creatures");
        integer(r,"wildlife.amount",12,0,50,"behavior","count");
        slider(r,"wildlife.spawnRange",24,8,64,1,"behavior","blocks");
        toggle(r,"wildlife.dayNightCycle",true,"behavior");

        // Biome overrides
        toggle(r,"biomes.enabled",true,"overrides");
        choice(r,"biomes.profile","default","overrides","default","lush","dry","cold","custom");
        slider(r,"biomes.overrideStrength",1.0,0.0,1.0,0.05,"overrides","%");
        toggle(r,"biomes.useVanillaColors",false,"overrides");
        choice(r,"biomes.fallback","global","overrides","global","vanilla","disabled");

        // World events
        toggle(r,"events.meteorShowers",true,"events");
        toggle(r,"events.aurora",true,"events");
        toggle(r,"events.bloodMoon",true,"events");
        toggle(r,"events.rainbows",true,"events");
        toggle(r,"events.migratingBirds",true,"events");
        slider(r,"events.frequency",0.5,0.0,5.0,0.05,"schedule","per night");
        integer(r,"events.duration",45,5,180,"schedule","minutes");
        toggle(r,"events.announcements",false,"schedule");

        // Touch and environment interaction
        toggle(r,"touch.bendingPlants",true,"plants");
        slider(r,"touch.bendStrength",0.65,0.0,1.0,0.05,"plants","%");
        toggle(r,"touch.waterRings",true,"water");
        toggle(r,"touch.dust",true,"ground");
        toggle(r,"touch.snowDents",true,"ground");
        toggle(r,"touch.footprints",true,"ground");
        integer(r,"touch.maxEffects",80,0,300,"performance","effects");

        // Ambient sound
        toggle(r,"sound.wind",true,"ambience");
        slider(r,"sound.windVolume",0.35,0.0,1.0,0.05,"ambience","%");
        toggle(r,"sound.rain",true,"ambience");
        slider(r,"sound.rainVolume",0.5,0.0,1.0,0.05,"ambience","%");
        toggle(r,"sound.crickets",true,"ambience");
        slider(r,"sound.cricketsVolume",0.3,0.0,1.0,0.05,"ambience","%");
        toggle(r,"sound.reverb",false,"ambience");

        // AFK / cinematic
        integer(r,"afk.idleMinutes",5,1,60,"activation","minutes");
        choice(r,"afk.camera","orbit","camera","static","orbit","panorama","overhead");
        slider(r,"afk.cameraSpeed",0.25,0.05,2.0,0.05,"camera","x");
        toggle(r,"afk.collisionProtection",true,"camera");
        toggle(r,"afk.cinematicBars",false,"overlay");
        toggle(r,"afk.locationLabel",true,"overlay");
        toggle(r,"afk.autoScreenshot",false,"overlay");

        // Spotify integration settings (connection requires a separate supported API/client).
        toggle(r,"spotify.showLyrics",false,"lyrics");
        choice(r,"spotify.position","bottom","lyrics","top","center","bottom");
        color(r,"spotify.lyricColor",0xFFFFFFFF,"lyrics");
        integer(r,"spotify.fontScale",100,50,200,"lyrics","%");
        slider(r,"spotify.transitionSpeed",0.35,0.05,1.0,0.05,"lyrics","s");
        choice(r,"spotify.noLyrics","hide","behavior","hide","show_track","keep_last");
    }
}
