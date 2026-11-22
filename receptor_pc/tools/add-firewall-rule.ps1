<#
.SYNOPSIS
    Agrega las reglas de Firewall de Windows para MSE Receptor PC (TCP 47800 y UDP 47801) en redes privadas.
.NOTES
    Ejecutar en PowerShell como Administrador.
#>

$ErrorActionPreference = "Stop"

$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Error "Este script requiere permisos de Administrador. Abre PowerShell como Administrador y vuelve a ejecutarlo."
    exit 1
}

$tcpRuleName = "MSE Receptor PC (TCP 47800)"
$udpRuleName = "MSE Receptor PC Discovery (UDP 47801)"

Remove-NetFirewallRule -DisplayName $tcpRuleName -ErrorAction SilentlyContinue
Remove-NetFirewallRule -DisplayName $udpRuleName -ErrorAction SilentlyContinue

New-NetFirewallRule -DisplayName $tcpRuleName `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort 47800 `
    -Profile Private | Out-Null

New-NetFirewallRule -DisplayName $udpRuleName `
    -Direction Inbound `
    -Action Allow `
    -Protocol UDP `
    -LocalPort 47801 `
    -Profile Private | Out-Null

Write-Host "Reglas de firewall creadas correctamente para redes privadas (TCP 47800 / UDP 47801)." -ForegroundColor Green
