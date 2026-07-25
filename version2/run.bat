@echo off
chcp 65001 >nul
echo ========================================
echo   BMI 体质评估与预测系统 v2.0 (JavaFX 重制版)
echo ========================================
echo.

set JDK_PATH=C:\Users\huang\Desktop\jdk\bin
REM 若写死路径不存在，自动回退 JAVA_HOME，再回退 PATH 中的 javac
if not exist "%JDK_PATH%\javac.exe" (
    if defined JAVA_HOME set "JDK_PATH=%JAVA_HOME%\bin"
)
if not exist "%JDK_PATH%\javac.exe" (
    for /f "delims=" %%i in ('where javac 2^>nul') do (
        if not defined JDK_DETECTED (
            set "JDK_PATH=%%~dpi"
            set "JDK_DETECTED=1"
        )
    )
)
set LIB=lib
set SRC=src
set OUT=out

if not exist "%OUT%" mkdir "%OUT%"

REM 模块路径：自动收集 lib 下全部 jar（仅取 Windows 版 JavaFX，避免与 linux 版冲突；并包含 POI 用于 Excel 导入）
set MODPATH=
for %%f in (%LIB%\javafx-*-win.jar %LIB%\postgresql-42.7.3.jar %LIB%\webcam-capture-0.3.12.jar %LIB%\bridj-0.7.0.jar %LIB%\slf4j-api-1.7.36.jar %LIB%\poi-*.jar %LIB%\commons-*.jar %LIB%\xmlbeans-*.jar %LIB%\com.zaxxer.sparsebitset-*.jar %LIB%\log4j-api-*.jar) do call set "MODPATH=%%MODPATH%%;%%f"
set MODPATH=%MODPATH:~1%

echo [1/3] 编译 Java 源文件（模块化）...
"%JDK_PATH%\javac" --module-path "%MODPATH%" -d "%OUT%" ^
  %SRC%\module-info.java ^
  %SRC%\com\bmi\App.java ^
  %SRC%\com\bmi\util\*.java ^
  %SRC%\com\bmi\db\*.java ^
  %SRC%\com\bmi\ui\*.java ^
  %SRC%\com\bmi\ui\user\*.java ^
  %SRC%\com\bmi\ui\admin\*.java

if %errorlevel% neq 0 (
    echo [错误] 编译失败！请检查 JDK 路径是否正确。
    pause
    exit /b 1
)
echo [2/3] 编译成功！

echo [3/3] 复制样式表...
copy /Y "%SRC%\style.css" "%OUT%\style.css" >nul

echo 启动系统...
"%JDK_PATH%\java" -Dprism.verbose=true -Dsun.java2d.d3d=true --module-path "%MODPATH%;%OUT%" -m com.bmi.app/com.bmi.App

pause
