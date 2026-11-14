# MySecretSanta

Une app Android gratuite pour organiser un Secret Santa.

Enregistre les participants, réunis-les en groupes (famille, collègues…), et laisse l'app tirer au sort qui offre un cadeau à qui. Chaque participant reçoit ensuite un SMS lui indiquant à qui il doit faire un cadeau, sans que l'organisateur voie le résultat. Les participants n'ont rien à installer. Toutes les données restent sur ton téléphone, sans compte ni connexion Internet.

---

## Télécharger et installer

> **Note :** l'app n'est pas sur le Play Store. Tu l'installes directement à partir d'un fichier : c'est ce qu'on appelle le *sideloading*. Ça prend 2 minutes.

### Étape 1 — Télécharger l'APK

Télécharge [MySecretSanta.apk](https://github.com/ChezMose/MySecretSanta/releases/download/dev/MySecretSanta.apk) sur ton téléphone Android.

### Étape 2 — Autoriser l'installation d'applications inconnues

Par défaut, Android bloque les installations en dehors du Play Store. Il faut lever cette restriction une fois :

1. Ouvre les **Paramètres**.
2. Va dans **Applications** → **Accès spécial** → **Installer des applis inconnues**.
3. Choisis ton navigateur (Chrome, Firefox…) ou ton gestionnaire de fichiers.
4. Active **Autoriser cette source**.

Le chemin exact varie selon la version d'Android et le fabricant, mais il est toujours quelque part dans Paramètres → Applications.

### Étape 3 — Installer l'APK

Ouvre le fichier `.apk` téléchargé (dans les notifications ou le dossier Téléchargements) et appuie sur **Installer**.

Play Protect peut afficher un avertissement, car l'app demande l'autorisation d'envoyer des SMS. Appuie sur **Installer quand même** : c'est indispensable pour que l'app envoie le résultat du tirage à chaque participant.

### Étape 4 — Autoriser les paramètres restreints (Android 13 et plus)

Pour protéger contre les applis malveillantes, Android 13 et les versions suivantes bloquent l'autorisation d'envoyer des SMS pour les applis installées hors du Play Store : c'est un **paramètre restreint**. Tant qu'il n'est pas débloqué, l'option SMS est grisée, ou Android affiche « Paramètre restreint » quand tu essaies de l'activer.

Pour le débloquer, une seule fois :

1. Ouvre les **Paramètres** → **Applications** → **MySecretSanta**.
   Raccourci : appui long sur l'icône de l'app → **ⓘ** (Infos sur l'appli).
2. Va dans **Autorisations** → **SMS** et essaie de l'autoriser. Android refuse et affiche « Paramètre restreint » : c'est normal, cette tentative fait apparaître l'option de l'étape suivante.
3. Reviens sur la page de l'app (MySecretSanta), touche **⋮** en haut à droite, puis **Autoriser les paramètres restreints**. Confirme avec ton code, ton schéma ou ton empreinte.
4. Retourne dans **Autorisations** → **SMS** et choisis **Autoriser**.

Les noms exacts varient un peu selon le fabricant (Samsung, Xiaomi…), mais le menu **⋮** est toujours sur la page de l'app dans les Paramètres. Sur Android 12 et avant, cette étape n'existe pas : l'app demande simplement l'autorisation au premier envoi.

### Étape 5 — Ouvrir MySecretSanta

Retrouve l'app dans la liste de tes applications.

---

## Mettre à jour

Quand une nouvelle version est disponible, refais les étapes 1 et 3. L'app se met à jour sans perdre tes participants, tes groupes ni tes tirages.

---

Construit avec amour par Mose et Claude
