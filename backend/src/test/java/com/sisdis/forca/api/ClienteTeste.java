package com.sisdis.forca.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Cliente WebSocket real, usado para testar o servidor de ponta a ponta. */
final class ClienteTeste implements WebSocket.Listener {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<Integer, CompletableFuture<JsonNode>> pendentes = new ConcurrentHashMap<>();
    private final AtomicInteger proximoId = new AtomicInteger(1);
    private final StringBuilder parcial = new StringBuilder();
    private final WebSocket ws;

    final List<String> recebidas = new CopyOnWriteArrayList<>();
    final List<String> avisos = new CopyOnWriteArrayList<>();
    final CompletableFuture<JsonNode> proximaSemId = new CompletableFuture<>();
    volatile JsonNode sala;
    volatile JsonNode salas;

    ClienteTeste(String url) {
        ws = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(url), this).join();
    }

    JsonNode chamar(String metodo, Map<String, ?> params) throws Exception {
        int id = proximoId.getAndIncrement();
        CompletableFuture<JsonNode> resposta = new CompletableFuture<>();
        pendentes.put(id, resposta);
        enviar(JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", id, "method", metodo, "params", params)));
        return resposta.get(5, TimeUnit.SECONDS);
    }

    JsonNode resultado(String metodo, Map<String, ?> params) throws Exception {
        JsonNode resposta = chamar(metodo, params);
        if (resposta.has("error")) {
            throw new AssertionError(resposta.get("error").toString());
        }
        return resposta.get("result");
    }

    synchronized void enviar(String texto) {
        ws.sendText(texto, true).join();
    }

    void fechar() {
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
    }

    void abortar() {
        ws.abort();
    }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence dados, boolean ultimo) {
        parcial.append(dados);
        if (ultimo) {
            receber(parcial.toString());
            parcial.setLength(0);
        }
        socket.request(1);
        return null;
    }

    private void receber(String texto) {
        recebidas.add(texto);
        JsonNode msg;
        try {
            msg = JSON.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String metodo = msg.path("method").asText();
        switch (metodo) {
            case "sala.atualizada" -> sala = msg.get("params").get("sala");
            case "lobby.atualizado" -> salas = msg.get("params").get("salas");
            case "sala.aviso" -> avisos.add(msg.get("params").get("mensagem").asText());
            default -> {
                var pendente = pendentes.remove(msg.path("id").asInt(-1));
                if (pendente != null) {
                    pendente.complete(msg);
                } else {
                    proximaSemId.complete(msg);
                }
            }
        }
    }
}
