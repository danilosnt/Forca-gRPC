package com.sisdis.forca.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ForcaTest {
    private static Forca forca(String palavra) {
        return new Forca(palavra, "");
    }

    @Test
    void acentoEIgnoradoNoPalpiteEMantidoNaPalavra() {
        Forca forca = forca("ação");
        assertEquals(ResultadoJogada.ACERTO, forca.tentarLetra("a"));
        assertEquals(Arrays.asList("A", null, "Ã", null), forca.mascara());
        forca.tentarLetra("c");
        assertEquals(Arrays.asList("A", "Ç", "Ã", null), forca.mascara());
    }

    @Test
    void revelarTodasAsLetrasVence() {
        Forca forca = forca("ovo");
        forca.tentarLetra("O");
        assertNull(forca.resultado());
        forca.tentarLetra("V");
        assertEquals(ResultadoRodada.VITORIA, forca.resultado());
    }

    @Test
    void letraRepetidaNaoContaComoErro() {
        Forca forca = forca("casa");
        assertEquals(ResultadoJogada.ERRO, forca.tentarLetra("X"));
        assertEquals(ResultadoJogada.REPETIDA, forca.tentarLetra("x"));
        assertEquals(1, forca.erros());
    }

    @Test
    void arriscarErradoCustaUmErroEArriscarCertoVence() {
        Forca forca = forca("coração");
        assertEquals(ResultadoJogada.ERRO, forca.arriscar("cerebro"));
        assertEquals(1, forca.erros());
        assertEquals(ResultadoJogada.ACERTO, forca.arriscar("CORACAO"));
        assertEquals(ResultadoRodada.VITORIA, forca.resultado());
    }

    @Test
    void sextoErroEnforcaEBloqueiaNovasJogadas() {
        Forca forca = forca("sol");
        List.of("A", "B", "C", "D", "E", "F").forEach(forca::tentarLetra);
        assertEquals(Forca.MAX_ERROS, forca.erros());
        assertEquals(ResultadoRodada.DERROTA, forca.resultado());
        assertThrows(RegraJogoException.class, () -> forca.tentarLetra("S"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ab", "duas palavras", "abc1", "aaaaaaaaaaaaaaaaaaaaa", "",
        "-abc", "abc-", "ab--cd", "a-b", "---", "guarda - chuva", "guarda_chuva", "guarda–chuva"
    })
    void palavrasInvalidasSaoRejeitadas(String palavra) {
        assertThrows(RegraJogoException.class, () -> forca(palavra));
    }

    @Test
    void hifenJaApareceNaMascaraENaoPrecisaSerAdivinhado() {
        Forca forca = forca("pé-de-moça");
        assertEquals(
                Arrays.asList(null, null, "-", null, null, "-", null, null, null, null),
                forca.mascara());

        List.of("P", "E", "D", "M", "O", "C").forEach(forca::tentarLetra);
        assertNull(forca.resultado());
        forca.tentarLetra("A");
        assertEquals(ResultadoRodada.VITORIA, forca.resultado());
        assertEquals("PÉ-DE-MOÇA", forca.palavra());
    }

    @Test
    void hifenNaoContaNoLimiteDeLetras() {
        assertEquals("AAAAAAAAAA-AAAAAAAAAA", forca("aaaaaaaaaa-aaaaaaaaaa").palavra());
        assertThrows(RegraJogoException.class, () -> forca("aaaaaaaaaaa-aaaaaaaaaa"));
    }

    @Test
    void arriscarAceitaAPalavraComOuSemHifen() {
        assertEquals(ResultadoJogada.ACERTO, forca("guarda-chuva").arriscar("guarda-chuva"));
        assertEquals(ResultadoJogada.ACERTO, forca("guarda-chuva").arriscar("guardachuva"));
    }

    @Test
    void hifenNaoEUmaLetraValida() {
        Forca forca = forca("guarda-chuva");
        assertThrows(RegraJogoException.class, () -> forca.tentarLetra("-"));
        assertThrows(RegraJogoException.class, () -> forca.arriscar("guarda--chuva"));
        assertEquals(0, forca.erros());
    }

    @Test
    void entradaInvalidaNaoGastaTentativa() {
        Forca forca = forca("casa");
        assertThrows(RegraJogoException.class, () -> forca.tentarLetra("1"));
        assertThrows(RegraJogoException.class, () -> forca.arriscar("ca sa"));
        assertEquals(0, forca.erros());
    }
}
