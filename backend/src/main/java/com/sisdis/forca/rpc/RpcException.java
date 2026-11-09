package com.sisdis.forca.rpc;

/** Falha de um método remoto, devolvida ao cliente no campo "error". */
public class RpcException extends RuntimeException {
    private final CodigoErro codigo;

    public RpcException(CodigoErro codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public CodigoErro codigo() {
        return codigo;
    }
}
