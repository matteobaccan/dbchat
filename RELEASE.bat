
set RELEASE_VER=2.0.3
:: 1. Update version in pom.xml
call mvn versions:set -DnewVersion=%RELEASE_VER%
call mvn versions:commit

:: 2. Build will modify version in README and other files
call mvn clean package

:: 3. Commit and push to main
git add pom.xml DIR.md INSTALL.md
git commit -m "Release version %RELEASE_VER%"
git push origin main
