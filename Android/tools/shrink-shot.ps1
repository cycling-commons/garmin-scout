# Downscales a screenshot so it fits in a review/chat context.
param([Parameter(Mandatory = $true)][string]$Path, [int]$Width = 380)

Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile((Resolve-Path $Path))
$height = [int]($src.Height * $Width / $src.Width)
$dst = New-Object System.Drawing.Bitmap $Width, $height
$g = [System.Drawing.Graphics]::FromImage($dst)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($src, 0, 0, $Width, $height)
$out = [IO.Path]::ChangeExtension((Resolve-Path $Path), '.small.jpg')
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 70
$dst.Save($out, $codec, $params)
$g.Dispose(); $dst.Dispose(); $src.Dispose()
Write-Output $out
