$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
$mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$propType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$mgr = Await ($mgrType::RequestAsync()) $mgrType
function State {
  $s = $mgr.GetCurrentSession()
  if ($s -eq $null) { return '{"session":false}' }
  $p = Await ($s.TryGetMediaPropertiesAsync()) $propType
  $status = $s.GetPlaybackInfo().PlaybackStatus.ToString()
  return (ConvertTo-Json -Compress @{ session = $true; title = [string]$p.Title; artist = [string]$p.Artist; playing = ($status -eq 'Playing'); app = [string]$s.SourceAppUserModelId })
}
function Press($name) {
  $s = $mgr.GetCurrentSession()
  if ($s -eq $null) { return }
  switch ($name) {
    'next' { [void](Await ($s.TrySkipNextAsync()) ([bool])) }
    'prev' { [void](Await ($s.TrySkipPreviousAsync()) ([bool])) }
    'pause' { [void](Await ($s.TryTogglePlayPauseAsync()) ([bool])) }
  }
}
'{"ready":true}'
$run = $true
while ($run) {
  $line = [Console]::In.ReadLine()
  if ($line -eq $null) { break }
  $cmd = $line.Trim()
  if ($cmd -eq 'quit') { break }
  if ($cmd -ne 'poll') { Press $cmd; Start-Sleep -Milliseconds 250 }
  State
}
