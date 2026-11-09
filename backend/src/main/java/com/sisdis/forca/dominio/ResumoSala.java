package com.sisdis.forca.dominio;

import java.util.List;

/** O que o lobby mostra de cada sala. */
public record ResumoSala(String id, String nome, List<String> jogadores, boolean cheia) {
}
