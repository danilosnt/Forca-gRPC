package com.sisdis.forca.dominio;

/** Uma regra do jogo foi violada. */
public class RegraJogoException extends RuntimeException {
    public RegraJogoException(String mensagem) {
        super(mensagem);
    }
}
