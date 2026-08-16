# AI Agent for Minecraft - manual build script (no Gradle)
# Builds the Fabric mod for Minecraft 1.21.11 using javac + tiny-remapper directly.
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

function Find-Javac {
  $candidates = @()
  if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\javac.exe") }
  $candidates += @(Get-ChildItem "C:\Program Files\Java\*\*\bin\javac.exe" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  $candidates += @(Get-ChildItem "C:\Program Files\Java\*\bin\javac.exe" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  $candidates += @(Get-ChildItem "C:\Program Files\Eclipse Adoptium\*\bin\javac.exe" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  $candidates += @(Get-ChildItem "C:\Program Files\Microsoft\jdk*\bin\javac.exe" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  $candidates += @(Get-ChildItem "$env:LOCALAPPDATA\Programs\*\*\bin\javac.exe" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  $candidates = $candidates | Sort-Object -Unique
  foreach ($c in $candidates) {
    if (Test-Path $c) {
      try {
        $v = & $c -version 2>&1 | Out-String
        if ($v -match 'javac (2[1-9]|[3-9][0-9])') { return $c }
      } catch {}
    }
  }
  $which = Get-Command javac -ErrorAction SilentlyContinue
  if ($which) { return $which.Source }
  throw "javac (JDK 21+) not found. Minecraft 1.21.11 requires Java 21+. Install a JDK or set JAVA_HOME."
}

$JAVAC = Find-Javac
$javaHome = Split-Path (Split-Path $JAVAC -Parent) -Parent
$JAVA = Join-Path $javaHome "bin\java.exe"
$JAR = Join-Path $javaHome "bin\jar.exe"
if (-not (Test-Path $JAVA)) {
  $j = Get-Command java -ErrorAction SilentlyContinue
  if ($j) { $JAVA = $j.Source }
}
if (-not (Test-Path $JAR)) {
  $j = Get-Command jar -ErrorAction SilentlyContinue
  if ($j) { $JAR = $j.Source }
}
Write-Host "Using javac: $JAVAC"

$MOD_VERSION = "1.0.0"
$MOD_JAR = "dist\mcai-$MOD_VERSION.jar"
$CLIENT_SHA1 = "4509ee9b65f226be61142d37bf05f8d28b03417b"
$YARN_VER = "1.21.11+build.6"
$FABRIC_API_VER = "0.141.6+1.21.11"
$LOADER_VER = "0.19.3"
$MOJANG_MAP_VER = "031a68bebf55d824f66d6573d8c752f0e1bf232a"
$GSON_VER = "2.13.2"
$TR_VER = "0.14.0"
$MAPIO_VER = "0.7.1"
$ASM_VER = "9.9.1"
$TMP_VER = "0.3.0+build.17"

$REM_JARS = @(
  "lib\tiny-remapper-$TR_VER.jar",
  "lib\mapping-io-$MAPIO_VER.jar",
  "lib\asm-$ASM_VER.jar",
  "lib\asm-commons-$ASM_VER.jar",
  "lib\asm-tree-$ASM_VER.jar",
  "lib\asm-util-$ASM_VER.jar",
  "lib\tiny-mappings-parser-$TMP_VER.jar"
)
$REM_CP = ($REM_JARS | ForEach-Object { (Join-Path $root $_).Replace("/", "\") }) -join ";"

function Get-Url([string]$url, [string]$dest) {
  if (Test-Path $dest) { return }
  Write-Host "Downloading $dest"
  & curl.exe -s -L $url -o $dest
  if (-not (Test-Path $dest)) { throw "Download failed: $url" }
  if ((Get-Item $dest).Length -eq 0) { Remove-Item $dest; throw "Download empty: $url" }
}

function Ensure-Dir([string]$path) {
  New-Item -ItemType Directory -Force -Path $path | Out-Null
}

Write-Host "==> Preparing directories"
Ensure-Dir "build\classes"
Ensure-Dir "build\remap"
Ensure-Dir "dist"
Ensure-Dir "lib\yarn-extracted"
Ensure-Dir "lib\fabric-api-modules"
Ensure-Dir "lib\fabric-api-mojmap"

Write-Host "==> Dependencies"
Get-Url "https://piston-data.mojang.com/v1/objects/$CLIENT_SHA1/client.jar" "lib\client.jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/yarn/$YARN_VER/yarn-$YARN_VER.jar" "lib\yarn-$YARN_VER.jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FABRIC_API_VER/fabric-api-$FABRIC_API_VER.jar" "lib\fabric-api-$FABRIC_API_VER.jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/fabric-loader/$LOADER_VER/fabric-loader-$LOADER_VER.jar" "lib\fabric-loader-$LOADER_VER.jar"
Get-Url "https://piston-data.mojang.com/v1/objects/$MOJANG_MAP_VER/client.txt" "lib\client_mappings.txt"
Get-Url "https://libraries.minecraft.net/com/google/code/gson/gson/$GSON_VER/gson-$GSON_VER.jar" "lib\gson-$GSON_VER.jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/tiny-remapper/$TR_VER/tiny-remapper-$TR_VER.jar" "lib\tiny-remapper-$TR_VER.jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/mapping-io/$MAPIO_VER/mapping-io-$MAPIO_VER.jar" "lib\mapping-io-$MAPIO_VER.jar"
Get-Url "https://repo1.maven.org/maven2/org/ow2/asm/asm/$ASM_VER/asm-$ASM_VER.jar" "lib\asm-$ASM_VER.jar"
Get-Url "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/$ASM_VER/asm-commons-$ASM_VER.jar" "lib\asm-commons-$ASM_VER.jar"
Get-Url "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/$ASM_VER/asm-tree-$ASM_VER.jar" "lib\asm-tree-$ASM_VER.jar"
Get-Url "https://repo1.maven.org/maven2/org/ow2/asm/asm-util/$ASM_VER/asm-util-$ASM_VER.jar" "lib\asm-util-$ASM_VER.jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/tiny-mappings-parser/$TMP_VER/tiny-mappings-parser-$TMP_VER.jar" "lib\tiny-mappings-parser-$TMP_VER.jar"

Write-Host "==> Extract yarn mappings"
if (-not (Test-Path "lib\yarn-extracted\mappings.tiny")) {
  & $JAR xf "lib\yarn-$YARN_VER.jar" "mappings" 2>&1 | Out-Null
  Copy-Item "mappings\mappings.tiny" "lib\yarn-extracted\mappings.tiny" -Force
}

Write-Host "==> Minecraft runtime libraries"
$manifest = "lib\mc-manifest.json"
Get-Url "https://maven.fabricmc.net/net/minecraft/1_21_11_unobfuscated.json" $manifest
$libDir = "lib\libs"
Ensure-Dir $libDir
if (-not (Test-Path "$libDir\.done")) {
  $json = Get-Content $manifest -Raw | ConvertFrom-Json
  $n = 0
  foreach ($lib in $json.libraries) {
    $artifact = $lib.downloads.artifact
    if ($artifact) {
      $name = Split-Path $artifact.path -Leaf
      if (-not (Test-Path "$libDir\$name")) {
        & curl.exe -s -L $artifact.url -o "$libDir\$name"
        $n++
      }
    }
  }
  Write-Host "  downloaded $n new libraries"
  New-Item -ItemType File -Path "$libDir\.done" | Out-Null
}

Write-Host "==> Build mapping files"
if (-not (Test-Path "lib\map-official-intermediary.tiny") -or -not (Test-Path "lib\map-official-mojang.tiny")) {
  Ensure-Dir "build\tools"
  & $JAVAC -encoding UTF-8 -cp "lib\mapping-io-$MAPIO_VER.jar" -d "build\tools" "tools\MergeMappings.java"
  & $JAVA -cp "build\tools;lib\mapping-io-$MAPIO_VER.jar" MergeMappings "lib\yarn-extracted\mappings.tiny" "lib\client_mappings.txt" "lib\map-official-intermediary.tiny" "lib\map-official-mojang.tiny"
}

Write-Host "==> Extract + remap fabric-api modules to Mojang names"
if (-not (Test-Path "lib\fabric-api-modules\fabric-key-binding-api-v1-1.1.7+4fc5413f3e.jar")) {
  & $JAR xf "lib\fabric-api-$FABRIC_API_VER.jar" "META-INF/jars"
  Get-ChildItem -Recurse -Filter *.jar -Path "META-INF\jars" | ForEach-Object {
    Copy-Item $_.FullName "lib\fabric-api-modules\$($_.Name)" -Force
  }
}
$needRemap = @(Get-ChildItem "lib\fabric-api-modules" -Filter *.jar | Where-Object { $_.Name -notmatch "official" -and -not (Test-Path "lib\fabric-api-mojmap\$($_.BaseName)-mojmap.jar") })
if ($needRemap.Count -gt 0) {
  foreach ($m in $needRemap) {
    Write-Host "  remap $($m.Name)"
    & $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main $m.FullName "build\remap\fapi-off.jar" "lib\map-official-intermediary.tiny" intermediary official "lib\client.jar" 2>&1 | Out-Null
    & $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main "build\remap\fapi-off.jar" "lib\fabric-api-mojmap\$($m.BaseName)-mojmap.jar" "lib\map-official-mojang.tiny" official mojang "lib\client.jar" 2>&1 | Out-Null
  }
}

Write-Host "==> Strip BOM from sources"
Get-ChildItem "src\main\java" -Recurse -Filter *.java | ForEach-Object {
  $c = [System.IO.File]::ReadAllText($_.FullName)
  if ($c.Length -gt 0 -and $c[0] -eq [char]0xFEFF) {
    [System.IO.File]::WriteAllText($_.FullName, $c.Substring(1), (New-Object System.Text.UTF8Encoding($false)))
  }
}

Write-Host "==> Generate icon"
if (-not (Test-Path "src\main\resources\assets\mcai\icon.png")) {
  & $JAVAC -encoding UTF-8 -d "build\tools" "tools\GenIcon.java"
  & $JAVA -cp "build\tools" GenIcon (Join-Path $root "src\main\resources\assets\mcai\icon.png")
}

Write-Host "==> Compile mod"
$cp = @("lib\client.jar", "lib\fabric-loader-$LOADER_VER.jar") + @(Get-ChildItem "lib\libs" -Filter *.jar | ForEach-Object { $_.FullName }) + @(Get-ChildItem "lib\fabric-api-mojmap" -Filter *.jar | ForEach-Object { $_.FullName })
$cp = $cp -join ";"
$sources = @(Get-ChildItem "src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& $JAVAC -encoding UTF-8 -g --release 21 -cp $cp -d "build\classes" @sources
if ($LASTEXITCODE -ne 0) { throw "Compilation failed" }

Write-Host "==> Copy resources"
Copy-Item "src\main\resources\fabric.mod.json" "build\classes\fabric.mod.json" -Force
Get-ChildItem "src\main\resources\assets" -Recurse -File | ForEach-Object {
  $rel = $_.FullName.Substring((Join-Path $root "src\main\resources").Length + 1)
  $dest = Join-Path "build\classes" $rel
  New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
  Copy-Item $_.FullName $dest -Force
}

Write-Host "==> Package + remap to intermediary"
if (-not (Test-Path "lib\client-official.jar")) {
  Write-Host "  mapping client -> official names"
  & $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main "lib\client.jar" "lib\client-official.jar" "lib\map-official-mojang.tiny" mojang official 2>&1 | Out-Null
}
$libsAll = @(Get-ChildItem "lib\libs" -Filter *.jar | ForEach-Object { $_.FullName })
$remapJarsMojang = @("lib\client.jar") + $libsAll
$remapJarsOfficial = @("lib\client-official.jar") + $libsAll
& $JAR cf "build\remap\mod-mojang.jar" -C "build\classes" .
& $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main "build\remap\mod-mojang.jar" "build\remap\mod-official.jar" "lib\map-official-mojang.tiny" mojang official @remapJarsMojang 2>&1 | Out-Null
& $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main "build\remap\mod-official.jar" "build\remap\mod-intermediary.jar" "lib\map-official-intermediary.tiny" official intermediary @remapJarsOfficial 2>&1 | Out-Null
Copy-Item "build\remap\mod-intermediary.jar" $MOD_JAR -Force

Write-Host ""
Write-Host "BUILD OK -> $MOD_JAR"
Write-Host "Size: $((Get-Item $MOD_JAR).Length) bytes"
