$ErrorActionPreference = "Stop"
$env:MAVEN_HOME = "C:\Program Files\apache-maven-3.9.14-bin\apache-maven-3.9.14"
$env:PATH = "$env:MAVEN_HOME\bin;$env:PATH"
Set-Location (Join-Path $PSScriptRoot "..")
mvn -v
mvn spring-boot:run
