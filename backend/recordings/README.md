# Recordings for REPLAY mode

`<scenarioId>.wav`: PCM 16 kHz, mono, 16-bit (the format of `/ws/audio`). The only audio allowed on disk (AUD-02).

The files are not in the repository. Make them with `make recordings` (macOS: `say` with the Polish voice Zosia,
then `afconvert`). They are **synthetic speech**, not recordings of people; the audit and every screen show
the mode REPLAY. To use the team's own recordings, put a WAV file with the scenario's id here in the same format.
