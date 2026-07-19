# grab-window.ps1 - reliably capture the TodoList client window to a PNG.
#
# Uses the Win32 PrintWindow API (PW_RENDERFULLCONTENT), which renders the
# window's OWN buffer to a bitmap. Unlike a screen-scrape (CopyFromScreen), it is
# correct even when the window is occluded, behind another window, or partly
# off-screen - so captures never accidentally show a terminal on top.
#
# Usage (non-interactive):
#   powershell -File scripts\grab-window.ps1 -Label welcome-light
#   powershell -File scripts\grab-window.ps1 -Label mainmenu-dark -MoveX 60 -MoveY 40 -MoveW 1040 -MoveH 720
#
# Output: screenshots\<Label>.png (gitignored). Matches the window whose title
# contains "Management System" (the client), so terminal tabs never match.

param(
  [string]$Label = 'capture',
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

$proc = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -match 'Management System' } | Select-Object -First 1
if (-not $proc) { Write-Host "NO CLIENT WINDOW (title match 'Management System'). Is the client running?"; exit 1 }
$h = $proc.MainWindowHandle
if ($MoveW -gt 0) { [PW]::Move($h, $MoveX, $MoveY, $MoveW, $MoveH); Start-Sleep -Milliseconds 900 }
$path = Join-Path $outDir "$Label.png"
$size = [PW]::Grab($h, $path)
Write-Host "SAVED $path ($size) title='$($proc.MainWindowTitle)'"
