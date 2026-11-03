$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backend = Join-Path $root 'feibo_backend_master'
$output = Join-Path $root 'tmp\video-generation-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$report = Get-ChildItem (Join-Path $backend 'digital-doctor-app\target\surefire-reports') -Filter 'TEST-*.xml' | Select-Object -First 1
if (-not $report) { throw 'Build the backend tests once to create a dependency classpath report.' }
[xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
$existing = ($xml.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
$paths = @($output)
$paths += Get-ChildItem $backend -Directory -Filter 'digital-doctor-*' | ForEach-Object { Join-Path $_.FullName 'target\classes' }
$paths += Get-ChildItem $backend -Directory -Filter 'digital-doctor-*' | ForEach-Object { Get-ChildItem (Join-Path $_.FullName 'target') -Filter '*.jar' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName }
$paths += $existing -split ';' | Where-Object { $_ -like '*.jar' -and (Test-Path -LiteralPath $_) }
$paths += Get-ChildItem (Join-Path $env:USERPROFILE '.m2\repository\org\junit\platform\junit-platform-launcher\1.11.4') -Filter '*.jar' | Select-Object -ExpandProperty FullName
$paths += Get-ChildItem (Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2') -Recurse -Filter '*.jar' | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
$cp = ($paths | Select-Object -Unique) -join ';'
$sources = @(Get-ChildItem (Join-Path $backend 'digital-doctor-app\src\main\java\com\hzmwmt\videogeneration') -Filter '*.java' | Select-Object -ExpandProperty FullName)
$sources += Get-ChildItem (Join-Path $backend 'digital-doctor-app\src\test\java\com\hzmwmt\videogeneration') -Filter '*.java' | Select-Object -ExpandProperty FullName
$sources += Join-Path $PSScriptRoot 'video-generation-qa\RunVideoTests.java'
$sources += Join-Path $backend 'digital-doctor-domain\src\main\java\com\hzmwmt\domain\security\AIModelApiKeyCipher.java'
$sources += Join-Path $backend 'digital-doctor-domain\src\main\java\com\hzmwmt\domain\entitlement\IEntitlementService.java'
$sources += Join-Path $backend 'digital-doctor-domain\src\main\java\com\hzmwmt\domain\entitlement\EntitlementGrantRequest.java'
$arguments = @('-encoding', 'UTF-8', '-cp', ('"' + $cp.Replace('\','/') + '"'), '-d', ('"' + $output.Replace('\','/') + '"'))
$arguments += $sources | ForEach-Object { '"' + $_.Replace('\','/') + '"' }
$argumentFile = Join-Path $output 'compile.args'
[IO.File]::WriteAllLines($argumentFile, $arguments, [Text.UTF8Encoding]::new($false))
& javac '-J-Dfile.encoding=UTF-8' ('@' + $argumentFile)
if ($LASTEXITCODE -ne 0) { throw 'Video module compilation failed' }
New-Item -ItemType Directory -Force -Path (Join-Path $output 'db') | Out-Null
Copy-Item -LiteralPath (Join-Path $backend 'digital-doctor-app\src\main\resources\db\video-generation.sql') -Destination (Join-Path $output 'db\video-generation.sql')
$runArgs = Join-Path $output 'run.args'
[IO.File]::WriteAllLines($runArgs, @('-cp', ('"' + $cp.Replace('\','/') + '"'), 'RunVideoTests'), [Text.Encoding]::GetEncoding(936))
& java '-Dfile.encoding=UTF-8' ('@' + $runArgs)
if ($LASTEXITCODE -ne 0) { throw 'Video module tests failed' }
