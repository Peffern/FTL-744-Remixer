# FTL-744-Remixer
Replace FTL in-game soundtrack with remix by 744 Music

## Is the music included with the mod?
No, I can't distribute the music. You have to get it separately from the artist's Bandcamp.

### I have the music! What do I do?
See **Installation** section below.

## Dependencies
This depends on Slipstream Mod Manager. It should be compatible with other mods that add music (such as Mike Hopley's), since this replaces the tracks rather than editing sector data.

Since not every track has a remix, non-remixed tracks are unaffected by this mod.

### How it works
The mod itself copies the remixed `.ogg` files into FTL's audio folder, then replaces the track listings in `sounds.xml` with the appropriate files.

The mod _generator_ (the Java code) is just a simple GUI with a button that streams in the (zipped) album, and streams out the (zipped) `.ftl` mod file. The body of the mod is loaded from a classpath resource (`/resources` folder in the source tree).

I chose to write it in Java since Slipstream itself is in Java so anyone using this is guaranteed to have it installed anyway.

## Installation
First get the album from the artist. Make sure you download the `Ogg Vorbis` version, since that's what FTL expects.

Then, download `ftl744remixer.jar` from this repository and execute it. Point it at the album file and it will generate the mod `744remix.ftl` for you. Then, copy this file into Slipstream's `/mods` folder.
