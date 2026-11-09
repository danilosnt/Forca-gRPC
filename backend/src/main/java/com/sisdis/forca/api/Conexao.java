package com.sisdis.forca.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sisdis.forca.dominio.ClienteRemoto;
import com.sisdis.forca.dominio.Jogador;
import com.sisdis.forca.dominio.ResumoSala;
import com.sisdis.forca.dominio.VisaoSala;
import io.javalin.websocket.WsContext;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uma conexão WebSocket. Funciona como stub do cliente no servidor: transforma
 * as chamadas de {@link ClienteRemoto} em notificações JSON-RPC.
 */
final class Conexao implements ClienteRemoto {
    private static final Logger LOG = LoggerFactory.getLogger(Conexao.class);

    private record Notificacao(String jsonrpc, String method, Object params) {
    }

    private record Salas(List<ResumoSala> salas) {
    }

    private record Sala(VisaoSala sala) {
    }

    private record Aviso(String mensagem) {
    }

    private final WsContext contexto;
    private final ObjectMapper json;
    private final ExecutorService filaDeSaida =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private volatile Jogador jogador;

    Conexao(WsContext contexto, ObjectMapper json) {
        this.contexto = contexto;
        this.json = json;
    }

    Jogador jogador() {
        return jogador;
    }

    void identificar(Jogador jogador) {
        this.jogador = jogador;
    }

    @Override
    public void lobbyAtualizado(List<ResumoSala> salas) {
        notificar("lobby.atualizado", new Salas(salas));
    }

    @Override
    public void salaAtualizada(VisaoSala sala) {
        notificar("sala.atualizada", new Sala(sala));
    }

    @Override
    public void aviso(String mensagem) {
        notificar("sala.aviso", new Aviso(mensagem));
    }

    /**
     * Só coloca a mensagem na fila de saída, sem nenhum I/O nesta thread.
     *
     * <p>Quem chama pode estar com a trava de uma sala. Se o envio acontecesse aqui, uma
     * falha faria o Jetty rodar o onClose nesta mesma thread, com a trava presa, e isso
     * causa deadlock com a publicação do lobby. A fila também preserva a ordem das
     * mensagens e impede que um cliente lento segure a sala.
     */
    void enviar(String mensagem) {
        try {
            filaDeSaida.execute(() -> escrever(mensagem));
        } catch (RejectedExecutionException e) {
            LOG.debug("Mensagem descartada: a conexão já foi fechada");
        }
    }

    void fechar() {
        filaDeSaida.shutdown();
    }

    private void escrever(String mensagem) {
        try {
            if (contexto.session.isOpen()) {
                contexto.send(mensagem);
            }
        } catch (Exception e) { // o Javalin, em Kotlin, lança IOException sem declarar
            LOG.debug("Falha ao enviar: o cliente caiu e o onClose vai removê-lo", e);
        }
    }

    private void notificar(String metodo, Object params) {
        try {
            enviar(json.writeValueAsString(new Notificacao("2.0", metodo, params)));
        } catch (JsonProcessingException e) {
            LOG.error("Falha ao serializar {}", metodo, e);
        }
    }
}
