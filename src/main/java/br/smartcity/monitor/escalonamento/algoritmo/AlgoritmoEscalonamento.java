package br.smartcity.monitor.escalonamento.algoritmo;

import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;

import java.util.List;

/** Contrato comum às políticas de escalonamento simuladas. */
@FunctionalInterface
public interface AlgoritmoEscalonamento {

    ResultadoEscalonamento executar(List<ProcessoUrbano> processos);
}
