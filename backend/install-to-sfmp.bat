@echo off
REM 安装 AI SDK 到 SFMP 使用的 Maven 本地仓 + file 仓（与 IDEA 一致）
REM 随后强制 Maven 编译 SFMP 的 mq 模块：IDEA Debug 用的是 target/classes，
REM 改 AI jar 后 IDEA 增量编译常把 AbstractRedisStreamMessageListener.class 弄丢。
set JAVA_HOME=D:\workFile\environment\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%
set LOCAL_REPO=D:\workFile\environment\.m2\repository
set SETTINGS=D:\workFile\environment\.m2\settings.xml
set AI_FILE_REPO=file:///d:/workFile/project/yzzh-ai-assistant/backend/.local-m2
set SFMP_ADMIN=D:\workFile\project\yzzh-sfmp\yzzh-sfmp-admin

cd /d "%~dp0"
echo JAVA_HOME=%JAVA_HOME%
echo LOCAL_REPO=%LOCAL_REPO%
echo SETTINGS=%SETTINGS%

call mvn -U clean deploy -DskipTests ^
  -DaltDeploymentRepository=yzzh-ai-local::default::%AI_FILE_REPO% ^
  -Dmaven.repo.local=%LOCAL_REPO% ^
  -s %SETTINGS%

if errorlevel 1 (
  echo FAILED: AI SDK deploy
  exit /b 1
)
echo OK: AI SDK installed to %LOCAL_REPO% and %AI_FILE_REPO%

if not exist "%SFMP_ADMIN%\pom.xml" (
  echo WARN: SFMP admin not found at %SFMP_ADMIN%, skip mq recompile
  exit /b 0
)

echo --- Recompile SFMP ysmp-server + deps (fix IDEA stub/bad classes) ---
cd /d "%SFMP_ADMIN%"
call mvn -pl ysmp-server -am clean compile -DskipTests ^
  -Dmaven.repo.local=%LOCAL_REPO% ^
  -s %SETTINGS%

if errorlevel 1 (
  echo FAILED: SFMP compile
  exit /b 1
)
echo OK: Maven classes ready. Now Debug YsmpServerApplication ^(do NOT Rebuild / Make^).
