# Crops a region of an image and upscales it, for inspecting fine detail.
param(
  [Parameter(Mandatory = $true)][string]$Path,
  [int]$X, [int]$Y, [int]$W, [int]$H, [int]$Scale = 2
)

Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile((Resolve-Path $Path))
if ($W -le 0) { $W = $src.Width - $X }
if ($H -le 0) { $H = $src.Height - $Y }
$dst = New-Object System.Drawing.Bitmap ($W * $Scale), ($H * $Scale)
$g = [System.Drawing.Graphics]::FromImage($dst)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, ($W * $Scale), ($H * $Scale)),
  (New-Object System.Drawing.Rectangle $X, $Y, $W, $H), [System.Drawing.GraphicsUnit]::Pixel)
$out = [IO.Path]::ChangeExtension((Resolve-Path $Path), '.crop.png')
$dst.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $dst.Dispose(); $src.Dispose()
Write-Output $out
