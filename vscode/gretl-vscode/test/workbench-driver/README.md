# GRETL workbench evidence driver

Test-only, read-only assertions for the installed product. Not part of its VSIX.
Set `gretlEvidence.directory` to a private external artifact directory in an
isolated profile. Native mouse/keyboard checks and screenshots are additional
mandatory evidence; provider API results alone do not constitute GUI acceptance.

`GRETL Evidence: Capture Buffer State Only` records workspace file buffers,
hashes, versions, dirty flags and the active selection without invoking language
providers. Use paired captures to verify source invariance during view gestures.
