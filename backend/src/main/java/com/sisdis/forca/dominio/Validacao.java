package com.sisdis.forca.dominio;

final class Validacao {
    private Validacao() {
    }

    static String texto(String valor, String rotulo, int min, int max) {
        String texto = valor.strip();
        if (texto.length() < min || texto.length() > max) {
            throw new RegraJogoException("%s deve ter de %d a %d caracteres.".formatted(rotulo, min, max));
        }
        return texto;
    }
}
