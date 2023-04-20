Forge port note
==============
**Do not report problems to Masa. This is not an offical version of Litematica.**<br>
**Report any problems [here](https://github.com/ZacSharp/litematica-forge/issues).**

This is a fork of Litematica created by me (@ZacSharp) so I could use Litematica
on Forge 1.16.5 and later 1.17.1. While I did decide to publish my work and am
likely to provide more ported versions than I personally need along with some
level of support, personal usage is still the main motivation so please don't expect
the same level of support as for the official versions.

If you want an official version you will have to either use Fabric or be patient.
Masa has plans to support both loaders with the rewritten codebase, but so far
(April 2023) there is no timeline.

Litematica
==============
Litematica is a client-side Minecraft schematic mod.
It is more or less a re-creation of or a substitute for [Schematica](https://minecraft.curseforge.com/projects/schematica),
with a lot of additional features.

Downloads / Compiled builds
=========
Have a look at the [CI builds](https://github.com/ZacSharp/litematica-forge/actions).
Click on the topmost entry for your version of Minecraft and
scroll down to "Artifacts". If you are logged in to GitHub you can click on
"Litematica Artifacts" to download a zip containing the built jar.
The "MaLiLib Artifacts" zip contains the version of MaLiLib used to build
Litematica, which is not always the latest version of MaLiLib. If you want an
up to date version of MaLiLib you will have to download it from the [MaLiLib Forge repository](https://github.com/ZacSharp/malilib-forge)

Compiling
=========
* Clone the malilib-forge repository from https://github.com/ZacSharp/malilib-forge
* Rename the directory from 'malilib-forge' to 'malilib'
* Open a command prompt/terminal to the repository directory
* run 'gradlew build'
* Clone this repository next to the malilib repository
* Open a command prompt/terminal to the repository directory
* run 'gradlew build'
* The built jar file will be in build/libs/