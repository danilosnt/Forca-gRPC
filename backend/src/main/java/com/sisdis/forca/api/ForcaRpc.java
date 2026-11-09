package com.sisdis.forca.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sisdis.forca.dominio.Jogador;
import com.sisdis.forca.dominio.Lobby;
import com.sisdis.forca.dominio.RegraJogoException;
import com.sisdis.forca.dominio.ResultadoJogada;
import com.sisdis.forca.rpc.CodigoErro;
import com.sisdis.forca.rpc.RpcDispatcher;
import com.sisdis.forca.rpc.RpcException;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Interface remota do servidor: registra os métodos que o cliente pode chamar.
 *
 * <p>O jogador vem da conexão, nunca dos parâmetros: um cliente não finge ser outro.
 */
final class ForcaRpc {
    private final Lobby lobby;
    private final RpcDispatcher<Conexao> dispatcher;

    private ForcaRpc(Lobby lobby, ObjectMapper json) {
        this.lobby = lobby;
        this.dispatcher = new RpcDispatcher<>(json);
    }

    static RpcDispatcher<Conexao> criarDispatcher(Lobby lobby, ObjectMapper json) {
        ForcaRpc rpc = new ForcaRpc(lobby, json);
        rpc.registrarMetodos();
        return rpc.dispatcher;
    }

    private void registrarMetodos() {
        dispatcher.registrar("jogador.entrar", Contrato.Entrar.class, comRegras(this::entrar));

        registrar("sala.criar", Contrato.CriarSala.class,
                (j, p) -> new Contrato.SalaCriada(lobby.criarSala(j, p.nome())));
        registrar("sala.entrar", Contrato.EntrarSala.class, (j, p) -> {
            lobby.entrarSala(j, p.id());
            return null;
        });
        registrar("sala.sair", j -> {
            lobby.sairSala(j);
            return null;
        });

        registrar("partida.definirPalavra", Contrato.DefinirPalavra.class, (j, p) -> {
            lobby.definirPalavra(j, p.palavra(), p.dica());
            return null;
        });
        registrar("partida.tentarLetra", Contrato.TentarLetra.class,
                (j, p) -> resultado(lobby.tentarLetra(j, p.letra())));
        registrar("partida.arriscarPalavra", Contrato.ArriscarPalavra.class,
                (j, p) -> resultado(lobby.arriscarPalavra(j, p.palavra())));
        registrar("partida.novaRodada", j -> {
            lobby.novaRodada(j);
            return null;
        });
    }

    private Object entrar(Conexao conexao, Contrato.Entrar params) {
        if (conexao.jogador() != null) {
            throw new RegraJogoException("Você já entrou no jogo.");
        }
        Jogador jogador = lobby.entrar(conexao, params.apelido());
        conexao.identificar(jogador);
        return new Contrato.JogadorEntrou(jogador.apelido());
    }

    private <P> void registrar(String nome, Class<P> tipo, BiFunction<Jogador, P, Object> metodo) {
        dispatcher.registrar(nome, tipo, comRegras((conexao, p) -> metodo.apply(exigirJogador(conexao), p)));
    }

    private void registrar(String nome, Function<Jogador, Object> metodo) {
        BiFunction<Conexao, Void, Object> comJogador = (conexao, nada) -> metodo.apply(exigirJogador(conexao));
        dispatcher.registrar(nome, conexao -> comRegras(comJogador).apply(conexao, null));
    }

    private static Jogador exigirJogador(Conexao conexao) {
        Jogador jogador = conexao.jogador();
        if (jogador == null) {
            throw new RegraJogoException("Informe seu apelido primeiro.");
        }
        return jogador;
    }

    private static <P> BiFunction<Conexao, P, Object> comRegras(BiFunction<Conexao, P, Object> metodo) {
        return (conexao, params) -> {
            try {
                return metodo.apply(conexao, params);
            } catch (RegraJogoException e) {
                throw new RpcException(CodigoErro.JOGO, e.getMessage());
            }
        };
    }

    private static Contrato.ResultadoTentativa resultado(ResultadoJogada resultado) {
        return new Contrato.ResultadoTentativa(resultado.name().toLowerCase(Locale.ROOT));
    }
}
