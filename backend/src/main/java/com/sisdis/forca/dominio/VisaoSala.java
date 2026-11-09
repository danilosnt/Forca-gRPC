package com.sisdis.forca.dominio;

import java.util.List;

/** Estado da sala do ponto de vista de um jogador. */
public record VisaoSala(
        String id,
        String nome,
        String fase,
        String papel,
        List<Pontuacao> placar,
        Rodada rodada) {

    public record Pontuacao(String apelido, String papel, int pontos) {
    }

    public record LetraTentada(String letra, boolean acertou) {
    }

    /** "palavra" é null para quem adivinha enquanto a rodada não termina. */
    public record Rodada(
            List<String> mascara,
            String dica,
            List<LetraTentada> letras,
            int erros,
            int maxErros,
            String resultado,
            String palavra) {
    }
}
