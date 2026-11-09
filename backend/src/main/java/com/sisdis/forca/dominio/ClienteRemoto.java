package com.sisdis.forca.dominio;

import java.util.List;

/**
 * Interface remota do cliente: o que o servidor pode chamar em cada jogador.
 *
 * <p>As implementações não podem bloquear nem fazer I/O na thread que chama,
 * porque o domínio chama estes métodos com travas presas.
 */
public interface ClienteRemoto {
    void lobbyAtualizado(List<ResumoSala> salas);

    /** Recebe null quando o jogador sai da sala. */
    void salaAtualizada(VisaoSala sala);

    void aviso(String mensagem);
}
