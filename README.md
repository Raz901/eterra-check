# eTerra Check

Aplicație Android 9+ pentru urmărirea documentațiilor din capturi eTerra.

1. Alege **Importă captură** sau distribuie imaginea din galerie către **eTerra Check**.
2. Folosește o captură clară, cu antetul tabelului și coloanele Status, Client, Cod înregistrare și Nr. cerere vizibile. Verifică rândurile citite și salvează importul.
3. Importă următoarele capturi în ordine cronologică. În **Schimbări** apar starea anterioară, starea nouă și data importului.

Aplicația potrivește documentațiile după codul de înregistrare. Numărul cererii și beneficiarul pot lipsi. Rândurile care nu apar într-o captură își păstrează ultima stare cunoscută. Pentru a urmări și soluționările, include în captură documentațiile finalizate din eTerra.

Citirea imaginii și salvarea documentațiilor au loc pe telefon. Aplicația nu preia stările automat din portal.

## Compilare

Proiectul folosește JDK 17, Gradle 8.9 și Android SDK 35. Rulează `gradle assembleDebug lintDebug`. APK-ul apare la `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions publică APK-ul de test ca artefact.
