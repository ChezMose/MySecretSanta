# MySecretSanta — Spécification

Version 0.3 — 11/11/2026

## 1. Objectif

Application Android qui permet à un organisateur de :

1. enregistrer des personnes et les réunir en groupes réutilisables (famille, collègues…) ;
2. tirer au sort, au sein d'un groupe, qui offre un cadeau à qui ;
3. envoyer à chaque participant, par SMS, le nom de la personne à qui il doit offrir un cadeau.

Seul l'organisateur installe l'application. Les participants reçoivent un simple SMS, sans rien installer.

## 2. Contraintes

| Contrainte | Conséquence |
|---|---|
| Gratuit | Outils gratuits uniquement. Aucun service payant, aucun serveur. |
| Pas de publication sur le Play Store | Distribution par APK installé à la main (sideload). La permission `SEND_SMS`, refusée par Google Play pour ce type d'app, devient utilisable. |
| Envoi direct des SMS | Envoi via `SmsManager`, sans passer par l'app SMS du téléphone. Les SMS partent du forfait de l'organisateur. |
| Hors ligne | Aucun accès réseau : l'app ne déclare pas la permission `INTERNET`. Les données restent sur le téléphone. |
| Français | Interface et messages en français uniquement. |

## 3. Hors périmètre (v1)

- iOS, version web, compte utilisateur, synchronisation cloud.
- Envoi par e-mail, WhatsApp ou autre messagerie.
- Liste de souhaits des participants, réponses par SMS.
- Tenir compte des tirages précédents : une personne peut retomber sur le même receveur qu'une autre année.

## 4. Concepts

| Concept | Définition |
|---|---|
| **Personne** | Une entrée du carnet de l'app : nom et numéro de mobile. Une personne peut appartenir à plusieurs groupes. |
| **Groupe** | Un ensemble de personnes qui font régulièrement un tirage ensemble, avec ses exclusions. Il est enregistré et réutilisé d'une année sur l'autre. |
| **Exclusion** | Une règle d'un groupe qui interdit certaines attributions, par exemple dans un couple. |
| **Tirage** | Un échange de cadeaux précis, créé à partir d'un groupe : « Noël 2026 », avec sa date, son budget, son message et ses participants. |

## 5. Fonctionnalités

### 5.1 Personnes

- Ajouter une personne à la main : nom affiché (obligatoire), numéro de mobile (obligatoire).
- Ajouter une personne depuis les contacts du téléphone, via le sélecteur de contacts système. Ce sélecteur ne demande pas la permission `READ_CONTACTS`.
- Modifier et supprimer une personne. Supprimer une personne la retire de ses groupes et de leurs exclusions. Les tirages passés gardent son nom.
- Validation du numéro : format français (`06…`, `07…`) ou international (`+33…`), normalisé au format international avant l'enregistrement.
- Refus des doublons : deux personnes ne peuvent pas avoir le même numéro.

### 5.2 Groupes

- Créer, renommer, dupliquer et supprimer un groupe.
- Ajouter des membres depuis le carnet, ou créer une personne directement depuis le groupe.
- Retirer un membre du groupe, sans le supprimer du carnet.
- Chaque groupe affiche l'historique de ses tirages.

### 5.3 Exclusions

Les exclusions appartiennent à un groupe et s'appliquent à chacun de ses tirages. Trois types :

| Type | Effet | Exemple |
|---|---|---|
| **Couple** | A n'offre pas à B, et B n'offre pas à A. | Deux conjoints. |
| **Sens unique** | A n'offre pas à B. B peut offrir à A. | Exclusion sur mesure, au cas par cas. |
| **Foyer** | Aucun membre du foyer n'offre à un autre membre du foyer. | Parents et enfants vivant ensemble. |

- Créer, modifier et supprimer une exclusion. Un libellé facultatif rappelle sa raison.
- L'app signale tout de suite si les exclusions rendent un tirage impossible, par exemple quand une personne est exclue de toutes les autres.

### 5.4 Tirage au sort

**Création :**

- Créer un tirage à partir d'un groupe : nom (obligatoire), date de l'échange (facultative), budget (facultatif, texte libre, ex. « 30 € »).
- Par défaut tous les membres du groupe participent. On peut décocher les absents pour ce tirage.
- Minimum 3 participants.

**Règles du tirage :**

- Chaque participant offre à exactement un participant et reçoit d'exactement un participant.
- Personne ne se tire lui-même.
- Toutes les exclusions du groupe sont respectées.
- Pas d'échange réciproque : si A offre à B, B n'offre pas à A.
- Le tirage est aléatoire (`SecureRandom`) et chaque répartition valide a la même probabilité.

**Cas impossibles :**

- Si aucune répartition ne respecte les exclusions, l'app affiche un message clair et n'envoie rien.
- Si une répartition existe seulement en autorisant un échange réciproque, l'app le dit et propose de lever cette règle pour ce tirage.

**Figer le tirage :** au moment du tirage, l'app fige la liste des participants, leurs noms et numéros. Modifier ensuite le groupe ou une personne ne change pas un tirage déjà fait. Refaire un tirage déjà envoyé demande une confirmation.

**Algorithme proposé :** tirer une permutation au hasard et la rejeter tant qu'elle ne respecte pas les règles. Pour des groupes de taille réaliste (moins de 50 personnes), une solution arrive en quelques essais. Au-delà d'un plafond d'essais, une recherche exhaustive par backtracking distingue « pas de chance » de « impossible ».

### 5.5 Secret du tirage

L'organisateur participe souvent au tirage. Il ne doit donc pas voir les résultats.

- Après le tirage, l'écran n'affiche que l'état d'envoi de chaque participant (§5.6), jamais à qui il offre.
- Le tirage est conservé, même après la date de l'échange. Il sert à renvoyer un SMS, sans l'afficher, et à garder l'historique.
- Une action « Révéler le tirage », derrière une double confirmation, permet de le consulter. L'app enregistre que le tirage a été révélé et l'indique.

**Limite connue :** quand une app qui n'est pas l'app SMS par défaut envoie un SMS, Android le copie dans l'historique de l'app SMS du téléphone. Les messages envoyés apparaissent donc dans les conversations de l'organisateur. L'app ne peut pas l'empêcher. Il faut le signaler à l'utilisateur avant l'envoi, avec le conseil de ne pas ouvrir ces conversations, ou de les supprimer.

### 5.6 Envoi des SMS

- Un bouton « Envoyer les SMS » envoie un message à chaque participant, après confirmation (nombre de SMS, rappel de la limite §5.5).
- La permission `SEND_SMS` est demandée au moment du premier envoi. Si elle est refusée, l'app explique pourquoi elle en a besoin et renvoie vers les réglages.
- Les messages longs sont découpés (`divideMessage` / `sendMultipartTextMessage`).
- Suivi par participant : *En attente* → *Envoyé* → *Distribué* (accusé de réception, si l'opérateur le fournit), ou *Échec* avec le motif (pas de réseau, numéro invalide…).
- Renvoyer le SMS d'un seul participant, par exemple après un échec ou un message perdu.
- Double SIM : choix de la SIM d'envoi s'il y en a plusieurs.
- Android limite le nombre de SMS envoyés par une app en peu de temps (environ 30 en 30 minutes, selon les constructeurs). Au-delà, le système demande à l'utilisateur d'autoriser l'envoi. L'app doit tolérer cette demande : envoyer les SMS l'un après l'autre et reprendre les envois restés en attente.

### 5.7 Message

Un modèle par défaut, modifiable dans les réglages, est copié dans chaque nouveau tirage, où on peut l'adapter :

> 🎅 Bonjour {donneur} ! Pour « {tirage} », tu offres un cadeau à **{receveur}**. Budget : {budget}. Rendez-vous le {date}. Chut, c'est secret !

- Variables : `{donneur}`, `{receveur}`, `{tirage}`, `{budget}`, `{date}`. Une variable dont le champ est vide est retirée avec sa phrase.
- Un aperçu du message utilise des noms fictifs, jamais le vrai tirage.
- Un émoji fait passer le SMS en encodage Unicode, limité à 70 caractères par segment au lieu de 160. L'app affiche le nombre de segments estimé.

## 6. Écrans

L'écran d'accueil a trois onglets : **Tirages**, **Groupes**, **Personnes**.

1. **Tirages** : liste des tirages, du plus récent au plus ancien, avec groupe, date et état (*brouillon*, *tiré*, *envoyé*, *révélé*). Bouton « Nouveau tirage ».
2. **Groupes** : liste des groupes avec leur nombre de membres. Bouton « + ».
3. **Personnes** : carnet, avec recherche. Bouton « + » et « Choisir dans les contacts ».
4. **Groupe** : membres, exclusions, historique des tirages, bouton « Nouveau tirage ».
5. **Exclusion** (création / modification) : type, personnes concernées, libellé.
6. **Personne** (création / modification) : nom, numéro, groupes dont elle est membre.
7. **Nouveau tirage** : groupe, participants à cocher, nom, date, budget, message avec aperçu et nombre de segments.
8. **Tirage** : état de chaque participant, bouton « Tirer au sort », puis « Envoyer les SMS », renvoi individuel, menu « Révéler le tirage ».
9. **Réglages** : modèle de message par défaut, SIM d'envoi par défaut.

## 7. Données

Stockage local SQLite.

| Entité | Champs |
|---|---|
| `Person` | id, name, phoneE164 |
| `Group` | id, name |
| `GroupMember` | groupId, personId |
| `ExclusionRule` | id, groupId, type (couple, sens unique, foyer), label? |
| `ExclusionMember` | ruleId, personId, role (donneur, receveur ou membre) |
| `Draw` | id, groupId, name, date?, budget?, messageTemplate, status, allowReciprocal, revealed, createdAt |
| `Participant` | drawId, personId?, name, phoneE164 (copie figée au tirage) |
| `Assignment` | drawId, giverParticipantId, receiverParticipantId, smsStatus, smsError?, sentAt? |

Les sauvegardes automatiques Android (`allowBackup`) sont désactivées : elles enverraient les tirages dans le cloud Google.

## 8. Choix techniques

| Élément | Choix | Raison |
|---|---|---|
| Langage | Kotlin | Langage officiel d'Android, accès direct à `SmsManager` sans plugin ni module natif. |
| Interface | Jetpack Compose + Material 3 | Standard actuel, peu de code pour des écrans simples. |
| Données | Room | Base locale officielle, gratuite. |
| Architecture | Une Activity, ViewModels, navigation Compose | Suffisant pour une dizaine d'écrans. |
| Version minimale | Android 8.0 (API 26) | Couvre la quasi-totalité des téléphones en service. |
| Outils | VS Code pour le code, Gradle (Kotlin DSL) exécuté par GitHub Actions | Rien à installer sur le PC : ni Android Studio, ni Java, ni SDK Android. |
| Tests | JUnit sur l'algorithme de tirage et la validation des numéros | Ce sont les règles critiques. |

Expo (React Native) a été écarté : `expo-sms` ne fait qu'ouvrir l'app SMS. L'envoi direct demanderait un module natif en Kotlin et un *development build*, et l'avantage multiplateforme ne sert pas, car iOS interdit l'envoi automatique de SMS.

**Compilation :** dans le cloud, par GitHub Actions, sur le dépôt public `ChezMose/MySecretSanta` (minutes gratuites et illimitées). Chaque push sur `main` compile l'APK release, le signe et le publie dans la release `dev`. Compter quelques minutes par compilation.

**Signature :** une clé personnelle unique, stockée dans les secrets GitHub et sauvegardée hors du dépôt. Garder toujours la même clé est indispensable : c'est ce qui permet d'installer une nouvelle version par-dessus l'ancienne sans perdre les données.

**Installation :** sur le téléphone, ouvrir `https://github.com/ChezMose/MySecretSanta/releases/download/dev/MySecretSanta.apk` et installer le fichier téléchargé. Le téléphone doit autoriser l'installation d'applications de sources inconnues pour le navigateur. Play Protect peut afficher un avertissement à cause de `SEND_SMS` : il suffit de le valider. L'APK est public, comme le dépôt.

## 9. Critères d'acceptation

- Avec 3 à 30 participants et des exclusions réalisables, le tirage aboutit toujours. Personne ne se tire lui-même, aucune exclusion n'est violée et aucun échange n'est réciproque, sauf si l'organisateur a levé cette règle (vérifié par des tests sur des milliers de tirages).
- Avec des exclusions impossibles, l'app le dit et n'envoie aucun SMS.
- Un groupe créé une année sert de nouveau l'année suivante, avec ses membres et ses exclusions, sans rien ressaisir.
- Chaque participant reçoit un seul SMS avec le bon receveur. L'état de chaque envoi est visible.
- L'organisateur peut mener le tirage et l'envoi jusqu'au bout sans jamais voir le résultat dans l'app.
- Un SMS en échec peut être renvoyé sans refaire le tirage.
- Modifier un groupe ou une personne ne change pas un tirage déjà fait.
- L'app fonctionne en mode avion pour tout ce qui ne concerne pas l'envoi des SMS.

## 10. Étapes de réalisation

1. Projet Android vide, compilé par GitHub Actions, qui s'installe sur le téléphone.
2. Personnes et groupes (stockage + écrans 2, 3, 4, 6).
3. Algorithme de tirage et tests unitaires.
4. Exclusions (écran 5).
5. Création d'un tirage et modèle de message (écrans 7, 9).
6. Envoi des SMS, suivi des états, historique (écrans 1, 8).
7. APK release signé et installation sur le téléphone.
