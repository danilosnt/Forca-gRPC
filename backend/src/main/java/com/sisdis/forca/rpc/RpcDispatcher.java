package com.sisdis.forca.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeleton do servidor: interpreta mensagens JSON-RPC 2.0, encontra o método
 * registrado, converte os parâmetros e monta a resposta.
 *
 * <p>Os métodos são registrados antes de o servidor iniciar. Depois disso o
 * mapa só é lido, por isso pode ser usado por várias threads sem trava.
 *
 * @param <C> contexto da chamada, por exemplo a conexão do cliente
 */
public final class RpcDispatcher<C> {
    private static final Logger LOG = LoggerFactory.getLogger(RpcDispatcher.class);

    private final ObjectMapper json;
    private final Map<String, BiFunction<C, JsonNode, Object>> metodos = new HashMap<>();

    public RpcDispatcher(ObjectMapper json) {
        this.json = json;
    }

    public <P> void registrar(String nome, Class<P> tipoParametros, BiFunction<C, P, Object> metodo) {
        metodos.put(nome, (contexto, params) -> metodo.apply(contexto, converter(params, tipoParametros)));
    }

    public void registrar(String nome, Function<C, Object> metodo) {
        metodos.put(nome, (contexto, params) -> metodo.apply(contexto));
    }

    /** Processa uma mensagem e devolve a resposta, que não existe para notificações. */
    public Optional<String> processar(String texto, C contexto) {
        JsonNode mensagem;
        try {
            mensagem = json.readTree(texto);
        } catch (JsonProcessingException e) {
            return Optional.of(erro(NullNode.instance, CodigoErro.PARSE, "JSON inválido."));
        }

        if (!requisicaoValida(mensagem)) {
            JsonNode id = mensagem != null && mensagem.has("id") ? mensagem.get("id") : NullNode.instance;
            return Optional.of(erro(id, CodigoErro.REQUISICAO_INVALIDA, "Requisição inválida."));
        }

        // Sem "id" é notificação: executa, mas o protocolo proíbe responder.
        boolean notificacao = !mensagem.has("id");
        JsonNode id = mensagem.path("id");
        String nome = mensagem.get("method").asText();

        var metodo = metodos.get(nome);
        if (metodo == null) {
            return notificacao
                    ? Optional.empty()
                    : Optional.of(erro(id, CodigoErro.METODO_NAO_ENCONTRADO, "Método não encontrado: " + nome));
        }

        try {
            Object resultado = metodo.apply(contexto, parametros(mensagem));
            return notificacao ? Optional.empty() : Optional.of(sucesso(id, resultado));
        } catch (RpcException e) {
            return notificacao ? Optional.empty() : Optional.of(erro(id, e.codigo(), e.getMessage()));
        } catch (RuntimeException e) {
            LOG.error("Erro em {}", nome, e);
            return notificacao
                    ? Optional.empty()
                    : Optional.of(erro(id, CodigoErro.INTERNO, "Erro interno do servidor."));
        }
    }

    private static boolean requisicaoValida(JsonNode mensagem) {
        return mensagem != null
                && mensagem.isObject()
                && "2.0".equals(mensagem.path("jsonrpc").asText())
                && mensagem.path("method").isTextual();
    }

    private JsonNode parametros(JsonNode mensagem) {
        JsonNode params = mensagem.get("params");
        return params == null || params.isNull() ? json.createObjectNode() : params;
    }

    private <P> P converter(JsonNode params, Class<P> tipo) {
        try {
            return json.treeToValue(params, tipo);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new RpcException(CodigoErro.PARAMETROS_INVALIDOS, "Parâmetros inválidos.");
        }
    }

    private String sucesso(JsonNode id, Object resultado) {
        ObjectNode resposta = json.createObjectNode().put("jsonrpc", "2.0");
        resposta.set("id", id);
        resposta.set("result", json.valueToTree(resultado));
        return resposta.toString();
    }

    private String erro(JsonNode id, CodigoErro codigo, String mensagem) {
        ObjectNode resposta = json.createObjectNode().put("jsonrpc", "2.0");
        resposta.set("id", id);
        resposta.putObject("error").put("code", codigo.valor()).put("message", mensagem);
        return resposta.toString();
    }
}
