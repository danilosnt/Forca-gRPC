import { api } from '../api/forcaApi.js';
import { $, tentar } from './dom.js';

const ALFABETO = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';

let sala = null;

function textoStatus() {
  const { fase, papel, rodada } = sala;
  const adversario = sala.placar.find((j) => j.papel !== papel)?.apelido;

  if (fase === 'definindo') {
    return papel === 'definidor'
      ? 'Escolha a palavra secreta.'
      : `${adversario} está escolhendo a palavra…`;
  }
  if (fase === 'aguardando') return 'Palavra definida. Aguardando um adversário entrar.';
  if (fase === 'jogando') {
    return papel === 'adivinhador'
      ? 'Sua vez: escolha uma letra.'
      : `${adversario} está tentando adivinhar.`;
  }
  const adivinhou = rodada.resultado === 'vitoria';
  if (papel === 'adivinhador') return adivinhou ? 'Você acertou a palavra!' : 'Você foi enforcado.';
  return adivinhou ? `${adversario} adivinhou sua palavra.` : `${adversario} não adivinhou. Você venceu!`;
}

function desenharPalavra({ mascara, palavra, resultado }) {
  const letrasReais = palavra ? Array.from(palavra) : [];
  $('palavra-oculta').replaceChildren(...mascara.map((letra, i) => {
    const casa = document.createElement('span');
    casa.className = 'letra';
    casa.textContent = letra ?? letrasReais[i] ?? '';
    // Quem definiu vê a palavra toda; o que ainda não foi descoberto fica apagado.
    if (letra === null && palavra) casa.classList.add('oculta-para-mim');
    if (letra === '-') casa.classList.add('hifen');
    return casa;
  }));
  $('palavra-oculta').setAttribute('aria-label', resultado ? `Palavra: ${palavra}` : 'Palavra secreta');
}

function desenharTeclado({ letras }, ativo) {
  const tentadas = new Map(letras.map(({ letra, acertou }) => [letra, acertou]));
  for (const botao of $('teclado').children) {
    const acertou = tentadas.get(botao.textContent);
    botao.disabled = !ativo || acertou !== undefined;
    botao.classList.toggle('acertou', acertou === true);
    botao.classList.toggle('errou', acertou === false);
  }
}

export function desenharSala(novaSala) {
  sala = novaSala;
  if (!sala) return;

  const { fase, papel, rodada } = sala;
  const adivinhando = fase === 'jogando' && papel === 'adivinhador';

  $('sala-nome').textContent = sala.nome;
  $('papel').textContent = papel === 'definidor' ? 'Você define a palavra' : 'Você adivinha';
  $('papel').className = `papel ${papel}`;
  $('placar').textContent = sala.placar.length === 2
    ? `${sala.placar[0].apelido} ${sala.placar[0].pontos} × ${sala.placar[1].pontos} ${sala.placar[1].apelido}`
    : 'Esperando adversário';

  const venceu = (rodada?.resultado === 'vitoria') === (papel === 'adivinhador');
  $('status').textContent = textoStatus();
  $('status').className = fase === 'fim' ? `status ${venceu ? 'vitoria' : 'derrota'}` : 'status';

  const erros = rodada?.erros ?? 0;
  for (const parte of document.querySelectorAll('.boneco')) {
    parte.classList.toggle('visivel', Number(parte.dataset.erro) <= erros);
  }

  const escolhendo = fase === 'definindo' && papel === 'definidor';
  if (escolhendo && $('form-palavra').hidden) $('form-palavra').reset();
  $('form-palavra').hidden = !escolhendo;
  $('rodada').hidden = !rodada;
  if (!rodada) return;

  desenharPalavra(rodada);
  $('dica-texto').textContent = rodada.dica ? `Dica: ${rodada.dica}` : '';
  $('dica-texto').hidden = !rodada.dica;
  $('contador-erros').textContent = `Erros: ${rodada.erros} de ${rodada.maxErros}`;
  desenharTeclado(rodada, adivinhando);
  $('form-arriscar').hidden = !adivinhando;
  $('btn-nova-rodada').hidden = fase !== 'fim';
}

function tentarLetra(letra) {
  tentar(() => api.tentarLetra(letra));
}

export function iniciarSala() {
  for (const letra of ALFABETO) {
    const botao = document.createElement('button');
    botao.type = 'button';
    botao.textContent = letra;
    botao.addEventListener('click', () => tentarLetra(letra));
    $('teclado').append(botao);
  }

  $('form-palavra').addEventListener('submit', (evento) => {
    evento.preventDefault();
    tentar(() => api.definirPalavra($('palavra').value, $('dica').value));
  });

  $('form-arriscar').addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (await tentar(() => api.arriscarPalavra($('palpite').value))) $('form-arriscar').reset();
  });

  $('btn-sair').addEventListener('click', () => tentar(api.sairSala));
  $('btn-nova-rodada').addEventListener('click', () => tentar(api.novaRodada));

  document.addEventListener('keydown', (evento) => {
    const digitando = evento.target instanceof HTMLInputElement;
    const modificador = evento.ctrlKey || evento.metaKey || evento.altKey;
    if (digitando || modificador || !/^[a-z]$/i.test(evento.key)) return;
    if (sala?.fase === 'jogando' && sala.papel === 'adivinhador') {
      const letra = evento.key.toUpperCase();
      if (!sala.rodada.letras.some((l) => l.letra === letra)) tentarLetra(letra);
    }
  });
}
