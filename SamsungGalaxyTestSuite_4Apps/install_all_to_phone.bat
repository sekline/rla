@echo off
chcp 65001 > nul
echo ============================================================================
echo [삼성 갤럭시 S10 ~ S25] 4개 테스트 앱 일괄 설치 및 권한 자동 부여 스크립트
echo ============================================================================
echo.
echo 1. 연결된 기기의 Play 프로텍트 차단 경고 비활성화 및 알림 개수 제한(999개) 해제 중...
adb shell settings put global package_verifier_user_consent -1
adb shell settings put global max_notification_per_app 1000

echo.
echo 2. 4개 APK 일괄 설치 및 모든 런타임 권한(-g) 자동 부여 중...
adb install -r -g app1_shortnews/build/outputs/apk/debug/app1_shortnews-debug.apk
adb install -r -g app2_longnews/build/outputs/apk/debug/app2_longnews-debug.apk
adb install -r -g app3_photosaver/build/outputs/apk/debug/app3_photosaver-debug.apk
adb install -r -g app4_cachefiller/build/outputs/apk/debug/app4_cachefiller-debug.apk

echo.
echo 3. 삼성 배터리 최적화 제외(Doze 화이트리스트) 자동 등록 중...
adb shell dumpsys deviceidle whitelist +com.test.app1.shortnews
adb shell dumpsys deviceidle whitelist +com.test.app2.longnews
adb shell dumpsys deviceidle whitelist +com.test.app3.photosaver
adb shell dumpsys deviceidle whitelist +com.test.app4.cachefiller

echo.
echo [완료] 4개 테스트 앱이 모든 권한 허용 상태로 설치되었습니다!
pause
