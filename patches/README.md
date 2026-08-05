# Patches

Fixes applied to vendored submodules. **Submodule content is not tracked by this repo**, so
these are lost on `git submodule update` and must be re-applied by hand.

## media-consumer-proguard-agp9.patch

Target: `media/` submodule (patched Media3 fork), file `common_library_config.gradle`.

`common_library_config.gradle:25` declares `consumerProguardFiles 'proguard-rules.txt'` for
every library, but 25 of them never shipped that file (`common_ktx`, `session`, `effect`,
`muxer`, `transformer`, `ui_compose`, ...). Older AGP ignored a missing consumer proguard
file; **AGP 9.2.1 fails the build**:

```
Execution failed for task ':media-lib-common-ktx:mergeDebugConsumerProguardFiles'.
> Supplied consumer proguard configuration does not exist: .../common_ktx/proguard-rules.txt
```

The patch makes the declaration conditional on the file existing, reproducing the previous
AGP behaviour. It touches build configuration only — no playback or decoding code.

Apply with:

```bash
cd media && git apply ../patches/media-consumer-proguard-agp9.patch
```

Re-apply after any `git submodule update`. **Relevant to buildspec Phase 6.3** (upstream
merge test): if the build breaks after a merge, check this patch is still present before
concluding the engine adapter is at fault.
