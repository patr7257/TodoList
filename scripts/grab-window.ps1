# grab-window.ps1 - capture any desktop window to a PNG without focusing it.
#
# Uses the Win32 PrintWindow API (PW_RENDERFULLCONTENT), which renders the
# window's OWN buffer to a bitmap. Unlike a screen-scrape (CopyFromScreen), it is
# correct even when the window is occluded, behind another window, or partly
# off-screen - so captures never accidentally show a terminal on top, and no
# window ever has to be brought to the foreground to be seen.
#
# This repo no longer ships a desktop app (issue #66 retired the JavaFX client),
# so nothing here calls this script. It is kept on purpose: it is the reference
# PrintWindow implementation the global instructions point at as the safe
# alternative to synthetic input or forced window activation. Pass -TitleMatch
# to aim it at whatever window you actually want.
#
# Usage (non-interactive):
#   powershell -File scripts\grab-window.ps1 -TitleMatch 'Notepad' -Label notes
#   powershell -File scripts\grab-window.ps1 -TitleMatch 'My App' -Label wide -MoveX 60 -MoveY 40 -MoveW 1040 -MoveH 720
#
# Output: screenshots\<Label>.png (gitignored).

param(
  [string]$Label = 'capture',
  [string]$TitleMatch = 'Management System',
  [int]$MoveX = -1, [int]$MoveY = -1, [int]$MoveW = -1, [int]$MoveH = -1
)

Set-Location (Join-Path $PSScriptRoot '..')
$outDir = Join-Path (Get-Location) 'screenshots'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System; using System.Drawing; using System.Runtime.InteropServices;
public class PW {
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdcBlt, uint nFlags);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int ht, bool repaint);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  public static void Move(IntPtr h, int x, int y, int w, int ht){ MoveWindow(h, x, y, w, ht, true); }
  public static string Grab(IntPtr h, string path){
    RECT r; GetWindowRect(h, out r);
    int w = r.Right-r.Left, ht = r.Bottom-r.Top;
    var bmp = new Bitmap(w, ht);
    using(var g = Graphics.FromImage(bmp)){
      IntPtr hdc = g.GetHdc();
      PrintWindow(h, hdc, 0x2);
      g.ReleaseHdc(hdc);
    }
    bmp.Save(path, System.Drawing.Imaging.ImageFormat.Png);
    bmp.Dispose();
    return w + "x" + ht;
  }
}
"@

$proc = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -match $TitleMatch } | Select-Object -First 1
if (-not $proc) { Write-Host "NO WINDOW matched title '$TitleMatch'. Is it running?"; exit 1 }
$h = $proc.MainWindowHandle
if ($MoveW -gt 0) { [PW]::Move($h, $MoveX, $MoveY, $MoveW, $MoveH); Start-Sleep -Milliseconds 900 }
$path = Join-Path $outDir "$Label.png"
$size = [PW]::Grab($h, $path)
Write-Host "SAVED $path ($size) title='$($proc.MainWindowTitle)'"
