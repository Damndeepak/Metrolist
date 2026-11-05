# Profiling builds keep R8's shrinking and inlining (so timings match release) but not its
# renaming, so method samples and traces read as real class and function names.
-dontobfuscate
