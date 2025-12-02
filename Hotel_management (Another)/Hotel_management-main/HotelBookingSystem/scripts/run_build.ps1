Set-Location -LiteralPath 'C:\Users\user\Hotel_management (Another)\Hotel_management-main\HotelBookingSystem'
if (-not (Test-Path out)) { New-Item -ItemType Directory out | Out-Null }
$files = Get-ChildItem -Path src -Recurse -Filter *.java | Where-Object { $_.Name -ne 'OwnerDashboard.java' } | ForEach-Object { $_.FullName }
Write-Host "Found $($files.Count) java files"
$argList = @('-cp','src\lib\mysql-connector-j-9.4.0.jar','-d','out') + $files
Write-Host "Running javac with args: $($argList[0..3] -join ' ') and $($files.Count) files"
& javac @argList
if ($LASTEXITCODE -ne 0) { Write-Host "javac failed with exit code $LASTEXITCODE"; exit $LASTEXITCODE }
Write-Host "Compilation succeeded; running Main"
& java -cp 'out;src\lib\mysql-connector-j-9.4.0.jar' Main
