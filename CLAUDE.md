# MySecretSanta

App Android perso : liste d'invités, tirage au sort du Secret Santa, envoi direct du résultat par SMS à chaque participant.

- Spécification : [SPEC.md](SPEC.md). La lire avant toute fonctionnalité, et la mettre à jour quand une décision la modifie.
- Stack : Kotlin, Jetpack Compose, Material 3. Interface et messages en français.
- Package : `io.github.chezmose.mysecretsanta`.

## Compilation

Aucun outil Android ni Java sur le poste : **toute compilation passe par GitHub Actions** ([.github/workflows/build.yml](.github/workflows/build.yml)).

- Chaque push sur `main` compile l'APK release signé et le publie dans la release `dev` :
  `https://github.com/ChezMose/MySecretSanta/releases/download/dev/MySecretSanta.apk`
- Vérifier un build : `gh run list --limit 3`, puis `gh run view <id> --log-failed` en cas d'échec.
- Gradle est fourni par `gradle/actions/setup-gradle` (version fixée dans le workflow), sans wrapper dans le dépôt.

## Signature

- Clé PKCS12, alias `mysecretsanta`, même mot de passe pour le store et la clé.
- Elle est dans les secrets GitHub `KEYSTORE_BASE64` et `KEYSTORE_PASSWORD`, et sauvegardée hors du dépôt dans `e:\Dev\- perso -\MySecretSanta-signature\`.
- Le dépôt est **public** : ne jamais y versionner la clé ni son mot de passe.
- Changer de clé empêche la mise à jour de l'app installée : il faudrait la désinstaller et perdre ses données.
