# 삼성 갤럭시 (S10 ~ S25) 4종 독립 테스트 앱 통합 프로젝트

이 압축 파일은 사용자가 직접 코드나 매니페스트, Gradle 설정을 건드릴 필요 없이 **열자마자 바로 4개의 독립된 APK를 빌드하고 갤럭시 기기에 일괄 설치**할 수 있도록 완벽히 세팅된 멀티 모듈 프로젝트입니다.

---

## 📦 포함된 4개 독립 앱 모듈

1. **`:app1_shortnews` (`com.test.app1.shortnews`)**
   - 앱 이름: `테스트1 단문알림(999)`
   - 기능: 48시간 동안 약 173초 간격으로 999개의 단문 뉴스 **무음 알림** 발송 (실행 즉시 권한 요청)
2. **`:app2_longnews` (`com.test.app2.longnews`)**
   - 앱 이름: `테스트2 장문알림(999)`
   - 기능: 48시간 동안 약 173초 간격으로 999개의 1,000자 이상 장문(`BigTextStyle`) **무음 알림** 발송
3. **`:app3_photosaver` (`com.test.app3.photosaver`)**
   - 앱 이름: `테스트3 사진저장알림`
   - 기능: 랜덤 해상도(400x400 ~ 2400x2400) JPEG 이미지를 생성하여 갤러리(`Pictures/TestApp3_RandomPhotos`)에 자동 저장 후 **무음 사진 알림** 발송 (기기 전체 용량 점유율 65% 도달 시 저장 자동 보호)
4. **`:app4_cachefiller` (`com.test.app4.cachefiller`)**
   - 앱 이름: `테스트4 랜덤캐시점유`
   - 기능: 48시간 동안 1MB~64MB 사이의 랜덤 크기 캐시 파일을 분할 생성하되, **앱3(사진) 및 시스템 용량과 합쳐서 기기 전체 사용률이 절대 65%(최소 여유 공간 35% 보장)를 넘지 않도록 동적 제어**

---

## 🚀 가장 빠르게 APK 4개를 생성하고 폰에 설치하는 2가지 방법

### 방법 A: Android Studio에서 열어서 버튼 한 번으로 APK 4개 뽑기
1. 압축을 푼 `SamsungGalaxyTestSuite_4Apps` 폴더를 Android Studio에서 **Open** 합니다.
2. 상단 메뉴에서 **Build > Build Bundle(s) / APK(s) > Build APK(s)** 를 클릭하면 4개 앱의 APK가 한 번에 생성됩니다:
   - `app1_shortnews/build/outputs/apk/debug/app1_shortnews-debug.apk`
   - `app2_longnews/build/outputs/apk/debug/app2_longnews-debug.apk`
   - `app3_photosaver/build/outputs/apk/debug/app3_photosaver-debug.apk`
   - `app4_cachefiller/build/outputs/apk/debug/app4_cachefiller-debug.apk`
3. 갤럭시 폰을 PC에 연결하고 폴더 안의 **`install_all_to_phone.bat`**(윈도우) 또는 **`install_all_to_phone.sh`**(맥/리눅스)를 더블클릭하면:
   - '출처를 알 수 없는 앱' 경고 없이 4개 앱이 동시에 설치되고,
   - 알림/저장소 권한과 배터리 최적화 제외까지 자동으로 부여됩니다.

### 방법 B: 내 컴퓨터에 Android Studio조차 설치하기 싫을 때 (GitHub 무료 클라우드 자동 빌드)
1. 본 폴더에 `.github/workflows/build_apks.yml` 자동 빌드 파일이 이미 세팅되어 있습니다.
2. GitHub에 비공개(Private) 저장소를 하나 만들고 이 폴더의 파일들을 그대로 드래그해서 올리면, **GitHub Actions** 탭에서 클라우드 서버가 자동으로 4개 APK를 빌드해 `Samsung_Test_4_APKs.zip`으로 만들어 줍니다.
