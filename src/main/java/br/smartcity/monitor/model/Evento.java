package br.smartcity.monitor.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Evento {

    private final String id;
    private final TipoEvento tipo;
    private final String descricao;
    private final LocalDateTime timestampCriacao;

    public Evento(TipoEvento tipo, String descricao) {
        this.id = UUID.randomUUID().toString();
        this.tipo = tipo;
        this.descricao = descricao;
        this.timestampCriacao = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public LocalDateTime getTimestampCriacao() {
        return timestampCriacao;
    }

    @Override
    public String toString() {
        return "[" + tipo + "] " + descricao +
                " | criado em: " + timestampCriacao;
    }
}