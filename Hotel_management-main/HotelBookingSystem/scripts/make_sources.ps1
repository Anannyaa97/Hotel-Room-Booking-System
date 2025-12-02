$out = "sources.txt"
if (Test-Path $out) { Remove-Item $out }
Get-ChildItem -Path src -Recurse -Filter *.java | ForEach-Object { '"' + $_.FullName + '"' } | Set-Content -Encoding ASCII $out
Write-Output "Wrote $out with $(Get-Content $out | Measure-Object -Line).Lines lines"
