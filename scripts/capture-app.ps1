# capture-app.ps1 - screenshot the running TodoList JavaFX window.
#
# WHY: Playwright cannot drive a JavaFX desktop app, and a clean `mvn package` is
# NOT evidence the UI looks right. This captures just the app window to a PNG so a
# visual change can actually be seen and eyeballed before any release.
#
# LOOP (run each in its own terminal, then capture):
#   1. Start the local HTTP API (starts an embedded Postgres for dev):
#        cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -q -pl api exec:java
#   2. Run the client:
#        cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -q -pl client javafx:run
#   3. Navigate the client to the screen you want (and toggle dark mode for the dark shot),
#      then run THIS script and type a label at the prompt (e.g. "mainmenu-dark").
#
# Output PNGs land in <repo>\screenshots\ (gitignored). Read them back and eyeball.

Set-Location (Join-Path $PSScriptRoot '..')

$outDir = Join-Path (Get-Location) 'screenshots'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

$titleMatch = 'TodoList'
$label = Read-Host "Label for this screenshot (e.g. welcome-light, mainmenu-dark)"
if ([string]::IsNullOrWhiteSpace($label)) { $label = 'capture' }

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win {
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

$proc = Get-Process |
  Where-Object { $_.MainWindowTitle -match $titleMatch -and $_.MainWindowHandle -ne 0 } |
  Select-Object -First 1

if ($null -eq $proc) {
  Write-Host "No window whose title matches '$titleMatch' is open. Is the client running?" -ForegroundColor Yellow
  return
}

$h = $proc.MainWindowHandle
[Win]::ShowWindow($h, 9) | Out-Null      # SW_RESTORE
[Win]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 400            # let it come to front and repaint

$rect = New-Object Win+RECT
[Win]::GetWindowRect($h, [ref]$rect) | Out-Null
$width  = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top
if ($width -le 0 -or $height -le 0) {
  Write-Host "Could not read window bounds (got ${width}x${height})." -ForegroundColor Yellow
  return
}

$bmp = New-Object System.Drawing.Bitmap $width, $height
$gfx = [System.Drawing.Graphics]::FromImage($bmp)
$gfx.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bmp.Size)

$path = Join-Path $outDir "$label.png"
$bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
$gfx.Dispose(); $bmp.Dispose()

Write-Host "Saved $path (${width}x${height})" -ForegroundColor Green
