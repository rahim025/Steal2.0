# Steal (PWA) — assistant vocal léger

Version web installable de Steal, sans les fonctions à risque de la version Android
(pas de service d'accessibilité, pas de déverrouillage automatique de l'écran, pas
d'automatisation d'autres applications). Elle tourne entièrement dans le navigateur.

## Fonctionnalités

- Reconnaissance vocale (Web Speech API) + repli clavier si le micro n'est pas supporté
- Heure et date parlées
- Minuteur avec notification et vibration
- Recherche web (ouvre un nouvel onglet Google)
- Ouverture de sites courants (Google, YouTube, Wikipédia, Gmail)
- Prise de notes vocales, stockées uniquement sur l'appareil (`localStorage`)
- Fonctionne hors-ligne une fois chargée une première fois (service worker)
- Installable sur l'écran d'accueil (Android, desktop Chrome/Edge)

## Limite importante : compatibilité navigateur

La reconnaissance vocale (Web Speech API) fonctionne bien sur Chrome/Edge (Android et
desktop), mais **Safari sur iPhone ne la supporte pas**. Sur iPhone, l'utilisateur devra
taper ses commandes dans le champ texte prévu à cet effet — tout le reste (minuteur,
notes, recherche) fonctionne normalement.

## Déployer (aucun store, aucune review)

N'importe quel hébergeur de site statique fonctionne. Trois options gratuites :

### Option A — Netlify (le plus simple)
1. Va sur https://app.netlify.com/drop
2. Glisse-dépose ce dossier entier
3. Netlify te donne une URL en HTTPS immédiatement

### Option B — GitHub Pages
1. Mets ces fichiers à la racine d'un repo GitHub (ou dans un dossier `/docs`)
2. Repo → Settings → Pages → choisis la branche et le dossier
3. L'URL est `https://<utilisateur>.github.io/<repo>/`

### Option C — Render (Static Site)
1. Crée un nouveau service → "Static Site"
2. Connecte le repo contenant ces fichiers
3. Build command : (laisser vide, ce sont des fichiers statiques)
4. Publish directory : `.` (la racine du dossier)

## Installation par l'utilisateur

Une fois l'URL ouverte dans Chrome (Android) ou Safari (iPhone) :
- Android : menu ⋮ → "Ajouter à l'écran d'accueil" (ou bandeau d'installation automatique)
- iPhone : bouton Partager → "Sur l'écran d'accueil"

Aucune installation depuis un store, aucun compte développeur, aucune review requise.
