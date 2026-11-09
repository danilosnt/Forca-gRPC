package com.sisdis.forca.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sisdis.forca.dominio.Lobby;
import com.sisdis.forca.rpc.Json;
import com.sisdis.forca.rpc.RpcDispatcher;
import io.javalin.Javalin;
import io.javalin.websocket.WsConfig;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Servidor WebSocket: cada conexão é atendida pelas threads do Jetty, em paralelo. */
public final class ServidorRpc {
    private static final long INTERVALO_PING_SEGUNDOS = 20;
    private static final Duration TEMPO_MAXIMO_OCIOSO = Duration.ofSeconds(60);
    private static final long TAMANHO_MAXIMO_MENSAGEM = 4096;

    private final Lobby lobby = new Lobby();
    private final ObjectMapper json = Json.criar();
    private final RpcDispatcher<Conexao> dispatcher = ForcaRpc.criarDispatcher(lobby, json);
    private final Map<String, Conexao> conexoes = new ConcurrentHashMap<>();
    private final Javalin app;

    public ServidorRpc() {
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jetty.modifyWebSocketServletFactory(fabrica -> {
                fabrica.setIdleTimeout(TEMPO_MAXIMO_OCIOSO);
                fabrica.setMaxTextMessageSize(TAMANHO_MAXIMO_MENSAGEM);
            });
        });
        app.ws("/rpc", this::configurar);
    }

    public int iniciar(int porta) {
        app.start("0.0.0.0", porta);
        return app.port();
    }

    public void parar() {
        app.stop();
    }

    private void configurar(WsConfig ws) {
        ws.onConnect(ctx -> {
            // O ping mantém a conexão ativa; quem caiu não responde e expira.
            ctx.enableAutomaticPings(INTERVALO_PING_SEGUNDOS, TimeUnit.SECONDS);
            conexoes.put(ctx.sessionId(), new Conexao(ctx, json));
        });
        ws.onMessage(ctx -> {
            Conexao conexao = conexoes.get(ctx.sessionId());
            dispatcher.processar(ctx.message(), conexao).ifPresent(conexao::enviar);
        });
        ws.onClose(ctx -> {
            Conexao conexao = conexoes.remove(ctx.sessionId());
            if (conexao == null) {
                return;
            }
            conexao.fechar();
            if (conexao.jogador() != null) {
                lobby.desconectar(conexao.jogador());
            }
        });
    }
}
