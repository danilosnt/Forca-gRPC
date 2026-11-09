import './styles/main.css';
import { aoReceber, api } from './api/forcaApi.js';
import { $, avisar, mostrarTela, tentar } from './ui/dom.js';
import { desenharLobby, iniciarLobby } from './ui/lobby.js';
import { desenharSala, iniciarSala } from './ui/sala.js';

aoReceber({
  lobbyAtualizado: desenharLobby,
  aviso: avisar,
  salaAtualizada(sala) {
    desenharSala(sala);
    mostrarTela(sala ? 'tela-sala' : 'tela-lobby');
  },
  desconectado() {
    desenharSala(null);
    mostrarTela('tela-entrada');
    avisar('A conexão com o servidor caiu. Entre novamente.');
  },
});

$('form-entrada').addEventListener('submit', (evento) => {
  evento.preventDefault();
  tentar(async () => {
    const eu = await api.entrar($('apelido').value);
    $('lobby-apelido').textContent = eu.apelido;
    mostrarTela('tela-lobby');
  });
});

iniciarLobby();
iniciarSala();
