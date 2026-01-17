# Install Java 17 - Step by Step

## The Problem
- You have Java 25 installed
- Gradle doesn't support Java 25 yet
- Spring Boot 3.2.0 requires Java 17 minimum

## Solution: Install Java 17

### Method 1: Using Homebrew (Recommended - Easiest)

```bash
# Install Java 17
brew install openjdk@17

# Set JAVA_HOME for current session
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Verify it worked
java -version
# Should show: openjdk version "17.x.x"

# Now run backend
cd backend
./gradlew bootRun
```

**To make it permanent**, add to `~/.zshrc`:
```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
source ~/.zshrc
```

### Method 2: Using SDKMAN (Java Version Manager)

```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash

# Restart terminal or run:
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

### Method 3: Download and Install Manually

1. Go to: https://adoptium.net/temurin/releases/?version=17
2. Download: **macOS ARM64** (for Apple Silicon) or **macOS x64** (for Intel)
3. Install the `.pkg` file
4. Set JAVA_HOME:
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
   ```

## After Installing Java 17

### 1. Verify Java Version
```bash
java -version
# Should show: openjdk version "17.x.x"
```

### 2. Stop Old Gradle Daemons
```bash
cd backend
./gradlew --stop
```

### 3. Clean Build
```bash
cd backend
./gradlew clean
```

### 4. Start Backend
```bash
cd backend
./gradlew bootRun
```

**Wait for:**
```
Started RdbmsApplication in X.XXX seconds
```

## Quick Commands (Copy & Paste)

If you have Homebrew:
```bash
brew install openjdk@17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
java -version
cd backend
./gradlew --stop
./gradlew bootRun
```

## Why Java 17?

- ✅ Spring Boot 3.2.0 requires Java 17 minimum
- ✅ Gradle fully supports Java 17
- ✅ Most stable and widely used
- ✅ Better compatibility

## Troubleshooting

### "Command not found: brew"
Install Homebrew first:
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### "Java version still shows 25"
Make sure JAVA_HOME is set:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
java -version
```

### Still getting errors?
1. Make sure Java 17 is installed: `/usr/libexec/java_home -V`
2. Set JAVA_HOME: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`
3. Stop Gradle: `./gradlew --stop`
4. Clean: `./gradlew clean`
5. Try again: `./gradlew bootRun`

---

**Once Java 17 is installed and JAVA_HOME is set, the backend should start successfully!** 🎉
