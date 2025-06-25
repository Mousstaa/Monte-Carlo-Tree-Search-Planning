@echo off
setlocal enabledelayedexpansion

:: Path setup
set CLASS_DIR=classes
set LIB_JAR=lib\pddl4j-4.0.0.jar
set MAIN_CLASS=fr.uga.pddl4j.examples.asp.ASP
set PDDL_DIR=pddlproblems
set LOG_FILE=results.log

:: Clear previous log
if exist %LOG_FILE% del %LOG_FILE%

echo Running ASP Planner on all domain-problem sets...
echo ----------------------------------------------- >> %LOG_FILE%

:: Loop through all domain files (*.pddl that do not start with 'p')
for %%D in (%PDDL_DIR%\*.pddl) do (
    set "DOMAIN=%%~nxD"
    if /i not "!DOMAIN:~0,1!"=="p" (
        echo Processing domain: !DOMAIN!
        echo DOMAIN: !DOMAIN! >> %LOG_FILE%

        :: Get base name without .pddl
        set "BASE=!DOMAIN:.pddl=!"

        :: Loop through 3 problem files
        for %%i in (1 2 3) do (
            set "PROBLEM=p!BASE!%%i.pddl"
            set "PROBLEM_PATH=%PDDL_DIR%\!PROBLEM!"

            if exist "!PROBLEM_PATH!" (
                echo   Problem: !PROBLEM!
                echo   Problem: !PROBLEM! >> %LOG_FILE%
                echo ------------------------ >> %LOG_FILE%
                java -cp "%CLASS_DIR%;%LIB_JAR%" %MAIN_CLASS% "%PDDL_DIR%\!DOMAIN!" "!PROBLEM_PATH!" >> %LOG_FILE% 2>&1
                echo ------------------------ >> %LOG_FILE%
            ) else (
                echo   Problem file not found: !PROBLEM_PATH!
                echo   Missing: !PROBLEM! >> %LOG_FILE%
            )
        )
        echo. >> %LOG_FILE%
    )
)

echo Done. See results in %LOG_FILE%
pause
