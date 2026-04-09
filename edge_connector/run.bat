@echo off
REM QuantixMed Edge Connector launcher
REM ------------------------------------
REM Requires: JRE 21+ on PATH
REM The jar self-boots an embedded Spring Boot REST server on :9090
REM and opens a Swing window.  Minimise the window to keep the
REM service running in the background.

java -jar edge-connector.jar --ui
