package br.smartcity.monitor.escalonamento.model;

import br.smartcity.monitor.model.TipoEvento;

import java.util.Objects;

/**
 * Processo didático criado a partir de um evento recebido pela Central.
 *
 * @param id identificador único na carga
 * @param tipoEvento categoria urbana já usada pelo Smart City Monitor
 * @param descricao descrição do alerta que originou o processo
 * @param tempoChegada instante lógico em que o processo fica disponível
 * @param burst unidades de CPU necessárias para concluir o processamento
 * @param ordemCadastro critério estável para desempatar chegadas simultâneas
 */
public record ProcessoUrbano(
        String id,
        TipoEvento tipoEvento,
        String descricao,
        int tempoChegada,
        int burst,
        int ordemCadastro
) {
    public ProcessoUrbano {
        id = normalizarTexto(id, "O ID do processo é obrigatório");
        tipoEvento = Objects.requireNonNull(
                tipoEvento,
                "O tipo do evento é obrigatório"
        );
        descricao = normalizarTexto(
                descricao,
                "A descrição do evento é obrigatória"
        );

        if (tempoChegada < 0) {
            throw new IllegalArgumentException(
                    "O tempo de chegada não pode ser negativo"
            );
        }
        if (burst <= 0) {
            throw new IllegalArgumentException(
                    "O burst de CPU deve ser maior que zero"
            );
        }
        if (ordemCadastro < 0) {
            throw new IllegalArgumentException(
                    "A ordem de cadastro não pode ser negativa"
            );
        }
    }

    private static String normalizarTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        return valor.trim();
    }
}
