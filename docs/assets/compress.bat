@echo off
setlocal enabledelayedexpansion

for /r %%i in (*.png) do (
    echo Обработка PNG: "%%i"
    ffmpeg -y -i "%%i" -c:v libwebp -quality 80 "%%~dpni.webp" > nul 2>&1
    if exist "%%~dpni.webp" (
        del "%%i"
    )
)

for /r %%i in (*.webp) do (
    set "filename=%%~nxi"
    if "!filename:~0,5!" NEQ "temp_" (
        echo WEBP Optimization: "%%i"
        ffmpeg -y -i "%%i" -c:v libwebp -quality 80 "%%~dptemp_%%~nxi" > nul 2>&1
        if exist "%%~dptemp_%%~nxi" (
            del "%%i"
            move "%%~dptemp_%%~nxi" "%%i" > nul
        )
    )
)

pause
