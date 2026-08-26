# Minimal placeholder ProGuard/R8 rules for the release build type.
#
# Intentionally empty beyond this comment: this project's dependencies (Media3, Compose,
# ktor via subtitle-engine) each have their own real-world consumer-rule needs, but tuning
# those correctly requires a working build environment to verify against (none was
# available when this file was created -- see the remediation audit). Guessing at rules
# here risks silently stripping code that's actually reachable via reflection. Treat real
# R8 tuning for this app as a separate follow-up once release builds can be tested
# end-to-end.
