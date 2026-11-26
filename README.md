# Luciole

Petit jeu Android de labyrinthe : guidez une luciole dans un jardin de haies,
ramassez tout le pollen et évitez les araignées. Les fleurs de nectar rendent
la luciole flamboyante pendant quelques secondes : les araignées prennent peur
et peuvent être renvoyées au nid.

## Commandes

- **Glisser** le doigt : changer de direction
- **Toucher** l'écran : pause / reprise, ou rejouer après une partie terminée

## Compiler

Ouvrir le dossier dans Android Studio, ou en ligne de commande :

```
./gradlew assembleDebug        # APK dans app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # tests du moteur de jeu
```

Chaque push déclenche aussi le workflow GitHub Actions « Android build », qui
publie l'APK de debug en artefact (`luciole-debug-apk`).

## Organisation

- `app/src/main/java/.../engine/` : règles du jeu en Kotlin pur (labyrinthe,
  déplacements, araignées), sans dépendance Android
- `GameView.kt` : rendu Canvas et gestes tactiles
- `MainActivity.kt` : activité plein écran
