package com.sisdis.forca;

import com.sisdis.forca.api.ServidorRpc;

public final class Aplicacao {
    private static final int PORTA_PADRAO = 8080;

    private Aplicacao() {
    }

    public static void main(String[] args) {
        String portaConfigurada = System.getenv("PORT");
        int porta = portaConfigurada == null ? PORTA_PADRAO : Integer.parseInt(portaConfigurada);

        new ServidorRpc().iniciar(porta);
        System.out.printf("API da forca no ar: ws://0.0.0.0:%d/rpc%n", porta);
    }
}
