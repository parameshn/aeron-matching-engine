function Get-Applied($path) {
    Select-String -Path $path -Pattern 'log position (\d+), applied \d+ bytes, resting=(\d+) hash=(\w+)' |
        ForEach-Object {
            '{0} resting={1} hash={2}' -f $_.Matches[0].Groups[1].Value,
                $_.Matches[0].Groups[2].Value, $_.Matches[0].Groups[3].Value
        }
}

function Get-Hashes($paths, $pattern) {
    @(Select-String -Path $paths -Pattern $pattern |
        ForEach-Object { $_.Matches[0].Groups[1].Value })
}

$script:failed = $false
function Check($name, $ok) {
    if (-not $ok) { $script:failed = $true }
    '{0,-56} {1}' -f $name, $(if ($ok) { 'PASS' } else { 'FAIL' })
}

$n0 = @(Get-Applied docs\evidence\node0.log)
$n1 = @(Get-Applied docs\evidence\node1.log)
$n2 = @(Get-Applied docs\evidence\node2.log)
$r2 = @(Get-Applied docs\evidence\node2-restart.log)

$nodeLogs = @('docs\evidence\node0.log', 'docs\evidence\node1.log', 'docs\evidence\node2.log')
$written = @(Get-Hashes $nodeLogs 'snapshot written: \d+ resting orders, hash=(\w+)')
$loaded  = @(Get-Hashes @('docs\evidence\node2-restart.log') 'snapshot loaded: \d+ resting orders, hash=(\w+)')

$sameAsLoaded = $false
if ($loaded.Count -eq 1 -and $written.Count -ge 1) {
    $differing = @($written | Where-Object { $_ -ne $loaded[0] })
    $sameAsLoaded = ($differing.Count -eq 0)
}

Check 'node 0 applied 9 entries' ($n0.Count -eq 9)
Check 'node 1 identical to node 0' ($n1.Count -eq $n0.Count -and -not (Compare-Object $n0 $n1))
Check 'node 2 identical to node 0' ($n2.Count -eq $n0.Count -and -not (Compare-Object $n0 $n2))
Check 'all three nodes wrote a snapshot' ($written.Count -eq 3)
Check 'all snapshots have the same hash' ($written.Count -eq 3 -and @($written | Select-Object -Unique).Count -eq 1)
Check 'snapshot hash equals the book after entry 6' ($written.Count -ge 1 -and $n0.Count -ge 6 -and $written[0] -eq ($n0[5] -split 'hash=')[1])
Check 'restarted node loaded a snapshot' ($loaded.Count -eq 1)
Check 'loaded hash equals written hash' $sameAsLoaded
Check 'restarted node applied only the 3 later entries' ($r2.Count -eq 3)
Check 'those 3 entries match the last 3 on node 0' ($r2.Count -eq 3 -and $n0.Count -ge 9 -and -not (Compare-Object $r2 $n0[6..8]))

if (-not $sameAsLoaded) {
    ''
    "  written: $written"
    "  loaded : $loaded"
}

''
'Role changes:'
Select-String -Path docs\evidence\node*.log -Pattern 'role is now' |
    ForEach-Object { '  ' + $_.Filename + ': ' + $_.Line }

if ($script:failed) { exit 1 }