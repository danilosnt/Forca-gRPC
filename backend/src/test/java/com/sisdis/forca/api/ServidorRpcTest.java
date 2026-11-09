package com.sisdis.forca.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Testes ponta a ponta: servidor real e clientes WebSocket reais. */
class ServidorRpcTest {
    private static final AtomicInteger CONTADOR_SALAS = new AtomicInteger();
    private static final List<ClienteTeste> CLIENTES = new ArrayList<>();

    private static ServidorRpc servidor;
    private static String url;

    private record Dupla(ClienteTeste a, ClienteTeste b, String id) {
    }

    @BeforeAll
    static void iniciar() {
        servidor = new ServidorRpc();
        url = "ws://127.0.0.1:" + servidor.iniciar(0) + "/rpc";
    }

    @AfterAll
    static void parar() {
        CLIENTES.forEach(ClienteTeste::abortar);
        servidor.parar();
    }

    private static ClienteTeste conectar() {
        ClienteTeste cliente = new ClienteTeste(url);
        CLIENTES.add(cliente);
        return cliente;
    }

    private static ClienteTeste conectar(String apelido) throws Exception {
        ClienteTeste cliente = conectar();
        cliente.resultado("jogador.entrar", Map.of("apelido", apelido));
        return cliente;
    }

    private static Dupla salaComDois(String palavra) throws Exception {
        ClienteTeste a = conectar("Ana");
        ClienteTeste b = conectar("Bia");
        String nome = "sala " + CONTADOR_SALAS.incrementAndGet();
        String id = a.resultado("sala.criar", Map.of("nome", nome)).get("id").asText();
        a.resultado("partida.definirPalavra", Map.of("palavra", palavra, "dica", "bicho"));
        b.resultado("sala.entrar", Map.of("id", id));
        return new Dupla(a, b, id);
    }

    private static void aguardar(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("condição não atingida");
            }
            Thread.sleep(5);
        }
    }

    private static String fase(ClienteTeste cliente) {
        return cliente.sala == null ? "" : cliente.sala.get("fase").asText();
    }

    private static int codigoErro(JsonNode resposta) {
        return resposta.get("error").get("code").asInt();
    }

    @Test
    void quemEntraPrimeiroDefineEQuemEntraDepoisAdivinha() throws Exception {
        Dupla d = salaComDois("gato");
        aguardar(() -> fase(d.a()).equals("jogando") && fase(d.b()).equals("jogando"));
        assertEquals("definidor", d.a().sala.get("papel").asText());
        assertEquals("adivinhador", d.b().sala.get("papel").asText());
        assertEquals("bicho", d.b().sala.get("rodada").get("dica").asText());
    }

    @Test
    void aPalavraNaoChegaAQuemAdivinhaAntesDoFim() throws Exception {
        Dupla d = salaComDois("segredo");
        d.b().resultado("partida.tentarLetra", Map.of("letra", "S"));
        d.b().resultado("partida.tentarLetra", Map.of("letra", "Z"));
        assertFalse(d.b().recebidas.stream().anyMatch(m -> m.contains("SEGREDO")));

        d.b().resultado("partida.arriscarPalavra", Map.of("palavra", "segredo"));
        aguardar(() -> fase(d.b()).equals("fim"));
        assertEquals("SEGREDO", d.b().sala.get("rodada").get("palavra").asText());
    }

    @Test
    void papeisSaoProtegidosPeloServidor() throws Exception {
        Dupla d = salaComDois("gato");
        assertEquals(-32000, codigoErro(d.a().chamar("partida.tentarLetra", Map.of("letra", "G"))));
        assertEquals(-32000, codigoErro(d.b().chamar("partida.definirPalavra", Map.of("palavra", "outra"))));
    }

    @Test
    void terceiroJogadorNaoEntraEmSalaCheia() throws Exception {
        Dupla d = salaComDois("gato");
        JsonNode resposta = conectar("Caio").chamar("sala.entrar", Map.of("id", d.id()));
        assertTrue(resposta.get("error").get("message").asText().contains("cheia"));
    }

    @Test
    void fimDeRodadaPontuaENovaRodadaInverteOsPapeis() throws Exception {
        Dupla d = salaComDois("gato");
        d.b().resultado("partida.arriscarPalavra", Map.of("palavra", "GATO"));
        aguardar(() -> fase(d.a()).equals("fim"));
        assertEquals(0, d.a().sala.get("placar").get(0).get("pontos").asInt());
        assertEquals(1, d.a().sala.get("placar").get(1).get("pontos").asInt());

        d.a().resultado("partida.novaRodada", Map.of());
        aguardar(() -> fase(d.a()).equals("definindo") && fase(d.b()).equals("definindo"));
        assertEquals("adivinhador", d.a().sala.get("papel").asText());
        assertEquals("definidor", d.b().sala.get("papel").asText());
    }

    @Test
    void seUmCaiOOutroEsperaESalaVaziaSome() throws Exception {
        Dupla d = salaComDois("gato");
        aguardar(() -> fase(d.a()).equals("jogando"));
        d.a().fechar();
        aguardar(() -> d.b().avisos.size() == 1 && fase(d.b()).equals("definindo"));
        assertEquals("definidor", d.b().sala.get("papel").asText());
        assertEquals(1, d.b().sala.get("placar").size());

        ClienteTeste observador = conectar("Caio");
        d.b().resultado("sala.sair", Map.of());
        aguardar(() -> d.b().sala.isNull() && !temSala(observador, d.id()));
    }

    @Test
    void oLobbyEAvisadoDeSalasNovas() throws Exception {
        ClienteTeste a = conectar("Ana");
        ClienteTeste b = conectar("Bia");
        String nome = "avisos " + CONTADOR_SALAS.incrementAndGet();
        String id = a.resultado("sala.criar", Map.of("nome", nome)).get("id").asText();
        aguardar(() -> temSala(b, id));
    }

    @Test
    void quedaEmMassaNaoTravaOServidor() throws Exception {
        for (int rodada = 0; rodada < 10; rodada++) {
            List<ClienteTeste> caindo = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                Dupla d = salaComDois("gato");
                caindo.add(d.a());
                caindo.add(d.b());
            }
            caindo.forEach(ClienteTeste::abortar);

            // Se o servidor travar, estas chamadas estouram o tempo limite.
            ClienteTeste novo = conectar("Depois");
            ClienteTeste observador = conectar("Observador");
            String nome = "depois " + CONTADOR_SALAS.incrementAndGet();
            String id = novo.resultado("sala.criar", Map.of("nome", nome)).get("id").asText();
            aguardar(() -> temSala(observador, id));
        }
    }

    @Test
    void errosDoProtocoloSeguemOJsonRpc() throws Exception {
        assertEquals(-32700, codigoErroBruto("{quebrado"));
        assertEquals(-32600, codigoErroBruto("{\"id\":1}"));
        assertEquals(-32601, codigoErroBruto("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"constructor\"}"));

        ClienteTeste cliente = conectar();
        assertEquals(-32000, codigoErro(cliente.chamar("sala.criar", Map.of("nome", "x"))));
        assertEquals(-32602, codigoErro(cliente.chamar("jogador.entrar", Map.of("apelido", 7))));
        assertEquals(-32602, codigoErro(cliente.chamar("jogador.entrar", Map.of())));
    }

    private static int codigoErroBruto(String texto) throws Exception {
        ClienteTeste cliente = conectar();
        cliente.enviar(texto);
        return codigoErro(cliente.proximaSemId.get(3, TimeUnit.SECONDS));
    }

    private static boolean temSala(ClienteTeste cliente, String id) {
        if (cliente.salas == null) {
            return false;
        }
        for (JsonNode sala : cliente.salas) {
            if (sala.get("id").asText().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
