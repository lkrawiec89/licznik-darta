# Instrukcja publikacji: GitHub, IzzyOnDroid i F-Droid

Dokument zawiera kompletny przewodnik krok po kroku, jak opublikować aplikację **Licznik Darta** w repozytorium GitHub, udostępnić wydania z automatycznym mechanizmem OTA oraz dodać projekt do katalogu **IzzyOnDroid** i **F-Droid**.

---

## 1. Struktura repozytorium przygotowana w projekcie

Projekt posiada już wszystkie wymagane pliki i katalogi:
* `LICENSE` – licencja MIT Open Source
* `.gitignore` – wykluczenia plików tymczasowych, buildów i APK
* `fastlane/metadata/android/` – metadane w języku polskim (`pl-PL`) oraz angielskim (`en-US`):
  * `title.txt`
  * `short_description.txt`
  * `full_description.txt`
  * `icon.png` (512×512 px)
  * `changelogs/33.txt`
* `.github/workflows/release.yml` – automatyczny proces CI/CD budujący plik APK po utworzeniu taga (np. `v1.1.23`)
* `app/src/main/java/pl/licznikdarta/UpdateManager.kt` – wbudowany mechanizm OTA z obsługą GitHub Releases

---

## 2. Krok 1: Wypchnięcie repozytorium na GitHub

1. Zaloguj się na swoje konto [GitHub](https://github.com) i utwórz nowe publiczne repozytorium, np. `licznik-darta`.
2. W terminalu na serwerze zainicjalizuj git i wypchnij kod:
   ```bash
   cd /opt/t3code/projects/licznik-darta
   git init
   git add .
   git commit -m "feat: przygotowanie projektu pod IzzyOnDroid/F-Droid i wdrożenie OTA"
   git branch -M main
   git remote add origin https://github.com/<TWOJA_NAZWA>/licznik-darta.git
   git push -u origin main
   ```

---

## 3. Krok 2: Publikacja pierwszego wydania (GitHub Release)

IzzyOnDroid wymaga, aby w repozytorium znajdowało się co najmniej jedno oficjalne wydanie z załączonym podpisanym plikiem APK.

1. Zbuduj produkcyjny plik APK:
   ```bash
   ./gradlew assembleRelease -x lintVitalRelease -x lint
   ```
   Wygenerowany plik znajduje się w:
   `app/build/outputs/apk/release/app-release.apk`
2. Zmień jego nazwę na przyjazną:
   ```bash
   cp app/build/outputs/apk/release/app-release.apk LicznikDarta-1.1.22.apk
   ```
3. Na GitHubie wejdź w zakładkę **Releases** -> **Draft a new release**:
   * **Tag:** `v1.1.22`
   * **Release title:** `Licznik Darta v1.1.22`
   * **Opis:**
     ```markdown
     Wydanie 1.1.22 (build 33)
     - Wbudowany mechanizm aktualizacji OTA (Self-updater)
     - Optymalizacja ścieżek checkoutów (faworyzowanie podwójnych D8, D16, D20)
     - Obsługa przycisku wstecz z oknem dialogowym wyboru akcji
     ```
   * **Załącznik (Binary asset):** Przeciągnij i upuść plik `LicznikDarta-1.1.22.apk`.
   * Kliknij **Publish release**.

---

## 4. Krok 3: Zgłoszenie do IzzyOnDroid (Najszybsza ścieżka do F-Droid)

IzzyOnDroid to najpopularniejsze niezależne repozytorium F-Droid dla aplikacji bezpośrednio z GitHub Releases.

1. Wejdź na GitLab IzzyOnDroid:
   👉 **https://gitlab.com/IzzyOnDroid/repo/-/issues**
2. Kliknij **New issue**.
3. Jako szablon wybierz: **Application Inclusion Request**.
4. Wypełnij prosty formularz:
   * **Repository URL:** `https://github.com/<TWOJA_NAZWA>/licznik-darta`
   * **Application ID:** `pl.licznikdarta`
   * **License:** `MIT`
   * **Auto-Update Method:** `GitHub Releases`
5. Wyślij zgłoszenie.

### Co dzieje się dalej?
* Opiekun IzzyOnDroid uruchamia bota weryfikacyjnego.
* Bot sprawdza czystość zależności (brak śledzenia, brak zamkniętych binariów – nasz projekt spełnia to w 100%).
* Aplikacja zostaje dodana do repozytorium.
* Użytkownicy korzystający z klienta F-Droid (z włączonym repozytorium IzzyOnDroid) mogą instalować i aktualizować Licznik Darta bezpośrednio przez F-Droid!
* Każde kolejne wydanie (nowy release z tagiem i APK na GitHubie) zostanie automatycznie pobrane przez IzzyOnDroid bez konieczności ponownego zgłaszania.

---

## 5. Krok 4: Weryfikacja działania wbudowanego OTA w aplikacji

Dla użytkowników, którzy zainstalują plik `.apk` bezpośrednio (z GitHuba lub od znajomego):
1. W aplikacji wchodzimy w **Ustawienia** -> **Aktualizacje aplikacji (OTA)**.
2. Po kliknięciu **Sprawdź aktualizacje** aplikacja odpytuje GitHub API.
3. Gdy pojawi się nowsza wersja:
   * Wyświetli się okno z numerem wersji i listą zmian z GitHuba.
   * Użytkownik klika **Pobierz i zainstaluj**.
   * Pasek postępu pokazuje pobieranie.
   * Po zakończeniu natychmiast uruchamia się instalator systemowy.
