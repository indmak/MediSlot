# 启动后端（Windows PowerShell）
# 用法：.\scripts\start-backend.ps1 [-Profile dev]
param(
    [string]$Profile = "dev"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
& "$root\apps\backend\mvnw.cmd" -f "$root\apps\backend\pom.xml" spring-boot:run "-Dspring-boot.run.profiles=$Profile"
