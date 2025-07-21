RELEASE PROCESS
===============

# 1. Update version in pom.xml
mvn versions:set -DnewVersion=2.0.0
mvn versions:commit

# 2. Commit and push to main
git add pom.xml DIR.md INSTALL.md
git commit -m "Release version 2.0.0"
git push origin main

# 3. The workflow automatically:
#    - Runs tests
#    - Builds all variants
#    - Creates GitHub release with artifacts
#    - Publishes to Maven Central (when enabled)



