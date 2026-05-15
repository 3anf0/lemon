# Konfiguracja Firebase dla projektu Lemon

## 1. Utwórz projekt Firebase

1. Wejdź na https://console.firebase.google.com
2. Kliknij **"Dodaj projekt"** → nadaj nazwę (np. `lemon-app`)
3. Wyłącz Google Analytics (opcjonalne) → **"Utwórz projekt"**

---

## 2. Dodaj aplikację Android

1. W konsoli Firebase kliknij ikonę **Android** (Dodaj aplikację)
2. Podaj **Package name:** `com.smartcal.app`
3. Pobierz plik **`google-services.json`**
4. Skopiuj go do folderu: `app/google-services.json`

> ⚠️ Bez tego pliku projekt nie skompiluje się!

---

## 3. Włącz Email/Password Authentication

1. W konsoli Firebase → **Authentication** → **Sign-in method**
2. Kliknij **Email/hasło** → włącz → **Zapisz**

---

## 4. Utwórz bazę Firestore

1. W konsoli Firebase → **Firestore Database** → **Utwórz bazę danych**
2. Wybierz **Production mode** (lub Test mode na czas developmentu)
3. Wybierz region (np. `europe-west1`)

### Reguły Firestore (wklej w Reguły):
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```
Ta reguła zapewnia, że każdy użytkownik ma dostęp **tylko do swoich danych**.

---

## 5. Skonfiguruj stronę internetową (webapp.html)

Otwórz `strona internetowa/webapp.html` i znajdź sekcję:

```javascript
const firebaseConfig = {
  apiKey:            "TWOJ_API_KEY",
  authDomain:        "TWOJ_PROJECT_ID.firebaseapp.com",
  projectId:         "TWOJ_PROJECT_ID",
  storageBucket:     "TWOJ_PROJECT_ID.appspot.com",
  messagingSenderId: "TWOJ_SENDER_ID",
  appId:             "TWOJ_APP_ID"
};
```

Dane znajdziesz w konsoli Firebase:
- Kliknij ikonę ⚙️ (Ustawienia projektu) → **Twoje aplikacje**
- Dodaj aplikację **Web** (ikona `</>`)
- Skopiuj obiekt `firebaseConfig` i wklej go do `webapp.html`

---

## 6. Zbuduj i uruchom aplikację Android

```bash
./gradlew assembleDebug
```

Lub otwórz projekt w **Android Studio** i kliknij **Run**.

---

## Struktura danych w Firestore

```
users/
  {uid}/
    events/
      {uid}_{localId}/
        title: String
        startEpoch: Long (ms)
        endEpoch: Long (ms)
        category: String (PERSONAL|WORK|HEALTH|HABIT|SOCIAL)
        isAiSuggested: Boolean
        notes: String
    transactions/
      {uid}_{localId}/
        title: String
        amount: Double
        type: String (INCOME|EXPENSE)
        category: String (FOOD|FUEL|WORK|GAMBLING|OTHER)
        dateEpoch: Long (ms)
        note: String
```

---

## Synchronizacja — jak działa

| Akcja | Aplikacja | Strona |
|-------|-----------|--------|
| Dodaj wydarzenie | Room (natychmiast) + Firestore (async) | Firestore (natychmiast) |
| Usuń wydarzenie | Room + Firestore | Firestore |
| Zaloguj się | Sync z Firestore → Room | Odczyt z Firestore |
| Wyloguj | Czyści Room lokalnie | — |

Dane są **natychmiast dostępne offline** w apce (Room cache).
Po zalogowaniu na nowym urządzeniu dane pobierają się z Firestore.
