package com.sisdis.forca.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** Dispara threads reais contra o mesmo objeto, sem a fila de uma conexão no meio. */
class SalaConcorrenciaTest {
    private static final int THREADS = 32;

    private static final class ClienteFalso implements ClienteRemoto {
        volatile VisaoSala sala;
        volatile List<ResumoSala> salas = List.of();

        @Override
        public void lobbyAtualizado(List<ResumoSala> salas) {
            this.salas = salas;
        }

        @Override
        public void salaAtualizada(VisaoSala sala) {
            this.sala = sala;
        }

        @Override
        public void aviso(String mensagem) {
        }
    }

    /** Solta todas as tarefas no mesmo instante e devolve os resultados, com null nas que falharam. */
    private static <T> List<T> aoMesmoTempo(List<Callable<T>> tarefas) throws InterruptedException {
        ExecutorService threads = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<T>> futuros = new ArrayList<>();
        for (Callable<T> tarefa : tarefas) {
            futuros.add(threads.submit(() -> {
                largada.await();
                return tarefa.call();
            }));
        }
        largada.countDown();
        List<T> resultados = new ArrayList<>();
        for (Future<T> futuro : futuros) {
            try {
                resultados.add(futuro.get());
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof RegraJogoException)) {
                    throw new AssertionError(e.getCause());
                }
                resultados.add(null);
            }
        }
        threads.shutdown();
        return resultados;
    }

    private record Partida(Lobby lobby, Jogador definidor, Jogador adivinhador, ClienteFalso cliente) {
    }

    private static Partida partida(String palavra) {
        Lobby lobby = new Lobby();
        ClienteFalso cliente = new ClienteFalso();
        Jogador ana = lobby.entrar(cliente, "Ana");
        Jogador bia = lobby.entrar(new ClienteFalso(), "Bia");
        String id = lobby.criarSala(ana, "sala");
        lobby.definirPalavra(ana, palavra, null);
        lobby.entrarSala(bia, id);
        return new Partida(lobby, ana, bia, cliente);
    }

    @RepeatedTest(20)
    void soUmJogadorConsegueAUltimaVaga() throws InterruptedException {
        Lobby lobby = new Lobby();
        Jogador dono = lobby.entrar(new ClienteFalso(), "Dono");
        String id = lobby.criarSala(dono, "disputa");

        List<Callable<Boolean>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Jogador rival = lobby.entrar(new ClienteFalso(), "Rival " + i);
            tarefas.add(() -> {
                lobby.entrarSala(rival, id);
                return true;
            });
        }

        long entraram = aoMesmoTempo(tarefas).stream().filter(r -> r != null).count();
        assertEquals(1, entraram);
    }

    @RepeatedTest(20)
    void mesmaLetraEmParaleloContaUmaVezSo() throws InterruptedException {
        Partida p = partida("gato");
        List<Callable<ResultadoJogada>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tarefas.add(() -> p.lobby().tentarLetra(p.adivinhador(), "Z"));
        }

        List<ResultadoJogada> resultados = aoMesmoTempo(tarefas);

        assertEquals(1, resultados.stream().filter(r -> r == ResultadoJogada.ERRO).count());
        assertEquals(THREADS - 1, resultados.stream().filter(r -> r == ResultadoJogada.REPETIDA).count());
        assertEquals(1, p.cliente().sala.rodada().erros());
    }

    @RepeatedTest(20)
    void palpitesEmParaleloParamNoSextoErroEPontuamUmaVez() throws InterruptedException {
        Partida p = partida("gato");
        List<Callable<ResultadoJogada>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tarefas.add(() -> p.lobby().arriscarPalavra(p.adivinhador(), "errado"));
        }

        long aceitos = aoMesmoTempo(tarefas).stream().filter(r -> r != null).count();

        assertEquals(Forca.MAX_ERROS, aceitos);
        assertEquals("fim", p.cliente().sala.fase());
        assertEquals(1, p.cliente().sala.placar().stream().mapToInt(VisaoSala.Pontuacao::pontos).sum());
    }

    @RepeatedTest(20)
    void novaRodadaEmParaleloInverteOsPapeisUmaVezSo() throws InterruptedException {
        Partida p = partida("gato");
        p.lobby().arriscarPalavra(p.adivinhador(), "gato");

        aoMesmoTempo(List.<Callable<Boolean>>of(
                () -> {
                    p.lobby().novaRodada(p.definidor());
                    return true;
                },
                () -> {
                    p.lobby().novaRodada(p.adivinhador());
                    return true;
                }));

        assertEquals("definindo", p.cliente().sala.fase());
        assertEquals("adivinhador", p.cliente().sala.papel());
    }

    @RepeatedTest(20)
    void entrarEnquantoOUltimoSaiNaoDeixaSalaFantasma() throws InterruptedException {
        Lobby lobby = new Lobby();
        ClienteFalso observador = new ClienteFalso();
        lobby.entrar(observador, "Observador");
        Jogador dono = lobby.entrar(new ClienteFalso(), "Dono");
        Jogador visita = lobby.entrar(new ClienteFalso(), "Visita");
        String id = lobby.criarSala(dono, "corrida");

        List<Boolean> resultados = aoMesmoTempo(List.<Callable<Boolean>>of(
                () -> {
                    lobby.sairSala(dono);
                    return true;
                },
                () -> {
                    lobby.entrarSala(visita, id);
                    return true;
                }));

        assertTrue(resultados.get(0));
        boolean visitaEntrou = resultados.get(1) != null;
        assertEquals(visitaEntrou ? 1 : 0, observador.salas.size());
    }

    @Test
    void salasDiferentesFuncionamEmParalelo() throws InterruptedException {
        Lobby lobby = new Lobby();
        ClienteFalso observador = new ClienteFalso();
        lobby.entrar(observador, "Observador");

        List<Callable<Boolean>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String nome = "sala " + i;
            Jogador ana = lobby.entrar(new ClienteFalso(), "Ana " + i);
            Jogador bia = lobby.entrar(new ClienteFalso(), "Bia " + i);
            tarefas.add(() -> {
                String id = lobby.criarSala(ana, nome);
                lobby.definirPalavra(ana, "gato", null);
                lobby.entrarSala(bia, id);
                lobby.arriscarPalavra(bia, "gato");
                return true;
            });
        }

        assertTrue(aoMesmoTempo(tarefas).stream().allMatch(r -> r != null));
        assertEquals(THREADS, observador.salas.size());
    }
}
