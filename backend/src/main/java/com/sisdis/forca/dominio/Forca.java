package com.sisdis.forca.dominio;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras de uma rodada. Não sabe nada de rede nem de salas.
 *
 * <p>Não é thread-safe: quem protege o acesso é a {@link Sala} dona da rodada.
 */
public final class Forca {
    public static final int MAX_ERROS = 6;

    private static final int MIN_LETRAS = 3;
    private static final int MAX_LETRAS = 20;
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}");
    private static final String HIFEN = "-";
    private static final Pattern PALAVRA_UNICODE = Pattern.compile("\\p{L}+(-\\p{L}+)*");
    private static final Pattern PALAVRA_SEM_ACENTO = Pattern.compile("[A-Z]+(-[A-Z]+)*");
    private static final Pattern LETRA = Pattern.compile("[A-Z]");

    private final String palavra;
    private final String semAcento;
    private final String dica;
    private final Set<String> tentadas = new LinkedHashSet<>();
    private int erros;
    private ResultadoRodada resultado;

    public Forca(String palavra, String dica) {
        this.palavra = preparar(palavra);
        this.semAcento = semHifen(normalizar(this.palavra));
        this.dica = dica;
    }

    /** "Ação" vira "ACAO": palpites ignoram acentos e cedilha. */
    static String normalizar(String texto) {
        String decomposto = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return ACENTOS.matcher(decomposto).replaceAll("").toUpperCase(Locale.ROOT);
    }

    private static String semHifen(String texto) {
        return texto.replace(HIFEN, "");
    }

    private static String preparar(String bruta) {
        String palavra = Normalizer.normalize(bruta.strip(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
        String semAcento = normalizar(palavra);
        if (!PALAVRA_UNICODE.matcher(palavra).matches() || !PALAVRA_SEM_ACENTO.matcher(semAcento).matches()) {
            throw new RegraJogoException("A palavra deve ter apenas letras, com hífen só entre elas.");
        }
        int letras = semHifen(semAcento).length();
        if (letras < MIN_LETRAS || letras > MAX_LETRAS) {
            throw new RegraJogoException(
                    "A palavra deve ter de %d a %d letras.".formatted(MIN_LETRAS, MAX_LETRAS));
        }
        return palavra;
    }

    public ResultadoJogada tentarLetra(String bruta) {
        exigirEmAndamento();
        String letra = normalizar(bruta.strip());
        if (!LETRA.matcher(letra).matches()) {
            throw new RegraJogoException("Envie uma única letra.");
        }
        if (!tentadas.add(letra)) {
            return ResultadoJogada.REPETIDA;
        }
        if (!semAcento.contains(letra)) {
            registrarErro();
            return ResultadoJogada.ERRO;
        }
        if (semAcento.chars().allMatch(c -> tentadas.contains(Character.toString(c)))) {
            resultado = ResultadoRodada.VITORIA;
        }
        return ResultadoJogada.ACERTO;
    }

    public ResultadoJogada arriscar(String bruta) {
        exigirEmAndamento();
        String palpite = normalizar(bruta.strip());
        if (!PALAVRA_SEM_ACENTO.matcher(palpite).matches()) {
            throw new RegraJogoException("O palpite deve ter apenas letras, com hífen só entre elas.");
        }
        if (semHifen(palpite).equals(semAcento)) {
            resultado = ResultadoRodada.VITORIA;
            return ResultadoJogada.ACERTO;
        }
        registrarErro();
        return ResultadoJogada.ERRO;
    }

    public boolean terminou() {
        return resultado != null;
    }

    public ResultadoRodada resultado() {
        return resultado;
    }

    public int erros() {
        return erros;
    }

    public String palavra() {
        return palavra;
    }

    public String dica() {
        return dica;
    }

    /** Uma posição por caractere, com null nas letras ainda não descobertas. O hífen já vem visível. */
    public List<String> mascara() {
        return palavra.codePoints()
                .mapToObj(Character::toString)
                .map(letra -> terminou() || letra.equals(HIFEN) || tentadas.contains(normalizar(letra)) ? letra : null)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<VisaoSala.LetraTentada> letras() {
        return tentadas.stream()
                .map(letra -> new VisaoSala.LetraTentada(letra, semAcento.contains(letra)))
                .toList();
    }

    private void registrarErro() {
        erros++;
        if (erros >= MAX_ERROS) {
            resultado = ResultadoRodada.DERROTA;
        }
    }

    private void exigirEmAndamento() {
        if (terminou()) {
            throw new RegraJogoException("A rodada já terminou.");
        }
    }
}
