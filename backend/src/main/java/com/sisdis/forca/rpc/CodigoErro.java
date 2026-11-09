package com.sisdis.forca.rpc;

/** Códigos de erro do JSON-RPC 2.0. JOGO usa a faixa reservada para a aplicação. */
public enum CodigoErro {
    PARSE(-32700),
    REQUISICAO_INVALIDA(-32600),
    METODO_NAO_ENCONTRADO(-32601),
    PARAMETROS_INVALIDOS(-32602),
    INTERNO(-32603),
    JOGO(-32000);

    private final int valor;

    CodigoErro(int valor) {
        this.valor = valor;
    }

    public int valor() {
        return valor;
    }
}
