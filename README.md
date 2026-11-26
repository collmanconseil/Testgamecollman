# Mini Arcade

Application Android regroupant deux petits jeux originaux, avec un menu d'accueil.

## Luciole (labyrinthe)

Guidez une luciole dans un jardin de haies, ramassez tout le pollen et évitez
les araignées. Les fleurs de nectar effraient les araignées quelques secondes :
on peut alors les renvoyer au nid.

- **Glisser** le doigt : changer de direction
- **Toucher** l'écran : pause / reprise, ou rejouer

## Bip et les rouages (plateforme rétro)

Jeu de plateforme 2D dans le style des micro-ordinateurs 8 bits. Le robot Bip
traverse trois niveaux, récupère les rouages, saute sur les robots ennemis et
rejoint la porte verte. Les pointes et le vide sont mortels.

- Boutons **<** et **>** en bas à gauche : courir
- Moitié droite de l'écran : sauter (appui long = saut plus haut)
- **II** en haut au centre : pause

Musique et bruitages sont synthétisés par le programme (ondes carrées), comme
sur les machines de l'époque. Le volume se règle avec les touches du téléphone.

## Télécharger

Chaque push déclenche le workflow GitHub Actions « Android build », qui publie
l'APK en téléchargement direct :
https://github.com/collmanconseil/Testgamecollman/releases/latest/download/luciole.apk

## Compiler

```
./gradlew assembleDebug        # APK dans app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # tests des moteurs de jeu
```

## Organisation

- `engine/` : règles de Luciole en Kotlin pur
- `platformer/` : niveaux, physique et règles de Bip (Kotlin pur), plus sa vue
- `GameView.kt`, `PlatformView.kt` : rendu Canvas et commandes tactiles
- `MainActivity.kt` : menu d'accueil
