# MeuTube

App Android pessoal para ver YouTube sem anúncios. Funciona como o NewPipe:
nunca carrega o player do YouTube, só os ficheiros de vídeo e áudio, por isso os
anúncios nunca chegam a ser pedidos.

Só para uso pessoal. Não publiques nem distribuas: viola os Termos do YouTube.

## Como compilar

**Opção A: Android Studio (no PC)**
1. Instala o [Android Studio](https://developer.android.com/studio).
2. `File > Open` e escolhe esta pasta. Espera que o Gradle sincronize (a primeira vez demora).
3. Liga o telemóvel por USB com a *Depuração USB* ativa e carrega em ▶ Run.

**Opção B: GitHub Actions (sem instalar nada)**
1. Cria um repositório **privado** no GitHub e faz push desta pasta.
2. Separador *Actions* → último build → descarrega `MeuTube-apk`.
3. Passa o `.apk` para o telemóvel e instala (tens de permitir "fontes desconhecidas").

## Como está organizado

```
app/src/main/java/pt/meutube/
├── MeuTubeApp.kt          Arranque: liga o extractor ao nosso cliente HTTP
├── MainActivity.kt        Navegação entre ecrãs + barra de baixo
├── data/
│   ├── OkHttpDownloader.kt  "Ponte": o extractor pede, nós fazemos o pedido HTTP
│   ├── YouTubeRepo.kt       Pesquisa, vídeo (links dos streams), canal, feed
│   ├── Storage.kt           Subscrições e histórico guardados no telemóvel
│   └── Models.kt            Classes simples usadas pela interface
└── ui/
    ├── SearchScreen.kt      Pesquisa
    ├── FeedScreen.kt        Canais subscritos + vídeos recentes
    ├── HistoryScreen.kt     Vídeos vistos
    ├── PlayerScreen.kt      Player (ExoPlayer), qualidade, subscrever, sugestões
    └── Common.kt            Linha de vídeo, loading, mensagens
```

O caminho de um vídeo:
`PlayerScreen` → `YouTubeRepo.video()` → `StreamInfo.getInfo()` (NewPipeExtractor)
→ `OkHttpDownloader` faz os pedidos → recebemos os links → `ExoPlayer` toca-os.

## Quando algo deixar de funcionar

O YouTube muda coisas e a extração parte-se de vez em quando. A solução quase
sempre é atualizar o extractor em `app/build.gradle.kts`:

```kotlin
implementation("com.github.TeamNewPipe:NewPipeExtractor:vX.Y.Z")
```

A versão mais recente está em https://github.com/TeamNewPipe/NewPipeExtractor/releases
