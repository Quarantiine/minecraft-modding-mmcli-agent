import os, glob

# Find minecraft jar in gradle cache
gradle_cache = os.path.expanduser("~/.gradle/caches")
jars = glob.glob(f"{gradle_cache}/**/minecraft-merged-*.jar", recursive=True)
print("Found jars:", jars[:3])
