package com.sisdis.forca.dominio;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Jogadores conectados e salas abertas.
 *
 * <p>Concorrência: as coleções são concorrentes e cada {@link Sala} protege o
 * próprio estado. A ordem das travas é sempre publicação do lobby, depois sala,
 * e o lobby só publica depois de soltar a trava da sala, o que evita deadlock.
 */
public final class Lobby {
    private final Set<Jogador> jogadores = ConcurrentHashMap.newKeySet();
    private final Map<String, Sala> salas = new ConcurrentHashMap<>();
    private final Set<String> nomesEmUso = ConcurrentHashMap.newKeySet();
    private final AtomicInteger proximoNumero = new AtomicInteger(1);

    /** Sem ela, uma lista antiga poderia chegar ao cliente depois de uma mais nova. */
    private final Object travaPublicacao = new Object();

    public Jogador entrar(ClienteRemoto cliente, String apelidoBruto) {
        Jogador jogador = new Jogador(Validacao.texto(apelidoBruto, "O apelido", 1, 20), cliente);
        synchronized (travaPublicacao) {
            jogadores.add(jogador);
            cliente.lobbyAtualizado(resumos());
        }
        return jogador;
    }

    public String criarSala(Jogador jogador, String nomeBruto) {
        exigirForaDeSala(jogador);
        String nome = Validacao.texto(nomeBruto, "O nome da sala", 1, 30);
        if (!nomesEmUso.add(chaveDoNome(nome))) {
            throw new RegraJogoException("Já existe uma sala com esse nome.");
        }
        Sala sala = new Sala(proximoNumero.getAndIncrement(), nome, jogador);
        salas.put(sala.id(), sala);
        jogador.entrarEm(sala);
        sala.publicar();
        publicarLobby();
        return sala.id();
    }

    public void entrarSala(Jogador jogador, String id) {
        exigirForaDeSala(jogador);
        Sala sala = salas.get(id);
        if (sala == null) {
            throw new RegraJogoException("Essa sala não existe mais.");
        }
        sala.adicionar(jogador);
        publicarLobby();
    }

    public void sairSala(Jogador jogador) {
        remover(jogador, exigirSala(jogador));
        jogador.cliente().salaAtualizada(null);
    }

    public void desconectar(Jogador jogador) {
        jogadores.remove(jogador);
        Sala sala = jogador.sala();
        if (sala != null) {
            remover(jogador, sala);
        }
    }

    public void definirPalavra(Jogador jogador, String palavraBruta, String dicaBruta) {
        Sala sala = exigirSala(jogador);
        String palavra = Validacao.texto(palavraBruta, "A palavra", 1, 40);
        String dica = dicaBruta == null ? "" : Validacao.texto(dicaBruta, "A dica", 0, 60);
        sala.definirPalavra(jogador, palavra, dica);
    }

    public ResultadoJogada tentarLetra(Jogador jogador, String letra) {
        Sala sala = exigirSala(jogador);
        return sala.tentarLetra(jogador, Validacao.texto(letra, "A letra", 1, 2));
    }

    public ResultadoJogada arriscarPalavra(Jogador jogador, String palpite) {
        Sala sala = exigirSala(jogador);
        return sala.arriscar(jogador, Validacao.texto(palpite, "O palpite", 1, 40));
    }

    public void novaRodada(Jogador jogador) {
        exigirSala(jogador).novaRodada();
    }

    private void remover(Jogador jogador, Sala sala) {
        if (sala.remover(jogador)) {
            salas.remove(sala.id());
            nomesEmUso.remove(chaveDoNome(sala.nome()));
        }
        publicarLobby();
    }

    private void publicarLobby() {
        synchronized (travaPublicacao) {
            List<ResumoSala> resumos = resumos();
            for (Jogador jogador : jogadores) {
                if (jogador.sala() == null) {
                    jogador.cliente().lobbyAtualizado(resumos);
                }
            }
        }
    }

    private List<ResumoSala> resumos() {
        return salas.values().stream()
                .sorted(Comparator.comparingInt(Sala::numero))
                .map(Sala::resumo)
                .toList();
    }

    private static String chaveDoNome(String nome) {
        return nome.toLowerCase(Locale.ROOT);
    }

    private static void exigirForaDeSala(Jogador jogador) {
        if (jogador.sala() != null) {
            throw new RegraJogoException("Saia da sala atual primeiro.");
        }
    }

    private static Sala exigirSala(Jogador jogador) {
        Sala sala = jogador.sala();
        if (sala == null) {
            throw new RegraJogoException("Você não está em uma sala.");
        }
        return sala;
    }
}
