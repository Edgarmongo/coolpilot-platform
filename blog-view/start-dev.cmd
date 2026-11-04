@echo off
REM ============================================================
REM  NBlog blog-view 前端一键启动脚本 (Windows)
REM  1) 清除本机失效的 OPENSSL_CONF 环境变量
REM     (它指向不存在的 psqlODBC openssl.cnf，会让 npm 直接报错)
REM  2) Node 17+ 需要 --openssl-legacy-provider，
REM     否则 webpack 4 无法使用 md4 哈希 (ERR_OSSL_EVP_UNSUPPORTED)
REM  3) 启动开发服务器 (默认 http://localhost:8080/)
REM ============================================================
set "OPENSSL_CONF="
set "NODE_OPTIONS=--openssl-legacy-provider"
call npm run serve