# Volume leveler core provenance

Copied from volume-leveler commit `fcdc3a8562a00b33739c4bafc3c86f5218c22edd`.
The package is renamed for Wholphin. The copy follows ktlint 1.8.0: layout, explicit
kotlin.math imports, optional trailing commas/semicolons, and braces around single
conditional branches. DSP operations, constants and their order are unchanged.
There is no runtime dependency or separate build module.

Original source SHA-256 (before package rename or formatting):

- `Api.kt`: `f556a8b2bc61282f641b3512b1770abca0515fdc2b633d53537ff033e3656f2f`
- `DspMath.kt`: `efe218bcede73e2fb906924f148c7b41dfeeffc16ed92bc56bd2f10f5c1d63d4`
- `LoudnessMeter.kt`: `bc1d948f25e5eb7b5c1148cd5f204c2b02f8b43a32dee5fdf87c841fbc847bed`

Copied source SHA-256 (after formatting, with package renamed back to volumeleveler):

- `Api.kt`: `a569e37c8cb31a379c606f4c015f1e7b85bec22da14cd4ae4c83c27d681bef12`
- `DspMath.kt`: `74d4862e918acc07560ae7fbc2d7b6280c5df793359f8df75fe3a87f9e3599cf`
- `LoudnessMeter.kt`: `2ddc97457347b32746a7fffb66de3173dbe3571b2a5a33ac43eeda5a0e0f9433`

On a core refresh, copy the three main sources from the recorded commit, rename the
package, expand kotlin.math wildcard imports, and run the project's pre-commit
formatter until clean. Review formatting-only differences against the source, then
update both original and copied hashes here and the independent pins in
VolumeLevelingIntegrationTest. Keep algorithm changes in the source core so future
refreshes do not need to merge local DSP changes.
