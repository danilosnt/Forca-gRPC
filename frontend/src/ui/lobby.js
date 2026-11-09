import { api } from '../api/forcaApi.js';
import { $, tentar } from './dom.js';

function criarItemSala({ id, nome, jogadores, cheia }) {
  const item = document.createElement('li');
  item.className = 'sala';

  const info = document.createElement('div');
  const titulo = document.createElement('strong');
  titulo.textContent = nome;
  const detalhe = document.createElement('small');
  detalhe.textContent = cheia
    ? `${jogadores.join(' e ')} estão jogando`
    : `${jogadores[0]} está esperando um adversário`;
  info.append(titulo, detalhe);

  const botao = document.createElement('button');
  botao.type = 'button';
  botao.textContent = cheia ? 'Cheia' : 'Entrar';
  botao.disabled = cheia;
  botao.addEventListener('click', () => tentar(() => api.entrarSala(id)));

  item.append(info, botao);
  return item;
}

export function desenharLobby(salas) {
  $('sem-salas').hidden = salas.length > 0;
  $('lista-salas').replaceChildren(...salas.map(criarItemSala));
}

export function iniciarLobby() {
  $('form-sala').addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (await tentar(() => api.criarSala($('nome-sala').value))) $('form-sala').reset();
  });
}
