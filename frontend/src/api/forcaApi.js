// Stub do cliente: uma função para cada método remoto da API.
import { ClienteRpc } from './rpcClient.js';

// Em desenvolvimento a API roda à parte, na porta 8080. No build de produção
// (Docker), o nginx serve a página e repassa /rpc para a API no mesmo endereço.
const URL_PADRAO = import.meta.env.DEV
  ? `ws://${location.hostname}:8080/rpc`
  : `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/rpc`;

const URL_API = import.meta.env.VITE_API_URL || URL_PADRAO;

const rpc = new ClienteRpc();

export const api = {
  async entrar(apelido) {
    if (!rpc.conectado) await rpc.conectar(URL_API);
    return rpc.chamar('jogador.entrar', { apelido });
  },
  criarSala: (nome) => rpc.chamar('sala.criar', { nome }),
  entrarSala: (id) => rpc.chamar('sala.entrar', { id }),
  sairSala: () => rpc.chamar('sala.sair'),
  definirPalavra: (palavra, dica) => rpc.chamar('partida.definirPalavra', { palavra, dica }),
  tentarLetra: (letra) => rpc.chamar('partida.tentarLetra', { letra }),
  arriscarPalavra: (palavra) => rpc.chamar('partida.arriscarPalavra', { palavra }),
  novaRodada: () => rpc.chamar('partida.novaRodada'),
};

// Métodos que o servidor chama neste cliente.
export function aoReceber({ lobbyAtualizado, salaAtualizada, aviso, desconectado }) {
  rpc.registrar('lobby.atualizado', ({ salas }) => lobbyAtualizado(salas));
  rpc.registrar('sala.atualizada', ({ sala }) => salaAtualizada(sala));
  rpc.registrar('sala.aviso', ({ mensagem }) => aviso(mensagem));
  rpc.aoDesconectar = desconectado;
}
