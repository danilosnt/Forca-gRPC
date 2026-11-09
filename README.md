# Jogo da Forca com RPC

Jogo da forca para 2 jogadores por sala, feito para a disciplina de Sistemas
Distribuídos. A API é em Java e o frontend é um projeto separado. Toda a
comunicação usa **JSON-RPC 2.0 sobre WebSocket**.

```text
sis-dis/
├── backend/                 API em Java 21 (Maven)
│   └── src/main/java/com/sisdis/forca/
│       ├── Aplicacao.java       ponto de entrada
│       ├── api/                 WebSocket e métodos remotos
│       ├── rpc/                 protocolo JSON-RPC 2.0
│       └── dominio/             regras do jogo, salas e concorrência
└── frontend/                Interface web (Vite)
    └── src/
        ├── main.js              ponto de entrada
        ├── api/                 stub do cliente RPC
        ├── ui/                  telas do lobby e da sala
        └── styles/              estilos
```

## Como rodar

Precisa de Java 21, Maven e Node.js 20 ou mais novo. Use um terminal para cada parte.

**Backend** (porta 8080; para trocar, use a variável `PORT`):

```bash
cd backend
```

```bash
mvn package
```

```bash
java -jar target/forca-backend.jar
```

**Frontend** (porta 5173):

```bash
cd frontend
```

```bash
npm install
```

```bash
npm run dev
```

Abra `http://localhost:5173`. Em outro computador da rede, abra
`http://IP-DO-SERVIDOR:5173`. O frontend procura a API na porta 8080 do mesmo
host da página; se ela estiver em outra máquina, copie `.env.example` para `.env`
e ajuste `VITE_API_URL`.

Para rodar os testes do backend:

```bash
mvn test
```

## Regras

- Cada sala tem exatamente 2 jogadores. Quem entra primeiro define a palavra e
  quem entra depois adivinha.
- A palavra tem de 3 a 20 letras, sem espaços, e a dica é opcional. Ela aceita
  hífen entre letras, como em guarda-chuva. O hífen não conta no limite de letras
  e já aparece visível para quem adivinha.
- Acentos são ignorados nos palpites: a letra A revela também Á e Ã.
- São permitidos 6 erros. Arriscar a palavra errada conta 1 erro, e letra
  repetida não conta.
- Ao fim da rodada, quem vence ganha 1 ponto. Na próxima rodada os papéis se
  invertem.
- Se um jogador sair, a partida acaba, o placar zera e quem ficou espera um novo
  adversário. Sala vazia é apagada.

## Procedimentos remotos

Chamados pelo cliente no servidor:

| Método | Parâmetros | Resultado |
| --- | --- | --- |
| `jogador.entrar` | `apelido` | `{ apelido }` |
| `sala.criar` | `nome` | `{ id }` |
| `sala.entrar` | `id` | `null` |
| `sala.sair` | | `null` |
| `partida.definirPalavra` | `palavra`, `dica` (opcional) | `null` |
| `partida.tentarLetra` | `letra` | `{ resultado }`: `acerto`, `erro` ou `repetida` |
| `partida.arriscarPalavra` | `palavra` | `{ resultado }`: `acerto` ou `erro` |
| `partida.novaRodada` | | `null` |

Chamados pelo servidor no cliente, como notificações JSON-RPC (sem `id`):

| Método | Parâmetros | Quando |
| --- | --- | --- |
| `lobby.atualizado` | `salas` | Uma sala é criada, enche, esvazia ou some |
| `sala.atualizada` | `sala` (`null` ao sair) | Qualquer mudança na sala |
| `sala.aviso` | `mensagem` | O adversário saiu |

Erros usam os códigos padrão do JSON-RPC (`-32700`, `-32600`, `-32601`, `-32602`,
`-32603`). Violações de regra do jogo usam `-32000`.

## Concorrência

O servidor atende as conexões em paralelo, em várias threads.

- **Trava por sala:** cada `Sala` tem um `ReentrantLock`. Chamadas na mesma sala
  rodam uma por vez, e salas diferentes rodam em paralelo.
- **Lobby:** usa coleções concorrentes e uma trava própria para publicar a lista
  de salas sempre na ordem certa.
- **Ordem das travas:** sempre lobby antes de sala, e o lobby só publica depois de
  soltar a trava da sala.
- **Sem I/O com trava presa:** cada conexão tem uma fila de saída própria. O domínio
  só coloca mensagens na fila, então um cliente lento ou caído não trava a sala.
