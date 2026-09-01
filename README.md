# Watermelon Auto Sync Integration Bundle

This bundle contains the completed sandbox implementation for Watermelon automatic subtitle synchronization.

## Apply to a Watermelon source tree

```bash
python3 apply_autosync_patch.py /path/to/Watermelon-MediaPlayer-main
```

The patcher targets the verified `main` layout at commit:

`206bf2076c0b25469ef165ebfa07dec9736c872d`

## Included

- common sync contracts/models
- subtitle activity fingerprints
- probe selection
- offset correlation and consensus
- drift/conflict rejection
- subtitle fingerprinting
- persistent manual/automatic authority
- SQLite v11→v12 migration
- sparse MediaExtractor/MediaCodec audio probing
- lightweight speech-likelihood estimator
- phone/TV timing controls
- Auto Sync setting
- sandbox unit/algorithm tests
- validation report

See `VALIDATION.md` for executed checks and the remaining Android-device/Gradle validation boundary.
