# Mini Arcade

Application Android regroupant deux petits jeux originaux, avec un menu d'accueil.

## Luciole (labyrinthe)

Guidez une luciole dans un jardin de haies, ramassez tout le pollen et évitez
les araignées. Les fleurs de nectar effraient les araignées quelques secondes :
on peut alors les renvoyer au nid.

- **Glisser** le doigt : changer de direction
- **Toucher** l'écran : pause / reprise, ou rejouer

## Max l'explorateur (plateforme rétro)

Jeu de plateforme 2D dans le style des micro-ordinateurs 8 bits. Max traverse
la jungle, une grotte puis un temple : il ramasse les trésors, saute sur les
serpents et les chauves-souris et rejoint la porte dorée. Les pieux et le vide
sont mortels.

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
- `platformer/` : niveaux, physique et règles de Max (Kotlin pur), plus sa vue
- `GameView.kt`, `PlatformView.kt` : rendu Canvas et commandes tactiles
- `MainActivity.kt` : menu d'accueil
