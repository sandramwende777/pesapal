# Java 25 Compatibility Solution

## Issue
- You have Java 25 installed
- Gradle build scripts are being compiled with Java 25 (class file version 69)
- Older Gradle versions don't support Java 25

## Solution Applied

✅ **Upgraded to Gradle 8.11** - Latest version that supports Java 25
✅ **Cleaned Gradle caches** - Removed old compiled build scripts
✅ **Updated build.gradle** - Set Java 17 as source/target compatibility

## Current Configuration

- **Gradle**: 8.11 (supports Java 25)
- **Java Runtime**: 25 (for running Gradle)
- **Java Source/Target**: 17 (for compiling your code)

## If It Still Doesn't Work

### Option 1: Install Java 17 (Recommended)

```bash
# Using Homebrew
brew install openjdk@17

# Set JAVA_HOME for this session
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Or add to ~/.zshrc for permanent
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
source ~/.zshrc
```

Then run:
```bash
cd backend
./gradlew bootRun
```

### Option 2: Use SDKMAN (Easy Java Version Manager)

```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 17
sdk install java 17.0.10-tem

# Use Java 17
sdk use java 17.0.10-tem

# Verify
java -version  # Should show Java 17

# Run backend
cd backend
./gradlew bootRun
```

### Option 3: Download Java 17 Manually

1. Download from: https://adoptium.net/temurin/releases/?version=17
2. Install the .pkg file
3. Set JAVA_HOME:
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
   ```

## Verify Java Version

```bash
java -version
# Should show the version you want to use
```

## Why Java 17?

- Spring Boot 3.2.0 requires Java 17 minimum
- Most stable and widely supported
- Better compatibility with Gradle and Spring Boot

## Current Status

✅ Gradle 8.11 downloaded
✅ Build configuration updated
✅ Backend should start with Java 25 (if Gradle 8.11 works)

If you still get errors, **install Java 17** using one of the options above.
