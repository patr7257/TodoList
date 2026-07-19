# snap.ps1 - capture the CURRENT TodoList client window to the next numbered PNG.
#
# Paste the SAME one line each time; it auto-increments the filename, so you never
# edit the command. Run it once per screen you want captured (main menu, a task
# list, and each again in dark mode). Uses PrintWindow, so the app does not need to
# be in the foreground.
#
#   cd "C:\Users\pr\repos\1-Personal\-todolist-management-system"; powershell -ExecutionPolicy Bypass -File scripts\snap.ps1

Set-Location (Join-Path $PSScriptRoot '..')
$outDir = Join-Path (Get-Location) 'screenshots'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System; using System.Drawing; using System.Runtime.InteropServices;
public class Snap {
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdcBlt, uint nFlags);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  public static string Grab(IntPtr h, string path){
    RECT r; GetWindowRect(h, out r);
    int w = r.Right-r.Left, ht = r.Bottom-r.Top;
    var bmp = new Bitmap(w, ht);
    using(var g = Graphics.FromImage(bmp)){ IntPtr hdc = g.GetHdc(); PrintWindow(h, hdc, 0x2); g.ReleaseHdc(hdc); }
    bmp.Save(path, System.Drawing.Imaging.ImageFormat.Png); bmp.Dispose();
    return w + "x" + ht;
  }
}
"@

$proc = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -match 'Management System' } | Select-Object -First 1
if (-not $proc) { Write-Host "No TodoList client window found. Is the app open?"; exit 1 }

$n = 1
while (Test-Path (Join-Path $outDir ("snap-{0:00}.png" -f $n))) { $n++ }
$path = Join-Path $outDir ("snap-{0:00}.png" -f $n)
$size = [Snap]::Grab($proc.MainWindowHandle, $path)
Write-Host "Saved snap-$('{0:00}' -f $n).png ($size) - screen: '$($proc.MainWindowTitle)'"
