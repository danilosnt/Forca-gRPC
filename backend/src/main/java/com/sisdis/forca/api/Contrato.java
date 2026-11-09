package com.sisdis.forca.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Parâmetros e retornos dos métodos remotos. */
final class Contrato {
    private Contrato() {
    }

    record Entrar(@JsonProperty(required = true) String apelido) {
    }

    record CriarSala(@JsonProperty(required = true) String nome) {
    }

    record EntrarSala(@JsonProperty(required = true) String id) {
    }

    record DefinirPalavra(@JsonProperty(required = true) String palavra, String dica) {
    }

    record TentarLetra(@JsonProperty(required = true) String letra) {
    }

    record ArriscarPalavra(@JsonProperty(required = true) String palavra) {
    }

    record JogadorEntrou(String apelido) {
    }

    record SalaCriada(String id) {
    }

    record ResultadoTentativa(String resultado) {
    }
}
