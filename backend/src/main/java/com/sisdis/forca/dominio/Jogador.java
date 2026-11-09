package com.sisdis.forca.dominio;

/** Jogador conectado. Só a thread da sua própria conexão troca a sala dele. */
public final class Jogador {
    private final String apelido;
    private final ClienteRemoto cliente;
    private volatile Sala sala;

    Jogador(String apelido, ClienteRemoto cliente) {
        this.apelido = apelido;
        this.cliente = cliente;
    }

    public String apelido() {
        return apelido;
    }

    ClienteRemoto cliente() {
        return cliente;
    }

    Sala sala() {
        return sala;
    }

    void entrarEm(Sala sala) {
        this.sala = sala;
    }

    void sairDaSala() {
        this.sala = null;
    }
}
