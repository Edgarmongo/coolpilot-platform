@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  blog-cms 启动脚本 (Windows)
REM  - 自动检查 Node.js 环境
REM  - 自动安装依赖 (node_modules 不存在时)
REM  - 开发运行 / 生产构建 两种模式
REM ============================================================

title blog-cms 启动脚本

REM 切换到脚本所在目录
cd /d "%~dp0"

REM 清除可能指向失效路径的 OpenSSL 环境变量，避免 Node.js 启动时报 OpenSSL configuration error
set OPENSSL_CONF=
set OPENSSL_CONF_INCLUDE=
set OPENSSL_MODULES=

echo ============================================
echo         blog-cms 启动脚本
echo ============================================
echo.

REM ---------- 1. 检查 Node.js 环境 ----------
where node >nul 2>nul
if errorlevel 1 (
    echo [错误] 未检测到 Node.js，请先安装 Node.js (推荐 v14 及以上)
    echo        下载地址: https://nodejs.org/
    pause
    exit /b 1
)

for /f "delims=" %%v in ('node -v') do set NODE_VER=%%v
for /f "delims=" %%v in ('npm -v') do set NPM_VER=%%v
echo [信息] Node 版本: %NODE_VER%   npm 版本: %NPM_VER%
echo.

REM ---------- 2. 检查并安装依赖 ----------
if not exist "node_modules" (
    echo [信息] 未检测到 node_modules，开始安装依赖...
    echo.
    call npm install
    if errorlevel 1 (
        echo.
        echo [错误] 依赖安装失败，请检查网络后重试。
        pause
        exit /b 1
    )
    echo.
    echo [信息] 依赖安装完成。
) else (
    echo [信息] 依赖已存在，跳过安装。
)
echo.

REM ---------- 3. 选择启动模式 ----------
echo 请选择启动模式:
echo   [1] 开发运行 (npm run serve)   端口: 8079，自动打开浏览器
echo   [2] 生产构建 (npm run build)   输出到 dist 目录
echo   [0] 退出
echo.
set /p choice=请输入选项 (1/2/0):

if "%choice%"=="1" goto serve
if "%choice%"=="2" goto build
if "%choice%"=="0" goto end

echo [错误] 无效的选项: %choice%
pause
exit /b 1

:serve
echo.
echo [信息] 正在启动开发服务器...
call npm run serve
goto end

:build
echo.
echo [信息] 正在进行生产构建...
call npm run build
if errorlevel 1 (
    echo.
    echo [错误] 构建失败。
    pause
    exit /b 1
)
echo.
echo [信息] 构建完成，产物位于 dist 目录。
goto end

:end
echo.
echo ============================================
echo              脚本执行结束
echo ============================================
pause
endlocal
