# App-specific R8 rules for the release variant.
#
# The application currently relies on dependency consumer rules. Do not add broad keep rules
# without reproducing a release-build failure and documenting the reflective API that needs them.
# The release workflow validates this file through :app:assembleRelease before any signed artifact
# is considered for distribution.
