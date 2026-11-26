# eTerra Check

Aplicație Android 9+ pentru urmărirea documentațiilor din capturi eTerra.

1. Alege **Importă captură** sau distribuie imaginea din galerie către **eTerra Check**.
2. Folosește o captură clară, cu antetul tabelului și coloanele Status, Client, Nr. cerere, UAT, Termen cerere și Flux vizibile. Verifică rândurile citite și salvează importul.
3. Importă următoarele capturi în ordine cronologică. În ecranul de verificare vezi diferențele față de ultima captură salvată. În **Schimbări** apar starea anterioară, starea nouă și data importului.

Aplicația potrivește documentațiile după numărul cererii. Poți completa numărul în ecranul de verificare. Beneficiarul poate lipsi. Rândurile care nu apar într-o captură își păstrează ultima stare cunoscută. Pentru a urmări și soluționările, include în captură documentațiile finalizate din eTerra.

Citirea imaginii și salvarea documentațiilor au loc pe telefon. Fiecare import folosește captura aleasă de tine.

## Compilare

Proiectul folosește JDK 17, Gradle 8.9 și Android SDK 35. Rulează `gradle assembleDebug lintDebug`. APK-ul apare la `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions publică APK-ul de test ca artefact.
