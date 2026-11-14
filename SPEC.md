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
| **Personne** | Une entrée du carnet de l'app : nom et numéro de mobile, ou aucun numéro pour une personne sans téléphone (§5.9). Une personne peut appartenir à plusieurs groupes. |
| **Groupe** | Un ensemble de personnes qui font régulièrement un tirage ensemble, avec ses exclusions. Il est enregistré et réutilisé d'une année sur l'autre. |
| **Exclusion** | Une règle d'un groupe qui interdit certaines attributions, par exemple dans un couple. |
| **Tirage** | Un échange de cadeaux précis, créé à partir d'un groupe : « Noël 2026 », avec son budget, son message et ses participants. |

## 5. Fonctionnalités

### 5.1 Personnes

- Le bouton « Nouvelle personne » propose deux choix : **Choisir dans les contacts** ou **Saisir à la main**.
  - *Choisir dans les contacts* ouvre directement le sélecteur de contacts du système, puis la fiche pré-remplie (nom et numéro) : il ne reste qu'à cocher les groupes et enregistrer. Annuler le sélecteur annule la création. Ce sélecteur ne demande pas la permission `READ_CONTACTS`.
  - *Saisir à la main* ouvre la fiche vide : nom affiché (obligatoire), numéro de mobile (obligatoire, sauf personne sans téléphone, §5.9).
- Le choix entre contacts et saisie se fait avant d'ouvrir la fiche : la fiche elle-même n'a pas de bouton « Choisir dans les contacts ».
- Modifier et supprimer une personne. Supprimer une personne la retire de ses groupes et de leurs exclusions. Les tirages passés gardent son nom.
- Validation du numéro : format français (`06…`, `07…`) ou international (`+33…`), normalisé au format international avant l'enregistrement.
- Refus des doublons : deux personnes ne peuvent pas avoir le même numéro.

### 5.2 Groupes

- Créer, renommer, dupliquer et supprimer un groupe.
- Ajouter des membres de trois façons : depuis le carnet (plusieurs à la fois), depuis les contacts, ou en saisissant une nouvelle personne. Une personne créée depuis le groupe en devient membre d'office.
- Retirer un membre du groupe, sans le supprimer du carnet.
- Chaque groupe affiche l'historique de ses tirages.

### 5.3 Exclusions

Les exclusions appartiennent à un groupe et s'appliquent à chacun de ses tirages. Trois types :

| Type | Effet | Exemple |
|---|---|---|
| **Couple** | A n'offre pas à B, et B n'offre pas à A. | Deux conjoints. |
| **Sens unique** | A n'offre pas à B. B peut offrir à A. | Exclusion sur mesure, au cas par cas. |
| **Foyer** | Aucun membre du foyer n'offre à un autre membre du foyer. | Parents et enfants vivant ensemble. |

- Créer, modifier et supprimer une exclusion, depuis la fiche du groupe. Un libellé facultatif rappelle sa raison (« Mariés »).
- Une exclusion ne concerne que des membres du groupe. Si une personne quitte le groupe ou est supprimée, elle est retirée de ses exclusions, et une exclusion qui ne concerne plus au moins 2 personnes disparaît.
- Dupliquer un groupe copie aussi ses exclusions.
- La fiche du groupe signale tout de suite, avec l'algorithme de tirage :
  - un tirage **impossible**, en nommant les personnes bloquées quand c'est évident (« Alice ne peut offrir à personne. ») ;
  - un tirage possible **seulement avec un échange réciproque**, que l'organisateur pourra accepter au moment du tirage.

### 5.4 Tirage au sort

**Création :**

- Créer un tirage à partir d'un groupe, depuis l'onglet Tirages (choix du groupe) ou depuis la fiche du groupe : nom (obligatoire, « Noël <année> » proposé), budget (facultatif, texte libre, ex. « 30 € »). Pas de date de l'échange.
- Par défaut tous les membres du groupe participent. On peut décocher les absents pour ce tirage.
- Minimum 3 participants. La faisabilité (§5.3) est vérifiée en direct avec les participants cochés.
- Un tirage reste en **brouillon** tant qu'il n'est pas tiré : tout y reste modifiable, et il peut être supprimé.
- Un brouillon suit son groupe : une personne qui quitte le groupe, ou qui est supprimée, en est retirée ; supprimer le groupe supprime ses brouillons. Les tirages déjà faits restent dans l'historique, même sans leur groupe.

**Règles du tirage :**

- Chaque participant offre à exactement un participant et reçoit d'exactement un participant.
- Personne ne se tire lui-même.
- Toutes les exclusions du groupe sont respectées.
- Pas d'échange réciproque : si A offre à B, B n'offre pas à A.
- Le tirage est aléatoire (`SecureRandom`) et chaque répartition valide a la même probabilité.

**Cas impossibles :**

- Si aucune répartition ne respecte les exclusions, l'app affiche un message clair et n'envoie rien.
- Si une répartition existe seulement en autorisant un échange réciproque, l'app le dit et propose de lever cette règle pour ce tirage.

**Tirer au sort :** depuis l'écran du brouillon, bouton « Tirer au sort ». L'app fige alors la liste des participants (cochés et toujours membres du groupe) avec leurs noms et numéros du moment, revérifie le message, puis tire au sort. Modifier ensuite le groupe ou une personne ne change pas un tirage déjà fait. Si le tirage n'est possible qu'avec un échange réciproque, l'app propose de l'accepter pour ce tirage, puis relance le tirage.

**Refaire le tirage :** menu de l'écran Tirage. Le tirage redevient un brouillon (participants conservés, résultats effacés), après une confirmation qui prévient si des résultats ont déjà été transmis.

**Algorithme** ([SecretSanta.kt](app/src/main/java/io/github/chezmose/mysecretsanta/draw/SecretSanta.kt)) :

1. Un couplage parfait (algorithme de Kuhn) vérifie qu'au moins une répartition respecte les exclusions. Sinon : *impossible*, sans chercher plus loin.
2. Échantillonnage par rejet : mélanger les participants au hasard et recommencer tant que le résultat viole une règle. Chaque répartition valide a exactement la même probabilité de sortir. Pour des groupes réalistes, une solution arrive en quelques essais.
3. Au-delà de 200 000 essais (exclusions très serrées), une recherche exhaustive au hasard trouve une répartition valide, ou prouve qu'il n'en existe qu'avec un échange réciproque. Cette solution de secours est aléatoire mais pas parfaitement équiprobable.

Les exclusions sont transmises à l'algorithme sous forme de paires interdites (donneur, receveur) : un couple donne deux paires, un foyer toutes les paires entre ses membres.

Les tests ([SecretSantaTest.kt](app/src/test/java/io/github/chezmose/mysecretsanta/draw/SecretSantaTest.kt)) comparent le verdict de l'algorithme à l'énumération de toutes les répartitions possibles jusqu'à 8 participants, vérifient les règles sur des milliers de tirages jusqu'à 50 participants, et mesurent l'équiprobabilité.

### 5.5 Secret du tirage

L'organisateur participe souvent au tirage. Il ne doit donc pas voir les résultats.

- Après le tirage, l'écran n'affiche que l'état de transmission de chaque participant (§5.6), jamais à qui il offre.
- Le tirage est conservé, même une fois l'échange passé. Il sert à renvoyer un SMS, sans l'afficher, et à garder l'historique.
- Une action « Révéler le tirage » (menu de l'écran Tirage), derrière une double confirmation, affiche « donneur → receveur » pour chacun. L'app enregistre que le tirage a été révélé : il apparaît comme « Révélé » dans les listes.

**Limite connue :** quand une app qui n'est pas l'app SMS par défaut envoie un SMS, Android le copie dans l'historique de l'app SMS du téléphone. Les messages envoyés apparaissent donc dans les conversations de l'organisateur. L'app ne peut ni l'empêcher, ni les supprimer ensuite : Android réserve l'écriture et la suppression dans l'historique des SMS à l'app SMS par défaut. Devenir temporairement l'app SMS par défaut a été écarté : les SMS reçus pendant ce temps risqueraient d'être perdus. Il faut le signaler à l'utilisateur avant l'envoi, avec le conseil de ne pas ouvrir ces conversations, ou de les supprimer.

### 5.6 Envoi des SMS

- Un bouton « Envoyer les SMS (N) » envoie les SMS restants (jamais envoyés ou en échec), après une confirmation qui rappelle la limite §5.5. Les SMS partent l'un après l'autre, à une seconde d'intervalle.
- La permission `SEND_SMS` est demandée au moment du premier envoi, avec la permission facultative de lire l'état du téléphone (pour lister les SIM). Si l'envoi de SMS est refusé, l'app explique pourquoi elle en a besoin et ouvre les réglages de l'app.
- Les messages longs sont découpés (`divideMessage` / `sendMultipartTextMessage`).
- Suivi par participant : *SMS à envoyer* → *Envoi en cours* → *SMS envoyé* → *SMS distribué* (accusé de réception, si l'opérateur le fournit), ou *Échec* avec le motif (pas de réseau, mode avion, limite d'envoi…). Le tirage passe à « Envoyé » quand chaque participant a reçu (ou vu, §5.9) son résultat.
- Renvoyer le SMS d'un seul participant (bouton d'envoi sur sa ligne), par exemple après un échec ou un message perdu.
- Double SIM : la confirmation d'envoi propose de choisir la SIM ; le choix est retenu pour les envois suivants. Avec une seule SIM, ou sans la permission de lire l'état du téléphone, le SMS part par la SIM par défaut.
- Android limite le nombre de SMS envoyés par une app en peu de temps (environ 30 en 30 minutes, selon les constructeurs). Au-delà, le système demande à l'utilisateur d'autoriser l'envoi. L'app doit tolérer cette demande : envoyer les SMS l'un après l'autre et reprendre les envois restés en attente.

### 5.7 Message

**Un seul SMS par participant, toujours.** Le message n'utilise que l'alphabet SMS standard (GSM), qui permet 160 caractères par SMS ; un seul caractère hors de cet alphabet ferait passer le SMS en Unicode, limité à 70 caractères.

Un modèle par défaut, modifiable dans les réglages, est copié dans chaque nouveau tirage, où on peut l'adapter :

> Bonjour {donneur}, pour {tirage}, tu offres un cadeau à {receveur}.
> Ne réponds pas à ce message ! C'est un secret pour moi aussi.

- Variables : `{donneur}`, `{receveur}`, `{tirage}`, `{budget}`. Une variable dont le champ est vide (budget absent) est retirée avec sa phrase. Les retours à la ligne du modèle sont conservés.
- Le message doit contenir `{receveur}` : sinon, personne ne saurait à qui offrir.
- **Caractères interdits** : la saisie du modèle est convertie au fil de l'eau (ê → e, ç → c, « » → ", émojis retirés). Les noms et le nom du tirage sont convertis de la même façon à l'envoi : « Noël » devient « Noel ».
- **Longueur** : l'app affiche la longueur du message avec le plus long nom des participants (« 137 / 160 caractères »), et refuse d'enregistrer un message qui dépasse 160 caractères. Dans les réglages, la vérification se fait avec un nom d'exemple (« Jean-Baptiste ») ; chaque tirage revérifie avec ses vrais participants.
- Un aperçu du message utilise des noms fictifs (Alice offre à Bob), jamais le vrai tirage.

### 5.8 Partager l'app

Un bouton « Partager » permet de transmettre l'app à quelqu'un, en lui montrant l'écran du téléphone.

- L'écran Partager affiche un QR code vers la page du dépôt GitHub, `https://github.com/ChezMose/MySecretSanta`. Son README explique comment télécharger et installer l'app.
- Sous le QR code, le même lien est cliquable et s'ouvre dans le navigateur.
- L'app dessine le QR code elle-même, avec la bibliothèque ZXing, sans accès réseau. Il est toujours noir sur fond blanc, pour être lisible par tous les lecteurs.
- L'URL est définie à un seul endroit du code : `AppLinks.REPO_URL`.

### 5.9 Participants sans téléphone

Certains participants n'ont pas de téléphone, par exemple un enfant. Ils participent au tirage comme les autres, mais découvrent leur résultat sur le téléphone de l'organisateur.

**Fiche personne :**

- Une case à cocher « Pas de téléphone », sous le nom, masque le champ du numéro et rappelle que la personne découvrira son tirage sur ce téléphone.
- Dans les listes, une personne sans téléphone affiche « Pas de téléphone » à la place du numéro.
- Plusieurs personnes peuvent être sans téléphone : la règle « un numéro unique » ne s'applique qu'aux numéros renseignés.

**Tirage :**

- Une personne sans téléphone offre et reçoit un cadeau comme les autres. Elle peut être le receveur de n'importe quel participant.
- Elle ne reçoit pas de SMS. L'écran Tirage affiche à la place un bouton **« Voir pour XX »**, XX étant son nom, pour chacun des participants sans téléphone.
- Appuyer sur ce bouton affiche un compte à rebours de 5 secondes en plein écran, avec un bouton **Annuler**. Ce délai laisse à l'organisateur le temps de tendre le téléphone à XX sans voir le résultat.
- Sans annulation, l'écran affiche ensuite : « XX, tu offres un cadeau à **YY** », avec un bouton pour masquer le résultat et revenir à l'écran Tirage.
- Le bouton reste disponible, pour revoir le résultat si XX l'a oublié. L'état du participant passe à *Vu* au premier affichage, comme *Envoyé* pour un SMS.

Fait à l'étape 6, avec l'écran Tirage.

## 6. Écrans

L'écran d'accueil a trois onglets : **Tirages**, **Groupes**, **Personnes**. Sa barre du haut porte les boutons **Partager** et **Réglages**.

1. **Tirages** : liste des tirages, du plus récent au plus ancien, avec groupe, nombre de participants et état (*brouillon*, *tiré*, *envoyé*, *révélé*). Bouton « Nouveau tirage ».
2. **Groupes** : liste des groupes avec leur nombre de membres. Bouton « + ».
3. **Personnes** : carnet, avec recherche par nom ou numéro. Bouton « Nouvelle personne », qui propose *Choisir dans les contacts* ou *Saisir à la main*.
4. **Groupe** : membres, ajout depuis le carnet, depuis les contacts ou par saisie, exclusions, historique des tirages (avec leur état), bouton « Nouveau tirage ».
5. **Exclusion** (création / modification) : type, personnes concernées, libellé.
6. **Personne** (création / modification) : nom, case « Pas de téléphone », numéro, groupes dont elle est membre (cases à cocher). En création depuis les contacts, le sélecteur s'ouvre d'abord et pré-remplit le nom et le numéro.
7. **Nouveau tirage / Modifier le tirage** (brouillon) : groupe (au choix depuis l'onglet Tirages), nom, budget, participants à cocher avec l'alerte de faisabilité, message avec aperçu et longueur (un seul SMS). Suppression du brouillon.
8. **Tirage** : groupe, budget. Brouillon : faisabilité, participants, boutons « Tirer au sort » et « Modifier le tirage ». Tiré : bilan des envois, bouton « Envoyer les SMS (N) », état de chaque participant avec renvoi individuel, bouton « Voir pour XX » par participant sans téléphone (§5.9), menu « Révéler le tirage », « Refaire le tirage », « Supprimer le tirage ».
9. **Réglages** : modèle de message par défaut, version de l'app.
10. **Partager** : QR code vers le dépôt GitHub, lien cliquable (§5.8).

## 7. Données

Stockage local SQLite.

| Entité | Champs |
|---|---|
| `Person` | id, name, phoneE164? (absent pour une personne sans téléphone) |
| `Group` | id, name |
| `GroupMember` | groupId, personId |
| `ExclusionRule` | id, groupId, type (couple, sens unique, foyer), label? |
| `ExclusionMember` | ruleId, personId, role (donneur, receveur ou membre) |
| `Draw` | id, groupId? (`null` si le groupe est supprimé), name, dateEpochDay? (inutilisée depuis le retrait de la date), budget?, messageTemplate, status (brouillon, tiré, envoyé), allowReciprocal, revealed, createdAt |
| `Participant` | id, drawId, personId? (`null` si la personne est supprimée), name, phoneE164? : sélection en cours dans un brouillon, copie figée au tirage ; après le tirage : receiverParticipantId (secret), status (à envoyer, en cours, envoyé, distribué, échec, à montrer, montré), statusError?, statusAt? |

Les sauvegardes automatiques Android (`allowBackup`) sont désactivées : elles enverraient les tirages dans le cloud Google.

Les réglages (message par défaut) sont stockés à part, dans les préférences de l'app sur le téléphone.

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

**Installation :** le [README](README.md) du dépôt explique la procédure pas à pas. C'est la page vers laquelle pointe le QR code de l'écran Partager (§5.8). En résumé : sur le téléphone, ouvrir `https://github.com/ChezMose/MySecretSanta/releases/download/dev/MySecretSanta.apk` et installer le fichier téléchargé. Le téléphone doit autoriser l'installation d'applications de sources inconnues pour le navigateur. Play Protect peut afficher un avertissement à cause de `SEND_SMS` : il suffit de le valider. Sur Android 13 et plus, la permission SMS est un « paramètre restreint » pour une app installée hors du Play Store : il faut l'autoriser depuis la page de l'app dans les Paramètres (menu ⋮ → « Autoriser les paramètres restreints »), puis accorder la permission SMS. Le README et la boîte de dialogue de refus de la permission expliquent la procédure. L'APK est public, comme le dépôt.

## 9. Critères d'acceptation

- Avec 3 à 30 participants et des exclusions réalisables, le tirage aboutit toujours. Personne ne se tire lui-même, aucune exclusion n'est violée et aucun échange n'est réciproque, sauf si l'organisateur a levé cette règle (vérifié par des tests sur des milliers de tirages).
- Avec des exclusions impossibles, l'app le dit et n'envoie aucun SMS.
- Un groupe créé une année sert de nouveau l'année suivante, avec ses membres et ses exclusions, sans rien ressaisir.
- Chaque participant reçoit un seul SMS avec le bon receveur. L'état de chaque envoi est visible.
- Un participant sans téléphone découvre son receveur sur le téléphone de l'organisateur, après un compte à rebours de 5 secondes que l'on peut annuler.
- L'organisateur peut mener le tirage et l'envoi jusqu'au bout sans jamais voir le résultat dans l'app.
- Un SMS en échec peut être renvoyé sans refaire le tirage.
- Modifier un groupe ou une personne ne change pas un tirage déjà fait.
- L'app fonctionne en mode avion pour tout ce qui ne concerne pas l'envoi des SMS.

## 10. Étapes de réalisation

1. ✅ Projet Android vide, compilé et signé par GitHub Actions, qui s'installe sur le téléphone. Écran Partager et README d'installation.
2. ✅ Personnes et groupes (stockage + écrans 2, 3, 4, 6), dont les personnes sans téléphone.
3. ✅ Algorithme de tirage et tests unitaires.
4. ✅ Exclusions (écran 5).
5. Création d'un tirage et modèle de message (écrans 7, 9).
6. Envoi des SMS, suivi des états, historique (écrans 1, 8).
7. Participants sans téléphone (§5.9) : bouton « Voir pour XX » avec compte à rebours sur l'écran Tirage.
