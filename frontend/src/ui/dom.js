export const $ = (id) => document.getElementById(id);

let temporizadorAviso;

export function mostrarTela(id) {
  for (const tela of document.querySelectorAll('.tela')) tela.hidden = tela.id !== id;
}

export function avisar(mensagem) {
  $('aviso').textContent = mensagem;
  $('aviso').hidden = false;
  clearTimeout(temporizadorAviso);
  temporizadorAviso = setTimeout(() => { $('aviso').hidden = true; }, 4500);
}

// Executa a chamada remota e mostra o erro ao jogador. Devolve true se deu certo.
export async function tentar(chamada) {
  try {
    await chamada();
    return true;
  } catch (erro) {
    avisar(erro.message);
    return false;
  }
}
