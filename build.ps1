# AI Agent for Minecraft - multi-version manual build script (no Gradle)
# Builds the Fabric mod for Minecraft 1.21.6 / 1.21.7 / 1.21.8 / 1.21.9 / 1.21.10 / 1.21.11
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1 -McVersion 1.21.6 -ModVersion 1.2
param(
    [string]$McVersion = "1.21.11",
    [string]$ModVersion = "1.2"
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

# Per-version dependency table
$VER = @{
  "1.21.6"  = @{ ClientSha1="740a125b83dd3447feaa3c5e891ead7fbb21ae28"; MapSha1="848855615bc81e3db1c85e69b6afb150807a1261"; Yarn="1.21.6+build.1";  FabricApi="0.128.2+1.21.6";  Libs="mojang"; MojangJson="https://piston-meta.mojang.com/v1/packages/a77ea2f86c070c4d1a0acc42567ce7bec76f019f/1.21.6.json";  Input="old" }
  "1.21.7"  = @{ ClientSha1="a2db1ea98c37b2d00c83f6867fb8bb581a593e07"; MapSha1="8d83af626cae1865deaf55fbf96934be4886fd45"; Yarn="1.21.7+build.8";  FabricApi="0.129.0+1.21.7";  Libs="mojang"; MojangJson="https://piston-meta.mojang.com/v1/packages/9fe301f4f90b4fbe6b2bbfaab3a9a3f6ef71020d/1.21.7.json";  Input="old" }
  "1.21.8"  = @{ ClientSha1="a19d9badbea944a4369fd0059e53bf7286597576"; MapSha1="bdeb624c3aefba11d9d40f34bc96176350b549b6"; Yarn="1.21.8+build.1";  FabricApi="0.136.1+1.21.8";  Libs="mojang"; MojangJson="https://piston-meta.mojang.com/v1/packages/79e36d4b0cb8a0c0d149e2986f74d9b464655c9b/1.21.8.json";  Input="old" }
  "1.21.9"  = @{ ClientSha1="ce92fd8d1b2460c41ceda07ae7b3fe863a80d045"; MapSha1="3641ccb54eac2153c7e8274823c5a8e046beaba0"; Yarn="1.21.9+build.1";  FabricApi="0.134.1+1.21.9";  Libs="mojang"; MojangJson="https://piston-meta.mojang.com/v1/packages/1f5029dd360b8372e67753297ea800135f8ff4be/1.21.9.json";  Input="new" }
  "1.21.10" = @{ ClientSha1="d3bdf582a7fa723ce199f3665588dcfe6bf9aca8"; MapSha1="7e62354a697f95cf5e7d5981face0583676a9ef7"; Yarn="1.21.10+build.3"; FabricApi="0.138.4+1.21.10"; Libs="mojang"; MojangJson="https://piston-meta.mojang.com/v1/packages/cf316bbecd10861487e6bde2b7d3cf1b60639a92/1.21.10.json"; Input="new" }
  "1.21.11" = @{ ClientSha1="4509ee9b65f226be61142d37bf05f8d28b03417b"; MapSha1="031a68bebf55d824f66d6573d8c752f0e1bf232a"; Yarn="1.21.11+build.6"; FabricApi="0.141.6+1.21.11"; Libs="fabric"; Input="new" }
}
if (-not $VER.ContainsKey($McVersion)) { throw "Unsupported MC version: $McVersion" }
$VC = $VER[$McVersion]

$LOADER_VER = "0.19.3"
$GSON_VER = "2.13.2"
$TR_VER = "0.14.0"
$MAPIO_VER = "0.7.1"
$ASM_VER = "9.9.1"
$TMP_VER = "0.3.0+build.17"

$L = Join-Path $root "lib\$McVersion"
$B = Join-Path $root "build\$McVersion"
$OUT_JAR = Join-Path $root "dist\aafmc_v${ModVersion}_${McVersion}.jar"

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
  throw "javac (JDK 21+) not found. Minecraft 1.21.x requires Java 21+. Install a JDK or set JAVA_HOME."
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
Write-Host "Using javac: $JAVAC  (Minecraft $McVersion)"

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

Write-Host "==> [$McVersion] Preparing directories"
Ensure-Dir "$B\classes"
Ensure-Dir "$B\remap"
Ensure-Dir "$B\tools"
Ensure-Dir "$L\yarn-extracted"
Ensure-Dir "$L\fabric-api-modules"
Ensure-Dir "$L\fabric-api-mojmap"
Ensure-Dir "$L\libs"
Ensure-Dir "dist"

Write-Host "==> [$McVersion] Dependencies"
Get-Url "https://piston-data.mojang.com/v1/objects/$($VC.ClientSha1)/client.jar" "$L\client.jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/yarn/$($VC.Yarn)/yarn-$($VC.Yarn).jar" "$L\yarn-$($VC.Yarn).jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$($VC.FabricApi)/fabric-api-$($VC.FabricApi).jar" "$L\fabric-api-$($VC.FabricApi).jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/fabric-loader/$LOADER_VER/fabric-loader-$LOADER_VER.jar" "$L\fabric-loader-$LOADER_VER.jar"
Get-Url "https://piston-data.mojang.com/v1/objects/$($VC.MapSha1)/client.txt" "$L\client_mappings.txt"
Get-Url "https://maven.fabricmc.net/net/fabricmc/tiny-remapper/$TR_VER/tiny-remapper-$TR_VER.jar" "lib\tiny-remapper-$TR_VER.jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/mapping-io/$MAPIO_VER/mapping-io-$MAPIO_VER.jar" "lib\mapping-io-$MAPIO_VER.jar"
Get-Url "https://repo1.maven.org/maven2/org/ow2/asm/asm/$ASM_VER/asm-$ASM_VER.jar" "lib\asm-$ASM_VER.jar"
Get-Url "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/$ASM_VER/asm-commons-$ASM_VER.jar" "lib\asm-commons-$ASM_VER.jar"
Get-Url "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/$ASM_VER/asm-tree-$ASM_VER.jar" "lib\asm-tree-$ASM_VER.jar"
Get-Url "https://repo1.maven.org/maven2/org/ow2/asm/asm-util/$ASM_VER/asm-util-$ASM_VER.jar" "lib\asm-util-$ASM_VER.jar"
Get-Url "https://maven.fabricmc.net/net/fabricmc/tiny-mappings-parser/$TMP_VER/tiny-mappings-parser-$TMP_VER.jar" "lib\tiny-mappings-parser-$TMP_VER.jar"

Write-Host "==> [$McVersion] Extract yarn mappings"
if (-not (Test-Path "$L\yarn-extracted\mappings.tiny")) {
  & $JAR xf "$L\yarn-$($VC.Yarn).jar" "mappings" 2>&1 | Out-Null
  Copy-Item "mappings\mappings.tiny" "$L\yarn-extracted\mappings.tiny" -Force
}

Write-Host "==> [$McVersion] Minecraft runtime libraries"
if ($VC.Libs -eq "fabric") {
  $manifest = "$L\mc-manifest.json"
  $undName = ($McVersion -replace '\.', '_') + "_unobfuscated.json"
  Get-Url "https://maven.fabricmc.net/net/minecraft/$undName" $manifest
} else {
  $manifest = "$L\mc-manifest.json"
  Get-Url $VC.MojangJson $manifest
}
$json = Get-Content $manifest -Raw | ConvertFrom-Json
$n = 0
foreach ($lib in $json.libraries) {
  $artifact = $lib.downloads.artifact
  if ($artifact) {
    $name = Split-Path $artifact.path -Leaf
    if (-not (Test-Path "$L\libs\$name")) {
      & curl.exe -s -L $artifact.url -o "$L\libs\$name"
      $n++
    }
  }
}
Write-Host "  downloaded $n new libraries"

Write-Host "==> [$McVersion] Build mapping files"
if (-not (Test-Path "$L\map-official-intermediary.tiny") -or -not (Test-Path "$L\map-official-mojang.tiny")) {
  if (-not (Test-Path "build\tools\MergeMappings.class")) {
    & $JAVAC -encoding UTF-8 -cp "lib\mapping-io-$MAPIO_VER.jar" -d "build\tools" "tools\MergeMappings.java"
  }
  & $JAVA -cp "build\tools;lib\mapping-io-$MAPIO_VER.jar" MergeMappings "$L\yarn-extracted\mappings.tiny" "$L\client_mappings.txt" "$L\map-official-intermediary.tiny" "$L\map-official-mojang.tiny"
}

Write-Host "==> [$McVersion] Prepare client jar (mojang-named for compilation)"
$jarEntries = & $JAR tf "$L\client.jar" 2>&1
if ($jarEntries -contains "net/minecraft/client/Minecraft.class") {
  $clientNamed = "$L\client.jar"
  $clientOfficial = "$L\client-official.jar"
  if (-not (Test-Path $clientOfficial)) {
    Write-Host "  mapping client mojang -> official names"
    & $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main "$L\client.jar" $clientOfficial "$L\map-official-mojang.tiny" mojang official 2>&1 | Out-Null
  }
} else {
  $clientNamed = "$L\client-named.jar"
  if (-not (Test-Path $clientNamed)) {
    Write-Host "  mapping client official -> mojang names"
    & $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main "$L\client.jar" $clientNamed "$L\map-official-mojang.tiny" official mojang 2>&1 | Out-Null
  }
  $clientOfficial = "$L\client.jar"
}

Write-Host "==> [$McVersion] Extract + remap fabric-api modules to Mojang names"
if (-not (Test-Path "$L\fabric-api-modules\fabric-key-binding-api-v1-*.jar")) {
  if (Test-Path "META-INF\jars") { Remove-Item -Recurse -Force "META-INF\jars" }
  & $JAR xf "$L\fabric-api-$($VC.FabricApi).jar" "META-INF/jars"
  Get-ChildItem -Recurse -Filter *.jar -Path "META-INF\jars" | ForEach-Object {
    Copy-Item $_.FullName "$L\fabric-api-modules\$($_.Name)" -Force
  }
}
$needRemap = @(Get-ChildItem "$L\fabric-api-modules" -Filter *.jar | Where-Object { -not (Test-Path "$L\fabric-api-mojmap\$($_.BaseName)-mojmap.jar") })
foreach ($m in $needRemap) {
  Write-Host "  remap $($m.Name)"
  & $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main $m.FullName "$B\remap\fapi-off.jar" "$L\map-official-intermediary.tiny" intermediary official $clientNamed 2>&1 | Out-Null
  & $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main "$B\remap\fapi-off.jar" "$L\fabric-api-mojmap\$($m.BaseName)-mojmap.jar" "$L\map-official-mojang.tiny" official mojang $clientNamed 2>&1 | Out-Null
}

Write-Host "==> [$McVersion] Prepare version-adapted sources"
$srcDir = "$B\src"
if (Test-Path $srcDir) { Remove-Item -Recurse -Force $srcDir }
Copy-Item "src\main\java" $srcDir -Recurse
$resDir = "$B\resources"
if (Test-Path $resDir) { Remove-Item -Recurse -Force $resDir }
Copy-Item "src\main\resources" $resDir -Recurse

# Normalize BOM + line endings on copied sources
Get-ChildItem $srcDir -Recurse -Filter *.java | ForEach-Object {
  $c = [System.IO.File]::ReadAllText($_.FullName)
  if ($c.Length -gt 0 -and $c[0] -eq [char]0xFEFF) { $c = $c.Substring(1) }
  $c = $c -replace "`r`n", "`n"
  [System.IO.File]::WriteAllText($_.FullName, $c, (New-Object System.Text.UTF8Encoding($false)))
}

function Apply-Patch([string]$file, [string]$find, [string]$replace) {
  $p = Join-Path $srcDir $file
  $c = [System.IO.File]::ReadAllText($p)
  if (-not $c.Contains($find)) { throw "Patch target not found in $file : [$find]" }
  [System.IO.File]::WriteAllText($p, $c.Replace($find, $replace), (New-Object System.Text.UTF8Encoding($false)))
}

# ResourceLocation instead of Identifier (1.21.6 - 1.21.10)
if ($McVersion -ne "1.21.11") {
  Apply-Patch "com\mcai\McAiClient.java" "Identifier" "ResourceLocation"
  Apply-Patch "com\mcai\agent\GameStateProvider.java" "Identifier" "ResourceLocation"
  Apply-Patch "com\mcai\agent\GameStateProvider.java" "level.dimension().identifier()" "level.dimension().location()"
  Apply-Patch "com\mcai\agent\GameEventWatcher.java" "p.level().dimension().identifier()" "p.level().dimension().location()"
}

# Old input system + authlib 6 (1.21.6 / 1.21.7 / 1.21.8)
if ($VC.Input -eq "old") {
  Apply-Patch "com\mcai\gui\Ui.java" "protected void renderContents" "protected void renderWidget"
  Apply-Patch "com\mcai\McAiClient.java" 'getGameProfile().name()' 'getGameProfile().getName()'
  Apply-Patch "com\mcai\agent\GameStateProvider.java" 'p.getGameProfile().name()' 'p.getGameProfile().getName()'
  Apply-Patch "com\mcai\McAiClient.java" 'profile.name()' 'profile.getName()'
  Apply-Patch "com\mcai\McAiClient.java" 'KeyMapping.Category.register(ResourceLocation.parse(MOD_ID + ":agent"))' '"key.categories.misc"'
  Apply-Patch "com\mcai\McAiClient.java" "getWindow().handle()" "getWindow().getWindow()"
  Apply-Patch "com\mcai\agent\AgentActions.java" "getWindow().handle()" "getWindow().getWindow()"

  # AiAgentScreen
  Apply-Patch "com\mcai\gui\AiAgentScreen.java" "import net.minecraft.client.input.KeyEvent;`nimport net.minecraft.client.input.MouseButtonEvent;`nimport net.minecraft.network.chat.Component;" "import net.minecraft.network.chat.Component;`nimport org.lwjgl.glfw.GLFW;"
  Apply-Patch "com\mcai\gui\AiAgentScreen.java" "public boolean keyPressed(KeyEvent event) {" "public boolean keyPressed(int keyCode, int scanCode, int modifiers) {"
  Apply-Patch "com\mcai\gui\AiAgentScreen.java" "if (event.isConfirmation() && this.getFocused() instanceof EditBox) {" "if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.getFocused() instanceof EditBox) {"
  Apply-Patch "com\mcai\gui\AiAgentScreen.java" "super.keyPressed(event)" "super.keyPressed(keyCode, scanCode, modifiers)"
  Apply-Patch "com\mcai\gui\AiAgentScreen.java" "public boolean mouseClicked(MouseButtonEvent event, boolean isOutside) {" "public boolean mouseClicked(double mouseX, double mouseY, int button) {"
  Apply-Patch "com\mcai\gui\AiAgentScreen.java" "super.mouseClicked(event, isOutside)" "super.mouseClicked(mouseX, mouseY, button)"

  # SessionScreen
  Apply-Patch "com\mcai\gui\SessionScreen.java" "import net.minecraft.client.input.KeyEvent;`nimport net.minecraft.client.input.MouseButtonEvent;`nimport net.minecraft.network.chat.Component;" "import net.minecraft.network.chat.Component;`nimport org.lwjgl.glfw.GLFW;"
  Apply-Patch "com\mcai\gui\SessionScreen.java" "public boolean keyPressed(KeyEvent event) {" "public boolean keyPressed(int keyCode, int scanCode, int modifiers) {"
  Apply-Patch "com\mcai\gui\SessionScreen.java" "if (event.isConfirmation() && this.getFocused() instanceof EditBox) {" "if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.getFocused() instanceof EditBox) {"
  Apply-Patch "com\mcai\gui\SessionScreen.java" "super.keyPressed(event)" "super.keyPressed(keyCode, scanCode, modifiers)"
  Apply-Patch "com\mcai\gui\SessionScreen.java" "public boolean mouseClicked(MouseButtonEvent event, boolean isOutside) {" "public boolean mouseClicked(double mouseX, double mouseY, int button) {"
  Apply-Patch "com\mcai\gui\SessionScreen.java" "event.x()" "mouseX"
  Apply-Patch "com\mcai\gui\SessionScreen.java" "event.y()" "mouseY"
  Apply-Patch "com\mcai\gui\SessionScreen.java" "super.mouseClicked(event, isOutside)" "super.mouseClicked(mouseX, mouseY, button)"

  # TaskScreen
  Apply-Patch "com\mcai\gui\TaskScreen.java" "import net.minecraft.client.input.MouseButtonEvent;`n" ""
  Apply-Patch "com\mcai\gui\TaskScreen.java" "public boolean mouseClicked(MouseButtonEvent event, boolean isOutside) {" "public boolean mouseClicked(double mouseX, double mouseY, int button) {"
  Apply-Patch "com\mcai\gui\TaskScreen.java" "event.x()" "mouseX"
  Apply-Patch "com\mcai\gui\TaskScreen.java" "event.y()" "mouseY"
  Apply-Patch "com\mcai\gui\TaskScreen.java" "super.mouseClicked(event, isOutside)" "super.mouseClicked(mouseX, mouseY, button)"

  # BlacklistScreen
  Apply-Patch "com\mcai\gui\BlacklistScreen.java" "import net.minecraft.client.input.KeyEvent;`nimport net.minecraft.client.input.MouseButtonEvent;`nimport net.minecraft.network.chat.Component;" "import net.minecraft.network.chat.Component;`nimport org.lwjgl.glfw.GLFW;"
  Apply-Patch "com\mcai\gui\BlacklistScreen.java" "public boolean keyPressed(KeyEvent event) {" "public boolean keyPressed(int keyCode, int scanCode, int modifiers) {"
  Apply-Patch "com\mcai\gui\BlacklistScreen.java" "if (event.isConfirmation() && this.getFocused() instanceof EditBox) {" "if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.getFocused() instanceof EditBox) {"
  Apply-Patch "com\mcai\gui\BlacklistScreen.java" "super.keyPressed(event)" "super.keyPressed(keyCode, scanCode, modifiers)"
  Apply-Patch "com\mcai\gui\BlacklistScreen.java" "public boolean mouseClicked(MouseButtonEvent event, boolean isOutside) {" "public boolean mouseClicked(double mouseX, double mouseY, int button) {"
  Apply-Patch "com\mcai\gui\BlacklistScreen.java" "event.x()" "mouseX"
  Apply-Patch "com\mcai\gui\BlacklistScreen.java" "event.y()" "mouseY"
  Apply-Patch "com\mcai\gui\BlacklistScreen.java" "super.mouseClicked(event, isOutside)" "super.mouseClicked(mouseX, mouseY, button)"

  # ConfigScreen
  Apply-Patch "com\mcai\gui\ConfigScreen.java" "import net.minecraft.client.input.MouseButtonEvent;`n" ""
  Apply-Patch "com\mcai\gui\ConfigScreen.java" "public boolean mouseClicked(MouseButtonEvent event, boolean isOutside) {" "public boolean mouseClicked(double mouseX, double mouseY, int button) {"
  Apply-Patch "com\mcai\gui\ConfigScreen.java" "event.x()" "mouseX"
  Apply-Patch "com\mcai\gui\ConfigScreen.java" "event.y()" "mouseY"
  Apply-Patch "com\mcai\gui\ConfigScreen.java" "super.mouseClicked(event, isOutside)" "super.mouseClicked(mouseX, mouseY, button)"
}

Write-Host "==> [$McVersion] Generate icon"
if (-not (Test-Path "src\main\resources\assets\mcai\icon.png")) {
  if (-not (Test-Path "build\tools\GenIcon.class")) { & $JAVAC -encoding UTF-8 -d "build\tools" "tools\GenIcon.java" }
  & $JAVA -cp "build\tools" GenIcon (Join-Path $root "src\main\resources\assets\mcai\icon.png")
}

Write-Host "==> [$McVersion] Patch fabric.mod.json"
$modJson = Get-Content "$resDir\fabric.mod.json" -Raw
$modJson = $modJson.Replace('"version": "1.0.0"', '"version": "' + $ModVersion + '"')
$modJson = $modJson.Replace('"minecraft": "~1.21.11"', '"minecraft": "~' + $McVersion + '"')
[System.IO.File]::WriteAllText("$resDir\fabric.mod.json", $modJson, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "==> [$McVersion] Compile mod"
$cp = @($clientNamed, "$L\fabric-loader-$LOADER_VER.jar") + @(Get-ChildItem "$L\libs" -Filter *.jar | ForEach-Object { $_.FullName }) + @(Get-ChildItem "$L\fabric-api-mojmap" -Filter *.jar | ForEach-Object { $_.FullName })
$cp = $cp -join ";"
$sources = @(Get-ChildItem $srcDir -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& $JAVAC -encoding UTF-8 -g --release 21 -cp $cp -d "$B\classes" @sources
if ($LASTEXITCODE -ne 0) { throw "Compilation failed for $McVersion" }

Write-Host "==> [$McVersion] Copy resources"
Copy-Item "$resDir\fabric.mod.json" "$B\classes\fabric.mod.json" -Force
Get-ChildItem "$resDir\assets" -Recurse -File | ForEach-Object {
  $rel = $_.FullName.Substring($resDir.Length + 1)
  $dest = Join-Path "$B\classes" $rel
  New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
  Copy-Item $_.FullName $dest -Force
}

Write-Host "==> [$McVersion] Package + remap to intermediary"
$libsAll = @(Get-ChildItem "$L\libs" -Filter *.jar | ForEach-Object { $_.FullName })
$remapJarsMojang = @($clientNamed) + $libsAll
$remapJarsOfficial = @($clientOfficial) + $libsAll
& $JAR cf "$B\remap\mod-mojang.jar" -C "$B\classes" .
& $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main "$B\remap\mod-mojang.jar" "$B\remap\mod-official.jar" "$L\map-official-mojang.tiny" mojang official @remapJarsMojang 2>&1 | Out-Null
& $JAVA -cp $REM_CP net.fabricmc.tinyremapper.Main "$B\remap\mod-official.jar" "$B\remap\mod-intermediary.jar" "$L\map-official-intermediary.tiny" official intermediary @remapJarsOfficial 2>&1 | Out-Null
Copy-Item "$B\remap\mod-intermediary.jar" $OUT_JAR -Force

Write-Host ""
Write-Host "BUILD OK [$McVersion] -> $OUT_JAR"
Write-Host "Size: $((Get-Item $OUT_JAR).Length) bytes"
