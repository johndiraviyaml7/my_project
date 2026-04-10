@echo off
REM QuantixMed Edge Connector — DEBUG launcher with JSSE handshake logging
REM -----------------------------------------------------------------------
REM Prints every byte of the SSL handshake to stdout.  Use only when
REM diagnosing TLS handshake failures — the output is very verbose.
REM Redirect to a file for easier inspection:
REM     run-debug.bat > edge-ssl-debug.log 2>&1

java -Djavax.net.debug=ssl:handshake:verbose ^
     -jar target\edge-connector.jar --ui
