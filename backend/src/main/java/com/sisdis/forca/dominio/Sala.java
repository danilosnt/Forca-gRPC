package com.sisdis.forca.dominio;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Uma sala de 2 jogadores.
 *
 * <p>Concorrência: cada sala tem sua própria trava, e todo acesso ao estado
 * passa por {@link #protegido}. Chamadas na mesma sala são executadas uma por
 * vez; salas diferentes rodam em paralelo. As notificações entram na fila de
 * saída de cada cliente com a trava presa, para que cheguem na ordem em que os
 * estados aconteceram. {@link ClienteRemoto} nunca faz I/O na thread que chama.
 */
final class Sala {
    private final ReentrantLock trava = new ReentrantLock();

    private final int numero;
    private final String nome;
    private final Map<Jogador, Integer> placar = new HashMap<>();
    private Jogador definidor;
    private Jogador adivinhador;
    private Forca rodada;
    private boolean encerrada;

    Sala(int numero, String nome, Jogador criador) {
        this.numero = numero;
        this.nome = nome;
        this.definidor = criador;
    }

    int numero() {
        return numero;
    }

    String id() {
        return "sala-" + numero;
    }

    String nome() {
        return nome;
    }

    void publicar() {
        protegido(() -> {
            jogadores().forEach(j -> j.cliente().salaAtualizada(visaoPara(j)));
            return null;
        });
    }

    /** Falha se a sala estiver cheia ou se o último jogador acabou de encerrá-la. */
    void adicionar(Jogador jogador) {
        protegido(() -> {
            if (encerrada) {
                throw new RegraJogoException("Essa sala não existe mais.");
            }
            if (adivinhador != null) {
                throw new RegraJogoException("Essa sala está cheia.");
            }
            adivinhador = jogador;
            jogador.entrarEm(this);
            publicar();
            return null;
        });
    }

    /** Devolve true se a sala ficou vazia e deve ser apagada. */
    boolean remover(Jogador jogador) {
        return protegido(() -> {
            Jogador restante = jogador == definidor ? adivinhador : definidor;
            jogador.sairDaSala();
            if (restante == null) {
                encerrada = true;
                return true;
            }
            // Quem fica vira definidor e espera um novo adversário, com placar zerado.
            definidor = restante;
            adivinhador = null;
            rodada = null;
            placar.clear();
            restante.cliente().aviso(
                    jogador.apelido() + " saiu da sala. Escolha uma palavra para o próximo adversário.");
            publicar();
            return false;
        });
    }

    void definirPalavra(Jogador jogador, String palavra, String dica) {
        protegido(() -> {
            if (jogador != definidor) {
                throw new RegraJogoException("Só quem define pode escolher a palavra.");
            }
            if (rodada != null) {
                throw new RegraJogoException("A palavra desta rodada já foi definida.");
            }
            rodada = new Forca(palavra, dica);
            publicar();
            return null;
        });
    }

    ResultadoJogada tentarLetra(Jogador jogador, String letra) {
        return jogada(jogador, () -> rodada.tentarLetra(letra));
    }

    ResultadoJogada arriscar(Jogador jogador, String palpite) {
        return jogada(jogador, () -> rodada.arriscar(palpite));
    }

    void novaRodada() {
        protegido(() -> {
            Fase fase = fase();
            if (fase == Fase.DEFININDO) {
                return null; // o outro jogador clicou primeiro
            }
            if (fase != Fase.FIM) {
                throw new RegraJogoException("A rodada ainda não terminou.");
            }
            Jogador anterior = definidor;
            definidor = adivinhador;
            adivinhador = anterior;
            rodada = null;
            publicar();
            return null;
        });
    }

    ResumoSala resumo() {
        return protegido(() -> new ResumoSala(
                id(), nome, jogadores().map(Jogador::apelido).toList(), adivinhador != null));
    }

    private ResultadoJogada jogada(Jogador jogador, Supplier<ResultadoJogada> acao) {
        return protegido(() -> {
            if (jogador != adivinhador) {
                throw new RegraJogoException("Só quem adivinha pode jogar.");
            }
            if (fase() != Fase.JOGANDO) {
                throw new RegraJogoException("Não há rodada em andamento.");
            }
            ResultadoJogada resultado = acao.get();
            if (rodada.terminou()) {
                Jogador vencedor = rodada.resultado() == ResultadoRodada.VITORIA ? adivinhador : definidor;
                placar.merge(vencedor, 1, Integer::sum);
            }
            publicar();
            return resultado;
        });
    }

    private <T> T protegido(Supplier<T> acao) {
        trava.lock();
        try {
            return acao.get();
        } finally {
            trava.unlock();
        }
    }

    private Stream<Jogador> jogadores() {
        return Stream.of(definidor, adivinhador).filter(java.util.Objects::nonNull);
    }

    private Fase fase() {
        if (rodada == null) {
            return Fase.DEFININDO;
        }
        if (rodada.terminou()) {
            return Fase.FIM;
        }
        return adivinhador == null ? Fase.AGUARDANDO : Fase.JOGANDO;
    }

    private String papelDe(Jogador jogador) {
        return jogador == definidor ? "definidor" : "adivinhador";
    }

    // A palavra só sai do servidor para quem a definiu ou depois do fim da rodada.
    private VisaoSala visaoPara(Jogador jogador) {
        List<VisaoSala.Pontuacao> pontuacoes = jogadores()
                .map(j -> new VisaoSala.Pontuacao(j.apelido(), papelDe(j), placar.getOrDefault(j, 0)))
                .toList();
        return new VisaoSala(id(), nome, minusculo(fase()), papelDe(jogador), pontuacoes, visaoRodada(jogador));
    }

    private VisaoSala.Rodada visaoRodada(Jogador jogador) {
        if (rodada == null) {
            return null;
        }
        boolean podeVerPalavra = jogador == definidor || rodada.terminou();
        return new VisaoSala.Rodada(
                rodada.mascara(),
                rodada.dica(),
                rodada.letras(),
                rodada.erros(),
                Forca.MAX_ERROS,
                rodada.terminou() ? minusculo(rodada.resultado()) : null,
                podeVerPalavra ? rodada.palavra() : null);
    }

    private static String minusculo(Enum<?> valor) {
        return valor.name().toLowerCase(Locale.ROOT);
    }
}
